package com.dreamreel.api.dramaforge.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "dramaforge_episodes")
@Getter
@Setter
@NoArgsConstructor
public class DramaForgeEpisode {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "episode_number", nullable = false)
    private int episodeNumber;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(name = "script_json", columnDefinition = "TEXT")
    private String scriptJson;

    /** Step ② 用户确认剧本 */
    @Column(name = "script_locked_at")
    private Instant scriptLockedAt;

    /** Step ④ 用户确认本分镜 */
    @Column(name = "storyboard_locked_at")
    private Instant storyboardLockedAt;

    /** Step ⑥ 时间轴 Lite JSON */
    @Column(name = "timeline_json", columnDefinition = "TEXT")
    private String timelineJson;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;
}
