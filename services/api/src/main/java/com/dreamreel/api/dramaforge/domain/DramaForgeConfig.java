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
@Table(name = "dramaforge_configs")
@Getter
@Setter
@NoArgsConstructor
public class DramaForgeConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "project_id", nullable = false, unique = true)
    private UUID projectId;

    @Enumerated(EnumType.STRING)
    @Column(name = "content_mode", nullable = false, length = 16)
    private DramaForgeContentMode contentMode = DramaForgeContentMode.DRAMA;

    @Enumerated(EnumType.STRING)
    @Column(name = "generation_mode", nullable = false, length = 24)
    private DramaForgeGenerationMode generationMode = DramaForgeGenerationMode.REFERENCE_TO_VIDEO;

    @Column(name = "image_backend", length = 128)
    private String imageBackend;

    @Column(name = "video_backend", length = 128)
    private String videoBackend;

    @Column(name = "text_backend", length = 128)
    private String textBackend;

    @Column(name = "style_prompt", length = 2000)
    private String stylePrompt;

    @Column(name = "source_text", columnDefinition = "TEXT")
    private String sourceText;

    @Column(name = "project_summary", columnDefinition = "TEXT")
    private String projectSummary;

    @Column(name = "worldview", columnDefinition = "TEXT")
    private String worldview;

    /** 全局画面比例，如 9:16 / 16:9；为空时按内容模式推断 */
    @Column(name = "aspect_ratio", length = 8)
    private String aspectRatio;

    /** 图像生成默认画质：480p / 720p / 1080p */
    @Column(name = "image_quality", length = 16)
    private String imageQuality = "720p";

    /** 视频生成默认画质：固定 480p（方舟 Seedance） */
    @Column(name = "video_quality", length = 16)
    private String videoQuality = "480p";

    /** 合成调色：none / warm / neutral / cool / cinematic */
    @Column(name = "color_grade_preset", length = 24)
    private String colorGradePreset = "none";

    /** 合成时将对白 TTS 轨混入各镜头片段 */
    @Column(name = "mix_dialogue_audio", nullable = false, columnDefinition = "boolean not null default true")
    private boolean mixDialogueAudioInCompose = true;

    /** 项目级 BGM URL，成片混音用 */
    @Column(name = "bgm_url", length = 2000)
    private String bgmUrl;

    /** BGM 相对音量 0.05–0.5，默认 0.18 */
    @Column(name = "bgm_volume")
    private Double bgmVolume = 0.18;

    /** 是否启用外部口型同步（需配置 endpoint） */
    @Column(name = "lip_sync_enabled", nullable = false, columnDefinition = "boolean not null default false")
    private boolean lipSyncEnabled = false;

    /** 外部 LipSync 服务 URL（POST JSON: videoUrl/audioUrl） */
    @Column(name = "lip_sync_endpoint", length = 2000)
    private String lipSyncEndpoint;

    /** 生成视频时优先用 Seedance 单次多机位提示词 */
    @Column(name = "prefer_model_multishot", nullable = false, columnDefinition = "boolean not null default false")
    /** 默认走模型内多机位，避免物理拆镜导致一集镜头暴涨 */
    private boolean preferModelMultiShot = true;

    /** Step ③ 用户确认资产库 */
    @Column(name = "assets_locked_at")
    private Instant assetsLockedAt;

    /** Step ④ 项目级分镜确认（可选，与集级二选一） */
    @Column(name = "storyboard_locked_at")
    private Instant storyboardLockedAt;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;
}
