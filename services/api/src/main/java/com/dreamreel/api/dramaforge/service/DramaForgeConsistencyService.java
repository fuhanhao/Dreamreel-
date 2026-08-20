package com.dreamreel.api.dramaforge.service;

import com.dreamreel.api.dramaforge.domain.*;
import com.dreamreel.api.dramaforge.dto.DramaForgeDtos.ComposeReadinessResponse;
import com.dreamreel.api.dramaforge.dto.DramaForgeDtos.ConsistencyReportResponse;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * 镜头视频生成前校验与项目级一致性报告。
 */
@Component
public class DramaForgeConsistencyService {

    /**
     * 校验单镜是否可生成视频；非空表示阻塞项。
     */
    public List<String> validateShotForVideo(DramaForgeShot shot, List<DramaForgeAsset> projectAssets) {
        var issues = new ArrayList<String>();
        var shotLabel = "镜头 " + shot.getShotNumber();

        var characterRefs = readNames(shot.getCharacterRefsJson());
        var propRefs = readNames(shot.getPropRefsJson());
        var forceBind = Boolean.TRUE.equals(shot.getForceCharacterBinding());

        var images = DramaForgeStylePrompts.resolveImageRefs(shot, characterRefs, propRefs, projectAssets);
        if (images.urls().isEmpty()) {
            issues.add(shotLabel + "：缺少可用 @Image 参考图（请为绑定角色/场景/道具生成或上传设计图）");
        }

        var visibleChars = characterRefs.stream()
                .filter(name -> forceBind || DramaForgeStylePrompts.isCharacterVisibleInShot(
                        name,
                        shot.getDescription() != null ? shot.getDescription() : "",
                        shot.getDialogue() != null ? shot.getDialogue() : ""))
                .toList();

        for (var name : visibleChars) {
            var asset = findAsset(projectAssets, DramaForgeAssetType.CHARACTER, name);
            if (asset == null) {
                issues.add(shotLabel + "：角色「" + name + "」不在资产库");
            } else if (isBlank(asset.getReferenceImageUrl())) {
                issues.add(shotLabel + "：角色「" + name + "」缺少设计参考图");
            }
        }

        if (shot.getSceneRef() != null && !shot.getSceneRef().isBlank()) {
            var scene = findAsset(projectAssets, DramaForgeAssetType.SCENE, shot.getSceneRef());
            if (scene != null && isBlank(scene.getReferenceImageUrl())) {
                issues.add(shotLabel + "：场景「" + shot.getSceneRef() + "」缺少设计参考图");
            }
        }

        for (var name : propRefs) {
            var prop = findAsset(projectAssets, DramaForgeAssetType.PROP, name);
            if (prop != null && isBlank(prop.getReferenceImageUrl())) {
                issues.add(shotLabel + "：道具「" + name + "」缺少设计参考图");
            }
        }

        var dialogue = shot.getDialogue() != null ? shot.getDialogue() : "";
        if (!dialogue.isBlank() && !visibleChars.isEmpty()) {
            var audio = DramaForgeStylePrompts.resolveAudioRefs(shot, characterRefs, projectAssets);
            if (audio.urls().isEmpty()) {
                issues.add(shotLabel + "：有对白但缺少角色音色样本（请生成或上传 @Audio 参考音）");
            }
        }

        return issues;
    }

    public ConsistencyReportResponse buildReport(
            UUID projectId,
            List<DramaForgeAsset> assets,
            List<DramaForgeEpisode> episodes,
            List<DramaForgeShot> allShots) {
        long assetsMissingImage = assets.stream()
                .filter(a -> isBlank(a.getReferenceImageUrl()))
                .count();
        long charactersMissingVoice = assets.stream()
                .filter(a -> a.getType() == DramaForgeAssetType.CHARACTER)
                .filter(a -> isBlank(a.getVoiceSampleUrl()))
                .count();
        long shotsMissingBindings = 0;
        long shotsReady = 0;
        long shotsPendingVideo = 0;
        var warnings = new ArrayList<String>();

        for (var shot : allShots) {
            if (shot.getStatus() != DramaForgeShotStatus.VIDEO_DONE) {
                shotsPendingVideo++;
            }
            var charRefs = readNames(shot.getCharacterRefsJson());
            var propRefs = readNames(shot.getPropRefsJson());
            if (charRefs.isEmpty()
                    && (shot.getSceneRef() == null || shot.getSceneRef().isBlank())
                    && propRefs.isEmpty()) {
                shotsMissingBindings++;
            }
            var issues = validateShotForVideo(shot, assets);
            if (issues.isEmpty() && shot.getStatus() != DramaForgeShotStatus.VIDEO_DONE) {
                shotsReady++;
            } else if (!issues.isEmpty() && shot.getStatus() != DramaForgeShotStatus.VIDEO_DONE) {
                if (warnings.size() < 8) {
                    warnings.addAll(issues.stream().limit(2).toList());
                }
            }
        }

        if (assetsMissingImage > 0) {
            warnings.add(0, "有 " + assetsMissingImage + " 个资产缺少设计参考图");
        }
        if (charactersMissingVoice > 0) {
            warnings.add("有 " + charactersMissingVoice + " 个角色缺少音色样本");
        }
        if (shotsMissingBindings > 0) {
            warnings.add("有 " + shotsMissingBindings + " 个镜头未绑定资产");
        }

        var deduped = new ArrayList<String>();
        var seen = new LinkedHashSet<String>();
        for (var w : warnings) {
            if (seen.add(w)) {
                deduped.add(w);
            }
        }

        return new ConsistencyReportResponse(
                assetsMissingImage,
                charactersMissingVoice,
                shotsMissingBindings,
                shotsReady,
                shotsPendingVideo,
                List.copyOf(deduped));
    }

    /** 合成前检查：视频就绪、对白音频、LipSync 配置等 */
    public ComposeReadinessResponse buildComposeReadiness(
            List<DramaForgeShot> shots,
            DramaForgeConfig config) {
        var lipSyncOn = config != null && config.isLipSyncEnabled();
        var endpointOk = config != null
                && config.getLipSyncEndpoint() != null
                && !config.getLipSyncEndpoint().isBlank();
        var mixDialogue = config == null || config.isMixDialogueAudioInCompose();

        var videoDone = 0;
        var withDialogue = 0;
        var missingAudio = 0;
        var warnings = new ArrayList<String>();
        var blockers = new ArrayList<String>();

        for (var shot : shots) {
            if (shot.getStatus() == DramaForgeShotStatus.VIDEO_DONE) {
                videoDone++;
            }
            var dialogue = shot.getDialogue() != null ? shot.getDialogue() : "";
            if (!dialogue.isBlank()) {
                withDialogue++;
                var hasAudio = shot.getDialogueAudioUrl() != null && !shot.getDialogueAudioUrl().isBlank();
                if (!hasAudio && (mixDialogue || lipSyncOn)) {
                    missingAudio++;
                }
            }
        }

        if (shots.isEmpty()) {
            blockers.add("本集没有镜头");
        } else if (videoDone == 0) {
            blockers.add("没有已完成视频的镜头，请先生成并同步视频");
        }

        if (lipSyncOn && !endpointOk) {
            warnings.add("口型同步已开启但未配置 endpoint，合成时将跳过 LipSync");
        }
        if (missingAudio > 0) {
            warnings.add(missingAudio + " 个含对白镜头缺少对白音频，建议先「批量对白TTS」");
        }
        if (lipSyncOn && endpointOk && missingAudio > 0) {
            warnings.add("LipSync 需要 dialogueAudioUrl，缺音频的镜头将跳过口型同步");
        }
        if (!shots.isEmpty() && videoDone < shots.size()) {
            warnings.add("仅 " + videoDone + "/" + shots.size() + " 个镜头视频已完成，合成将跳过未就绪镜头");
        }

        return new ComposeReadinessResponse(
                shots.size(),
                videoDone,
                withDialogue,
                missingAudio,
                lipSyncOn,
                endpointOk,
                mixDialogue,
                List.copyOf(warnings),
                List.copyOf(blockers));
    }

    private static DramaForgeAsset findAsset(
            List<DramaForgeAsset> assets,
            DramaForgeAssetType type,
            String name) {
        if (assets == null || name == null) {
            return null;
        }
        return assets.stream()
                .filter(a -> a.getType() == type)
                .filter(a -> a.getName().equalsIgnoreCase(name.trim()))
                .findFirst()
                .orElse(null);
    }

    private static List<String> readNames(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            var node = mapper.readTree(json);
            if (!node.isArray()) {
                return List.of();
            }
            var list = new ArrayList<String>();
            for (var item : node) {
                if (item.isTextual() && !item.asText().isBlank()) {
                    list.add(item.asText().trim());
                }
            }
            return list;
        } catch (Exception ex) {
            return List.of();
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
