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
@Table(name = "dramaforge_shots")
@Getter
@Setter
@NoArgsConstructor
public class DramaForgeShot {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "episode_id", nullable = false)
    private UUID episodeId;

    @Column(name = "shot_number", nullable = false)
    private int shotNumber;

    @Column(nullable = false, length = 16000)
    private String description;

    @Column(length = 4000)
    private String dialogue;

    @Column(name = "camera_note", length = 2000)
    private String cameraNote;

    @Column(name = "character_refs", length = 2000)
    private String characterRefsJson = "[]";

    @Column(name = "scene_ref", length = 200)
    private String sceneRef;

    @Column(name = "prop_refs", length = 2000)
    private String propRefsJson = "[]";

    @Column(name = "storyboard_url", length = 2000)
    private String storyboardUrl;

    @Column(name = "reference_video_url", length = 2000)
    private String referenceVideoUrl;

    /** 为 true 时 characterRefs 全部参与 @Image/@Audio，忽略「对白提及不算出镜」规则 */
    @Column(name = "force_character_binding")
    private Boolean forceCharacterBinding = false;

    /** 跨镜续拍：auto=自动上一镜 / none=关闭 / custom=仅自定义 URL */
    @Column(name = "reference_video_mode", length = 32)
    private String referenceVideoMode = "auto";

    @Column(name = "first_frame_url", length = 2000)
    private String firstFrameUrl;

    /** 分镜尾帧（动作结束态），供下一镜首帧连贯参考 */
    @Column(name = "last_frame_url", length = 2000)
    private String lastFrameUrl;

    /** 分镜首帧生成用的专用提示词（与 videoPrompt 分离） */
    @Column(name = "storyboard_prompt", length = 8000)
    private String storyboardPrompt;

    /** 对白 TTS 音频 URL，合成时可混入 */
    @Column(name = "dialogue_audio_url", length = 2000)
    private String dialogueAudioUrl;

    /** 质检：pending / pass / fail */
    @Column(name = "qa_status", length = 16)
    private String qaStatus = "pending";

    /** 是否用模型级多机位（单次生成含多个运镜 beat） */
    @Column(name = "model_multi_shot")
    private Boolean modelMultiShot = false;

    /** 多机位模板 id：dialogue / action / emotion */
    @Column(name = "multi_shot_template", length = 32)
    private String multiShotTemplate;

    @Column(name = "grid_group_id")
    private UUID gridGroupId;

    @Column(name = "video_job_id")
    private UUID videoJobId;

    /** 最近一次失败原因（视频/分镜等），供前端展示 */
    @Column(name = "error_message", length = 2000)
    private String errorMessage;

    /** 镜头视频时长（秒），平台规则 2–15 */
    @Column(name = "duration_seconds")
    private Integer durationSeconds = 5;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private DramaForgeShotStatus status = DramaForgeShotStatus.PENDING;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    /**
     * 仍在等待上游视频结果。已成功或已失败都不应再被 SYNC_VIDEOS 续跑。
     */
    public boolean needsVideoSync() {
        return videoJobId != null
                && status != DramaForgeShotStatus.VIDEO_DONE
                && status != DramaForgeShotStatus.FAILED;
    }
}
