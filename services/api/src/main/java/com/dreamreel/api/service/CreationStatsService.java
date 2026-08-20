package com.dreamreel.api.service;

import com.dreamreel.api.domain.GenerationMediaType;
import com.dreamreel.api.domain.GenerationStatus;
import com.dreamreel.api.dto.CreationStatsResponse;
import com.dreamreel.api.repository.GenerationJobRepository;
import com.dreamreel.api.repository.ProjectRepository;
import com.dreamreel.api.security.CurrentUserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

@Service
public class CreationStatsService {

    private static final ZoneId STATS_ZONE = ZoneId.of("Asia/Shanghai");
    /** 视频任务未持久化时长时，按默认 5 秒估算渲染时长 */
    private static final double SECONDS_PER_VIDEO = 5.0;
    private static final long CREDITS_PER_VIDEO = 100;
    private static final long CREDITS_PER_IMAGE = 20;
    private static final long CREDITS_PER_TEXT = 5;

    private final ProjectRepository projectRepository;
    private final GenerationJobRepository generationJobRepository;
    private final CurrentUserService currentUserService;

    public CreationStatsService(
            ProjectRepository projectRepository,
            GenerationJobRepository generationJobRepository,
            CurrentUserService currentUserService) {
        this.projectRepository = projectRepository;
        this.generationJobRepository = generationJobRepository;
        this.currentUserService = currentUserService;
    }

    @Transactional(readOnly = true)
    public CreationStatsResponse todayStats() {
        UUID userId = currentUserService.requireUserId();
        LocalDate today = LocalDate.now(STATS_ZONE);
        Instant todayStart = today.atStartOfDay(STATS_ZONE).toInstant();
        Instant tomorrowStart = today.plusDays(1).atStartOfDay(STATS_ZONE).toInstant();
        Instant yesterdayStart = today.minusDays(1).atStartOfDay(STATS_ZONE).toInstant();

        long projectsToday = projectRepository.countByUserIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                userId, todayStart, tomorrowStart);
        long projectsYesterday = projectRepository.countByUserIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                userId, yesterdayStart, todayStart);

        long videosToday = countCompleted(userId, GenerationMediaType.VIDEO, todayStart, tomorrowStart);
        long videosYesterday = countCompleted(userId, GenerationMediaType.VIDEO, yesterdayStart, todayStart);

        double renderToday = videosToday * SECONDS_PER_VIDEO / 3600.0;
        double renderYesterday = videosYesterday * SECONDS_PER_VIDEO / 3600.0;

        long creditsToday = creditsForDay(userId, todayStart, tomorrowStart);
        long creditsYesterday = creditsForDay(userId, yesterdayStart, todayStart);

        return new CreationStatsResponse(
                projectsToday,
                deltaPercent(projectsToday, projectsYesterday),
                round1(renderToday),
                deltaPercent(renderToday, renderYesterday),
                videosToday,
                deltaPercent(videosToday, videosYesterday),
                creditsToday,
                deltaPercent(creditsToday, creditsYesterday)
        );
    }

    private long countCompleted(UUID userId, GenerationMediaType mediaType, Instant start, Instant end) {
        return generationJobRepository
                .countByUserIdAndMediaTypeAndStatusAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                        userId, mediaType, GenerationStatus.COMPLETED, start, end);
    }

    private long creditsForDay(UUID userId, Instant start, Instant end) {
        long videos = countCompleted(userId, GenerationMediaType.VIDEO, start, end);
        long images = countCompleted(userId, GenerationMediaType.IMAGE, start, end);
        long texts = countCompleted(userId, GenerationMediaType.TEXT, start, end);
        return videos * CREDITS_PER_VIDEO + images * CREDITS_PER_IMAGE + texts * CREDITS_PER_TEXT;
    }

    private static int deltaPercent(long today, long yesterday) {
        if (yesterday == 0) {
            return today > 0 ? 100 : 0;
        }
        return (int) Math.round((today - yesterday) * 100.0 / yesterday);
    }

    private static int deltaPercent(double today, double yesterday) {
        if (yesterday == 0) {
            return today > 0 ? 100 : 0;
        }
        return (int) Math.round((today - yesterday) * 100.0 / yesterday);
    }

    private static double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
