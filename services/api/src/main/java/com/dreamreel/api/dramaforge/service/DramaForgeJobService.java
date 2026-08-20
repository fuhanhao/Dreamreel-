package com.dreamreel.api.dramaforge.service;

import com.dreamreel.api.config.DramaForgeProperties;
import com.dreamreel.api.dramaforge.domain.DramaForgeJob;
import com.dreamreel.api.dramaforge.domain.DramaForgeJobStatus;
import com.dreamreel.api.dramaforge.domain.DramaForgeJobType;
import com.dreamreel.api.dramaforge.dto.DramaForgeDtos.JobResponse;
import com.dreamreel.api.dramaforge.repository.DramaForgeJobRepository;
import com.dreamreel.api.dramaforge.repository.DramaForgeJobClaimRepository;
import com.dreamreel.api.exception.ResourceNotFoundException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional
public class DramaForgeJobService {

    /** 首屏任务列表默认条数；避免历史任务把 /jobs 撑到数十 MB。 */
    public static final int DEFAULT_LIST_LIMIT = 50;
    public static final int MAX_LIST_LIMIT = 200;
    private static final int FINISHED_PRUNE_THRESHOLD = 200;
    private static final List<DramaForgeJobStatus> ACTIVE_STATUSES =
            List.of(DramaForgeJobStatus.QUEUED, DramaForgeJobStatus.RUNNING);
    private static final List<DramaForgeJobStatus> FINISHED_STATUSES = List.of(
            DramaForgeJobStatus.COMPLETED,
            DramaForgeJobStatus.FAILED,
            DramaForgeJobStatus.CANCELLED);

    private final DramaForgeJobRepository jobRepository;
    private final DramaForgeJobClaimRepository claimRepository;
    private final DramaForgeEventHub eventHub;
    private final DramaForgeProperties properties;

    public DramaForgeJobService(
            DramaForgeJobRepository jobRepository,
            DramaForgeJobClaimRepository claimRepository,
            DramaForgeEventHub eventHub,
            DramaForgeProperties properties) {
        this.jobRepository = jobRepository;
        this.claimRepository = claimRepository;
        this.eventHub = eventHub;
        this.properties = properties;
    }

    public DramaForgeJob enqueue(UUID projectId, DramaForgeJobType type, UUID targetId, UUID episodeId, String payloadJson) {
        return enqueue(projectId, type, targetId, episodeId, payloadJson, false);
    }

    /**
     * @param startRunning 为 true 时直接以 RUNNING 入库，任务从不进入 QUEUED，避免其它实例抢走。
     */
    public DramaForgeJob enqueue(
            UUID projectId,
            DramaForgeJobType type,
            UUID targetId,
            UUID episodeId,
            String payloadJson,
            boolean startRunning) {
        recoverStaleJobs();
        // 链路续跑只禁止重复 QUEUED；RUNNING 时仍可补一条 QUEUED 形成下一轮
        if (type == DramaForgeJobType.SYNC_VIDEOS && !startRunning) {
            var queued = jobRepository.findFirstByProjectIdAndJobTypeAndStatusInOrderByCreatedAtDesc(
                    projectId,
                    DramaForgeJobType.SYNC_VIDEOS,
                    List.of(DramaForgeJobStatus.QUEUED));
            if (queued.isPresent()) {
                return queued.get();
            }
        }
        var now = Instant.now();
        var job = new DramaForgeJob();
        job.setProjectId(projectId);
        job.setJobType(type);
        job.setTargetId(targetId);
        job.setEpisodeId(episodeId);
        job.setPayloadJson(payloadJson);
        if (startRunning) {
            job.setStatus(DramaForgeJobStatus.RUNNING);
            job.setAttempts(1);
            job.setLeaseUntil(now.plusSeconds(leaseSeconds()));
        } else {
            job.setAttempts(0);
        }
        job = jobRepository.save(job);
        publishJob(projectId, startRunning ? "job_running" : "job_queued", job);
        return job;
    }

    public JobResponse toResponse(DramaForgeJob job) {
        Integer queuePosition = job.getStatus() == DramaForgeJobStatus.QUEUED
                ? buildQueuePositions().get(job.getId())
                : null;
        return JobResponse.from(job, queuePosition);
    }

    /**
     * 返回进行中任务 + 最近历史（默认 50 条）。
     * 不做全表清理：清理放在任务完成路径，避免 /jobs 首屏被数万历史拖死。
     */
    public List<JobResponse> listJobs(UUID projectId) {
        return listJobs(projectId, DEFAULT_LIST_LIMIT);
    }

    public List<JobResponse> listJobs(UUID projectId, Integer limit) {
        int cap = normalizeListLimit(limit);

        var byId = new LinkedHashMap<UUID, DramaForgeJob>();
        for (var job : jobRepository.findByProjectIdAndStatusInOrderByCreatedAtDesc(projectId, ACTIVE_STATUSES)) {
            byId.put(job.getId(), job);
        }
        for (var job : jobRepository.findByProjectIdOrderByCreatedAtDesc(projectId, PageRequest.of(0, cap))) {
            byId.putIfAbsent(job.getId(), job);
        }

        var queuePositions = buildQueuePositions();
        return byId.values().stream()
                .sorted(Comparator
                        .comparing((DramaForgeJob j) -> j.getStatus() == DramaForgeJobStatus.RUNNING ? 0
                                : j.getStatus() == DramaForgeJobStatus.QUEUED ? 1 : 2)
                        .thenComparing(DramaForgeJob::getCreatedAt, Comparator.reverseOrder()))
                .map(job -> JobResponse.from(job, queuePositions.get(job.getId())))
                .toList();
    }

    private static int normalizeListLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_LIST_LIMIT;
        }
        return Math.min(limit, MAX_LIST_LIMIT);
    }

    /**
     * 已结束任务超过阈值时清空已结束记录（进行中任务保留）。
     * SYNC_VIDEOS 轮询曾把单项目任务堆到数万条，/jobs 会到十几 MB。
     */
    public void pruneFinishedJobsIfNeeded(UUID projectId) {
        long finished = jobRepository.countByProjectIdAndStatusIn(projectId, FINISHED_STATUSES);
        if (finished <= FINISHED_PRUNE_THRESHOLD) {
            return;
        }
        var removed = jobRepository.deleteByProjectIdAndStatusIn(projectId, FINISHED_STATUSES);
        if (removed > 0) {
            eventHub.publish(projectId, "jobs_cleared", Map.of("removed", (int) removed));
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public DramaForgeJob claimNext() {
        var now = Instant.now();
        var jobId = claimRepository.lockNextClaimableId(now);
        if (jobId == null) {
            return null;
        }
        var job = jobRepository.findById(jobId).orElse(null);
        return job != null ? markRunning(job, now) : null;
    }

    private DramaForgeJob markRunning(DramaForgeJob job, Instant now) {
        job.setStatus(DramaForgeJobStatus.RUNNING);
        job.setAttempts(job.getAttempts() + 1);
        job.setLeaseUntil(now.plusSeconds(leaseSeconds()));
        job = jobRepository.saveAndFlush(job);
        publishJob(job.getProjectId(), "job_running", job);
        return job;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void releaseClaim(DramaForgeJob job, String message) {
        job.setStatus(DramaForgeJobStatus.QUEUED);
        job.setAttempts(Math.max(0, job.getAttempts() - 1));
        job.setLeaseUntil(null);
        job.setErrorMessage(truncateErrorMessage(message));
        jobRepository.save(job);
        publishJob(job.getProjectId(), "job_queued", job);
    }

    /**
     * 避免 SYNC_VIDEOS 任务堆积导致轮询风暴。
     * 仅检查队列中的任务：允许一个 RUNNING 在结束前补入下一个 QUEUED，
     * 从而形成持续轮询链路，直到视频真正完成。
     */
    public boolean hasActiveSyncJob(UUID projectId) {
        return jobRepository.existsByProjectIdAndJobTypeAndStatusIn(
                projectId,
                DramaForgeJobType.SYNC_VIDEOS,
                List.of(DramaForgeJobStatus.QUEUED));
    }

    public JobResponse cancelJob(UUID projectId, UUID jobId) {
        var job = requireJob(projectId, jobId);
        if (job.getStatus() != DramaForgeJobStatus.QUEUED) {
            throw new IllegalStateException("只能取消排队中的任务");
        }
        job.setStatus(DramaForgeJobStatus.CANCELLED);
        job.setLeaseUntil(null);
        job.setErrorMessage("已取消");
        jobRepository.save(job);
        publishJob(projectId, "job_cancelled", job);
        return JobResponse.from(job, null);
    }

    public JobResponse retryJob(UUID projectId, UUID jobId) {
        var job = requireJob(projectId, jobId);
        if (job.getStatus() != DramaForgeJobStatus.FAILED && job.getStatus() != DramaForgeJobStatus.CANCELLED) {
            throw new IllegalStateException("只能重试失败或已取消的任务");
        }
        job.setStatus(DramaForgeJobStatus.QUEUED);
        job.setAttempts(0);
        job.setLeaseUntil(null);
        job.setErrorMessage(null);
        job.setProgressCurrent(0);
        job.setProgressTotal(0);
        job.setProgressMessage(null);
        job = jobRepository.save(job);
        publishJob(projectId, "job_queued", job);
        return JobResponse.from(job, buildQueuePositions().get(job.getId()));
    }

    public int clearFinishedJobs(UUID projectId) {
        var removed = jobRepository.deleteByProjectIdAndStatusIn(
                projectId,
                List.of(DramaForgeJobStatus.COMPLETED, DramaForgeJobStatus.FAILED, DramaForgeJobStatus.CANCELLED));
        eventHub.publish(projectId, "jobs_cleared", Map.of("removed", removed));
        return (int) removed;
    }

    public void extendLease(DramaForgeJob job) {
        job.setLeaseUntil(Instant.now().plusSeconds(leaseSeconds()));
        jobRepository.save(job);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(DramaForgeJob job) {
        // 同步轮询任务不落历史：否则每十几秒一条 COMPLETED，会把 jobs 表与 /jobs 撑爆
        if (job.getJobType() == DramaForgeJobType.SYNC_VIDEOS) {
            var projectId = job.getProjectId();
            var jobId = job.getId();
            jobRepository.delete(job);
            eventHub.publish(projectId, "job_removed", Map.of("jobId", jobId.toString(), "jobType", "sync_videos"));
            return;
        }
        job.setStatus(DramaForgeJobStatus.COMPLETED);
        job.setLeaseUntil(null);
        job.setErrorMessage(null);
        if (job.getProgressTotal() > 0) {
            job.setProgressCurrent(job.getProgressTotal());
        }
        jobRepository.save(job);
        publishJob(job.getProjectId(), "job_completed", job);
        pruneFinishedJobsIfNeeded(job.getProjectId());
    }

    /**
     * 若已有进行中的 SYNC_VIDEOS（同项目，优先同集），直接复用，避免前端/链路重复入队。
     */
    /** 项目是否已有 RUNNING 任务（用于避免撞 uq_dramaforge_jobs_running_project） */
    public boolean hasRunningJob(UUID projectId) {
        return jobRepository.countByProjectIdAndStatus(projectId, DramaForgeJobStatus.RUNNING) > 0;
    }

    public DramaForgeJob findActiveSyncJob(UUID projectId, UUID episodeId) {
        if (episodeId != null) {
            var sameEpisode = jobRepository
                    .findFirstByProjectIdAndEpisodeIdAndJobTypeAndStatusInOrderByCreatedAtDesc(
                            projectId, episodeId, DramaForgeJobType.SYNC_VIDEOS, ACTIVE_STATUSES);
            if (sameEpisode.isPresent()) {
                return sameEpisode.get();
            }
        }
        return jobRepository
                .findFirstByProjectIdAndJobTypeAndStatusInOrderByCreatedAtDesc(
                        projectId, DramaForgeJobType.SYNC_VIDEOS, ACTIVE_STATUSES)
                .orElse(null);
    }

    /** 清理历史 SYNC_VIDEOS 轮询残骸（进行中的保留）。 */
    public int purgeFinishedSyncJobs(UUID projectId) {
        var removed = jobRepository.deleteByProjectIdAndJobTypeAndStatusIn(
                projectId, DramaForgeJobType.SYNC_VIDEOS, FINISHED_STATUSES);
        if (removed > 0) {
            eventHub.publish(projectId, "jobs_cleared", Map.of("removed", (int) removed, "jobType", "sync_videos"));
        }
        return (int) removed;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(DramaForgeJob job, String message) {
        var detail = truncateErrorMessage(message);
        if (job.getAttempts() < maxAttempts()) {
            job.setStatus(DramaForgeJobStatus.QUEUED);
            job.setLeaseUntil(null);
            job.setErrorMessage(detail);
        } else {
            job.setStatus(DramaForgeJobStatus.FAILED);
            job.setLeaseUntil(null);
            job.setErrorMessage(detail);
        }
        jobRepository.save(job);
        publishJob(job.getProjectId(), "job_failed", job);
    }

    public void forceFail(DramaForgeJob job, String message) {
        job.setStatus(DramaForgeJobStatus.FAILED);
        job.setLeaseUntil(null);
        job.setErrorMessage(truncateErrorMessage(message));
        jobRepository.save(job);
        publishJob(job.getProjectId(), "job_failed", job);
    }

    /** API 重启或任务超时后，回收卡在 RUNNING 的任务 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int recoverStaleJobs() {
        var now = Instant.now();
        var staleIds = claimRepository.lockStaleRunningIds(now, 100);
        var stale = jobRepository.findAllById(staleIds);
        var recovered = 0;
        for (var job : stale) {
            if (job.getAttempts() < maxAttempts()) {
                job.setStatus(DramaForgeJobStatus.QUEUED);
                job.setLeaseUntil(null);
                job.setErrorMessage("任务超时，已自动重试");
            } else {
                job.setStatus(DramaForgeJobStatus.FAILED);
                job.setLeaseUntil(null);
                job.setErrorMessage("任务超时且已达最大重试次数");
            }
            jobRepository.save(job);
            publishJob(job.getProjectId(), job.getStatus() == DramaForgeJobStatus.FAILED ? "job_failed" : "job_queued", job);
            recovered++;
        }
        return recovered;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void reportProgress(DramaForgeJob job, int current, int total, String message) {
        job.setProgressCurrent(current);
        job.setProgressTotal(total);
        job.setProgressMessage(message);
        extendLease(job);
        jobRepository.save(job);
        eventHub.publish(job.getProjectId(), "job_progress", Map.of(
                "jobId", job.getId().toString(),
                "type", job.getJobType().name().toLowerCase(),
                "status", job.getStatus().name().toLowerCase(),
                "current", current,
                "total", total,
                "message", message != null ? message : "",
                "episodeId", job.getEpisodeId() != null ? job.getEpisodeId().toString() : "",
                "targetId", job.getTargetId() != null ? job.getTargetId().toString() : ""
        ));
    }

    private DramaForgeJob requireJob(UUID projectId, UUID jobId) {
        return jobRepository.findByIdAndProjectId(jobId, projectId)
                .orElseThrow(() -> new ResourceNotFoundException("任务不存在: " + jobId));
    }

    private Map<UUID, Integer> buildQueuePositions() {
        var queued = jobRepository.findByStatusOrderByCreatedAtAsc(DramaForgeJobStatus.QUEUED);
        var positions = new HashMap<UUID, Integer>();
        for (int i = 0; i < queued.size(); i++) {
            positions.put(queued.get(i).getId(), i + 1);
        }
        return positions;
    }

    private void publishJob(UUID projectId, String event, DramaForgeJob job) {
        Integer queuePosition = job.getStatus() == DramaForgeJobStatus.QUEUED
                ? buildQueuePositions().get(job.getId())
                : null;
        var error = job.getErrorMessage() != null ? job.getErrorMessage() : "";
        // HashMap：避免 Map.of 空值限制；同时带 error 与 errorMessage 兼容前端
        var payload = new HashMap<String, Object>();
        payload.put("jobId", job.getId().toString());
        payload.put("type", job.getJobType().name().toLowerCase());
        payload.put("status", job.getStatus().name().toLowerCase());
        payload.put("error", error);
        payload.put("errorMessage", error);
        payload.put("current", job.getProgressCurrent());
        payload.put("total", job.getProgressTotal());
        payload.put("message", job.getProgressMessage() != null ? job.getProgressMessage() : "");
        payload.put("episodeId", job.getEpisodeId() != null ? job.getEpisodeId().toString() : "");
        payload.put("targetId", job.getTargetId() != null ? job.getTargetId().toString() : "");
        payload.put("queuePosition", queuePosition != null ? queuePosition : 0);
        eventHub.publish(projectId, event, payload);
    }

    /** 与 entity @Column(length=2000) 对齐，避免超长上游报错导致 fail 落库失败、SSE 发不出文案 */
    static String truncateErrorMessage(String message) {
        if (message == null || message.isBlank()) {
            return "任务执行失败";
        }
        var trimmed = message.trim();
        return trimmed.length() > 2000 ? trimmed.substring(0, 2000) : trimmed;
    }

    private int maxAttempts() {
        return Math.max(1, properties.maxAttempts());
    }

    private long leaseSeconds() {
        return Math.max(30, properties.leaseSeconds());
    }
}
