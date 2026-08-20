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
@Table(name = "dramaforge_assets")
@Getter
@Setter
@NoArgsConstructor
public class DramaForgeAsset {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private DramaForgeAssetType type;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(length = 2000)
    private String description;

    @Column(name = "design_prompt", length = 4000)
    private String designPrompt;

    @Column(name = "reference_image_url", length = 2000)
    private String referenceImageUrl;

    /** 音色标签/描述，便于全局统一 */
    @Column(name = "voice_label", length = 500)
    private String voiceLabel;

    /** 角色试听音频 URL，生视频时可作 @Audio 参考 */
    @Column(name = "voice_sample_url", length = 2000)
    private String voiceSampleUrl;

    /** 豆包/OpenSpeech speaker ID，生成音色后持久化复用 */
    @Column(name = "voice_speaker_id", length = 128)
    private String voiceSpeakerId;

    /**
     * 身份锁定强度 0–100：写入视频提示词，强化 @Image 约束。
     * 外部 LoRA 训练完成后可填 loraRef 作管线标记（本服务不训练权重）。
     */
    @Column(name = "identity_lock_strength")
    private Integer identityLockStrength = 75;

    /** 外部 LoRA / Character ID 引用（如 ComfyUI 模型名、Kling Character ID） */
    @Column(name = "lora_ref", length = 256)
    private String loraRef;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;
}
