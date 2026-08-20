import type { DramaForgeJob, DramaForgeShot } from "@dreamreel/shared-types";

export function hasActiveShotJob(
  shot: DramaForgeShot,
  jobs: DramaForgeJob[],
  mode: "storyboard" | "video",
): boolean {
  return Boolean(findActiveShotJob(shot, jobs, mode));
}

export function isStoryboardComplete(shot: DramaForgeShot): boolean {
  const first = shot.firstFrameUrl || shot.storyboardUrl;
  return Boolean(first && shot.lastFrameUrl);
}

export function canGenerateShotStoryboard(
  shot: DramaForgeShot,
  allShots: DramaForgeShot[],
): boolean {
  const previous = allShots
    .filter((s) => s.shotNumber < shot.shotNumber)
    .sort((a, b) => b.shotNumber - a.shotNumber)[0];
  if (!previous) return true;
  return Boolean(previous.lastFrameUrl);
}

function isShotJobType(job: DramaForgeJob, mode: "storyboard" | "video"): boolean {
  if (mode === "storyboard") {
    return job.jobType === "storyboard" || job.jobType === "shot_storyboard";
  }
  return job.jobType === "video" || job.jobType === "shot_video";
}

export function findActiveShotJob(
  shot: DramaForgeShot,
  jobs: DramaForgeJob[],
  mode: "storyboard" | "video",
): DramaForgeJob | undefined {
  // 只有排队/执行中的任务才算“活跃”，已完成/失败/取消的历史任务不应让镜头显示“生成中”
  return jobs
    .filter(
      (j) =>
        j.targetId === shot.id
        && isShotJobType(j, mode)
        && (j.status === "queued" || j.status === "running"),
    )
    .sort((a, b) => new Date(b.updatedAt).getTime() - new Date(a.updatedAt).getTime())[0];
}

export function resolveShotFailureReason(
  shot: DramaForgeShot,
  jobs: DramaForgeJob[],
  mode: "storyboard" | "video",
): string | null {
  // 分镜两张定妆帧齐全后，不再显示历史失败（旧失败属于上一轮生成）
  if (mode === "storyboard" && isStoryboardComplete(shot)) return null;
  const activeJob = findActiveShotJob(shot, jobs, mode);
  if (activeJob?.errorMessage) return activeJob.errorMessage;
  // 只看该镜头/模式下最新一次任务：只有最新任务失败才提示
  const newestJob = jobs
    .filter((j) => j.targetId === shot.id && isShotJobType(j, mode))
    .sort((a, b) => new Date(b.updatedAt).getTime() - new Date(a.updatedAt).getTime())[0];
  if (newestJob && newestJob.status === "failed" && newestJob.errorMessage) {
    return newestJob.errorMessage;
  }
  if (shot.status === "failed") {
    if (shot.errorMessage) return shot.errorMessage;
    const fromJob = jobs
      .filter((j) => j.targetId === shot.id && j.status === "failed" && !!j.errorMessage)
      .sort((a, b) => new Date(b.updatedAt).getTime() - new Date(a.updatedAt).getTime())[0]
      ?.errorMessage;
    if (fromJob) return fromJob;
  }
  return null;
}

export function shotVisualStatus(
  shot: DramaForgeShot,
  mode: "storyboard" | "video" = "video",
  jobs: DramaForgeJob[] = [],
): {
  key: "done" | "run" | "wait" | "fail";
  thumbLabel: string;
  statusLabel: string;
  thumbKey: string;
  statusKey: string;
} {
  const activeJob = findActiveShotJob(shot, jobs, mode);
  const label = mode === "storyboard" ? "storyboardGenerating" : "videoGenerating";
  if (activeJob?.status === "queued") {
    const qLabel = mode === "storyboard" ? "storyboardQueued" : "videoQueued";
    return { key: "run", thumbLabel: qLabel, statusLabel: qLabel, thumbKey: qLabel, statusKey: qLabel };
  }
  if (activeJob?.status === "running") {
    return { key: "run", thumbLabel: label, statusLabel: label, thumbKey: label, statusKey: label };
  }

  const hasFailureDetail = Boolean(resolveShotFailureReason(shot, jobs, mode));
  if (mode === "storyboard") {
    if (isStoryboardComplete(shot)) {
      return { key: "done", thumbLabel: "storyboardDone", statusLabel: "completed", thumbKey: "storyboardDone", statusKey: "completed" };
    }
    if (shot.status === "failed" || hasFailureDetail) {
      return { key: "fail", thumbLabel: "failed", statusLabel: "failed", thumbKey: "failed", statusKey: "failed" };
    }
    if (shot.firstFrameUrl || shot.storyboardUrl) {
      return { key: "wait", thumbLabel: "waitTailFrame", statusLabel: "waitTailFrame", thumbKey: "waitTailFrame", statusKey: "waitTailFrame" };
    }
    return { key: "wait", thumbLabel: "waitStoryboard", statusLabel: "pending", thumbKey: "waitStoryboard", statusKey: "pending" };
  }
  if (shot.status === "video_done" || shot.videoUrl) {
    return { key: "done", thumbLabel: "videoDone", statusLabel: "completed", thumbKey: "videoDone", statusKey: "completed" };
  }
  if (shot.videoJobId && !shot.videoUrl && shot.status !== "failed") {
    return { key: "run", thumbLabel: "videoGenerating", statusLabel: "videoGenerating", thumbKey: "videoGenerating", statusKey: "videoGenerating" };
  }
  if (shot.status === "failed" || hasFailureDetail) {
    return { key: "fail", thumbLabel: "failed", statusLabel: "failed", thumbKey: "failed", statusKey: "failed" };
  }
  return { key: "wait", thumbLabel: "pending", statusLabel: "pending", thumbKey: "pending", statusKey: "pending" };
}
