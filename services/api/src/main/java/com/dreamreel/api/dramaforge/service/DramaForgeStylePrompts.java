package com.dreamreel.api.dramaforge.service;

import com.dreamreel.api.dramaforge.domain.DramaForgeAsset;
import com.dreamreel.api.dramaforge.domain.DramaForgeAssetType;
import com.dreamreel.api.dramaforge.domain.DramaForgeConfig;
import com.dreamreel.api.dramaforge.domain.DramaForgeContentMode;
import com.dreamreel.api.dramaforge.domain.DramaForgeEpisode;
import com.dreamreel.api.dramaforge.domain.DramaForgeShot;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * DramaForge 对齐的提示词编排：参考图先行、风格锁定、角色/场景线索注入、负面约束防画风漂移。
 */
final class DramaForgeStylePrompts {

    private DramaForgeStylePrompts() {
    }

    static String assetDesignPrompt(DramaForgeConfig config, DramaForgeAsset asset) {
        return assetDesignPrompt(config, asset, false);
    }

    /**
     * @param privacySafe true 时强化「虚构半写实数字设定」约束（手动合规重生时可启用）
     */
    static String assetDesignPrompt(DramaForgeConfig config, DramaForgeAsset asset, boolean privacySafe) {
        var desc = asset.getDesignPrompt() != null && !asset.getDesignPrompt().isBlank()
                ? asset.getDesignPrompt()
                : asset.getDescription();
        return join(
                assetVisualStyleLock(config, asset.getType()),
                privacySafe ? privacySafeAssetLead(asset.getType()) : "",
                userStyle(config),
                assetDesignTypeConstraint(config, asset.getType(), asset.getName(), desc),
                sanitizeAssetDescription(asset, desc),
                assetDesignNegativeConstraint(config, asset.getType(), asset.getName(), desc),
                privacySafe ? privacySafeAssetNegative(asset.getType()) : ""
        );
    }

    private static String privacySafeAssetLead(DramaForgeAssetType type) {
        return switch (type) {
            case CHARACTER -> """
                    【合规重生·纯文生图】生成完全虚构的半写实数字角色设定稿（CG/数字雕塑/游戏角色卡质感），\
                    明确不是照片、不是实拍、不是名人、不是网红脸；五官适度风格化，皮肤呈数字渲染而非毛孔摄影，\
                    棚拍浅灰底三视图角色卡，仅用于短剧一致性参考。""";
            case SCENE -> """
                    【合规重生·纯文生图】虚构电影场景空镜，纯环境光影与材质，严禁任何人物、人脸、人影剪影。""";
            case PROP -> """
                    【合规重生·纯文生图】孤立道具棚拍，严禁人物、手部、人脸入镜。""";
        };
    }

    private static String privacySafeAssetNegative(DramaForgeAssetType type) {
        return switch (type) {
            case CHARACTER -> "【隐私硬约束】禁止：真人照片、实拍人像、证件照、明星脸、网红脸、写实皮肤毛孔摄影、手机自拍质感、超写实人像摄影。";
            case SCENE -> "【隐私硬约束】禁止：人物、人脸、人影、路人、雕像面孔。";
            case PROP -> "【隐私硬约束】禁止：人物、手部、人脸。";
        };
    }

    /** 资产定妆图比例：角色三视图横版、道具方形、场景跟项目全局 */
    static String resolveAssetDesignAspectRatio(DramaForgeConfig config, DramaForgeAssetType type) {
        return switch (type) {
            case CHARACTER -> "16:9";
            case PROP -> "1:1";
            case SCENE -> resolveAspectRatio(config);
        };
    }

    private static String assetDesignTypeConstraint(
            DramaForgeConfig config,
            DramaForgeAssetType type,
            String name,
            String desc) {
        if (type == DramaForgeAssetType.CHARACTER && looksLikeMinorCharacter(name, desc)) {
            return """
                    【角色定妆·青年形象】%s。青少年角色设定卡（外观约 16–18 岁），三视图（正面、侧面、背面同屏），\
                    校服或日常便装，全身或膝上完整可见，纯色浅灰摄影棚背景，均匀柔光，\
                    无场景、无剧情、无其他人物；清晰展示五官、发型、服装与体态，用于全剧角色一致性。\
                    【随身物锁定】若设定含书包/背包/配饰，三视图必须同一款式、同一颜色、同一位置与比例，禁止视角间变形或换款。\
                    画面为艺术设定稿/角色卡，禁止幼儿、禁止写实裸露、禁止不当构图。"""
                    .formatted(safeCharacterAlias(name));
        }
        return switch (type) {
            case CHARACTER -> """
                    【角色定妆】%s。虚构短剧角色数字设定三视图（正面、侧面、背面同屏），全身或膝上完整可见，\
                    纯色浅灰背景，均匀柔光，无场景、无剧情、无其他人物；清晰展示五官、发型、服装与体态。\
                    【随身物锁定】书包/背包/眼镜/配饰等在三视图中必须完全一致（同款同色同位置），禁止左右视图配件不一致。\
                    画面为艺术设定稿，非真实人物照片、非实拍摄影、非证件照。"""
                    .formatted(name);
            case SCENE -> """
                    【场景参考】%s。空镜环境参考图，无人物、无动物、无字幕，\
                    代表性视角展示空间结构、材质、色调与光影氛围，用于全剧场景一致性。"""
                    .formatted(name);
            case PROP -> """
                    【道具参考】%s。单品道具特写，孤立展示于纯色或简洁台面背景，\
                    无人物、无手部入镜，清晰呈现外形轮廓、材质纹理与尺寸比例，用于跨镜头道具追踪。"""
                    .formatted(name);
        };
    }

    /** TokenFree/部分模型对「小孩/男孩」写实定妆极易触发敏感拦截，改写为安全青年形象描述 */
    private static String sanitizeAssetDescription(DramaForgeAsset asset, String desc) {
        if (desc == null || desc.isBlank()) {
            return "";
        }
        if (asset.getType() != DramaForgeAssetType.CHARACTER || !looksLikeMinorCharacter(asset.getName(), desc)) {
            return desc;
        }
        return desc
                .replace("小男孩", "少年男生")
                .replace("小女孩", "少年女生")
                .replace("小孩子", "青少年")
                .replace("小孩", "青少年")
                .replace("儿童", "青少年")
                .replace("幼童", "青少年")
                .replace("男童", "少年男生")
                .replace("女童", "少年女生")
                + "。角色设定卡、得体日常服装、无敏感内容。";
    }

    private static boolean looksLikeMinorCharacter(String name, String desc) {
        var text = ((name != null ? name : "") + " " + (desc != null ? desc : "")).toLowerCase(Locale.ROOT);
        return text.contains("小男孩") || text.contains("小女孩") || text.contains("小孩")
                || text.contains("儿童") || text.contains("幼童") || text.contains("男童") || text.contains("女童")
                || text.contains("toddler") || text.contains("child") || text.contains("kid ")
                || text.contains("little boy") || text.contains("little girl");
    }

    private static String safeCharacterAlias(String name) {
        if (name == null || name.isBlank()) {
            return "角色";
        }
        return name
                .replace("小男孩", "少年男生")
                .replace("小女孩", "少年女生")
                .replace("小孩", "青少年")
                .replace("儿童", "青少年");
    }

    private static String assetVisualStyleLock(DramaForgeConfig config, DramaForgeAssetType type) {
        var ratioHint = resolveAssetDesignAspectRatio(config, type);
        return switch (config.getContentMode()) {
            case DRAMA -> switch (type) {
                case CHARACTER -> """
                        【画风】虚构短剧角色数字设定稿（半写实风格化），棚拍感灰底三视图，\
                        清晰五官与服装细节，用于角色一致性；禁止真实名人/实拍人像照片、禁止证件照质感。""";
                case SCENE -> """
                        【画风】虚构场景空镜参考，电影级环境光影，写实材质，%s，无人物、无肖像。"""
                        .formatted(aspectRatioLabelForRatio(ratioHint));
                case PROP -> """
                        【画风】写实道具/产品棚拍，干净背景，微距细节清晰，无人像。""";
            };
            case NARRATION -> """
                    【画风】写实插画风格资产设定图，统一色调，非漫画非二次元，适合叙事配图参考。""";
            case AD -> switch (type) {
                case CHARACTER -> "【画风】虚构广告角色数字设定，高质感棚拍感灰底，非真人照片、非名人肖像。";
                case SCENE -> "【画风】商业广告场景空镜，画面干净，品牌感强，无人物。";
                case PROP -> "【画风】商业产品摄影，白底或极简背景，高质感。";
            };
        };
    }

    private static String aspectRatioLabelForRatio(String ratio) {
        return switch (ratio) {
            case "9:16" -> "竖屏 9:16";
            case "16:9" -> "横屏 16:9";
            case "1:1" -> "方形 1:1";
            case "4:3" -> "横屏 4:3";
            case "3:4" -> "竖屏 3:4";
            default -> ratio;
        };
    }

    private static String assetDesignNegativeConstraint(
            DramaForgeConfig config,
            DramaForgeAssetType type,
            String name,
            String desc) {
        var common = "禁止：漫画分格、对话框、文字水印、线稿草图、赛璐璐、二次元、Q版、多图拼贴边框、低清晰度。";
        var typeSpecific = switch (type) {
            case CHARACTER -> "禁止：复杂背景、剧情场景、多人同框、夸张表情、电影剧照构图、手持道具特写。";
            case SCENE -> "禁止：人物、动物、车辆载人、字幕、分镜边框、漫画元素。";
            case PROP -> "禁止：人物、手部、复杂场景、多物品堆砌、模糊、强烈景深虚化。";
        };
        var minorSafe = (type == DramaForgeAssetType.CHARACTER && looksLikeMinorCharacter(name, desc))
                ? "禁止：幼儿面孔、暴露、暴力、不当姿势、性感化表现。"
                : "";
        if (config.getContentMode() == DramaForgeContentMode.DRAMA) {
            return "【负面约束】" + common + typeSpecific + minorSafe + "禁止插画感、禁止非写实渲染。";
        }
        return "【负面约束】" + common + typeSpecific + minorSafe;
    }

    static String storyboardPrompt(
            DramaForgeConfig config,
            DramaForgeEpisode episode,
            DramaForgeShot shot,
            List<String> characterRefs,
            List<String> propRefs,
            List<DramaForgeAsset> projectAssets,
            boolean img2img,
            boolean fromCharacterDesign,
            boolean fromSceneDesign,
            boolean chainFromPrevious) {
        return storyboardFirstFramePrompt(
                config, episode, shot, characterRefs, propRefs, projectAssets,
                img2img, fromCharacterDesign, fromSceneDesign, chainFromPrevious);
    }

    /** 分镜首帧：静态关键帧、动作起始态，不含视频运镜/口型绑定 */
    static String storyboardFirstFramePrompt(
            DramaForgeConfig config,
            DramaForgeEpisode episode,
            DramaForgeShot shot,
            List<String> characterRefs,
            List<String> propRefs,
            List<DramaForgeAsset> projectAssets,
            boolean img2img,
            boolean fromCharacterDesign,
            boolean fromSceneDesign,
            boolean chainFromPrevious) {
        return storyboardFirstFramePrompt(
                config, episode, shot, characterRefs, propRefs, projectAssets,
                img2img, fromCharacterDesign, fromSceneDesign, chainFromPrevious, List.of());
    }

    /**
     * @param assetBindings 资产参考图绑定（角色三维定妆优先），prompt 中用 {@code 角色名=@url} 声明
     */
    static String storyboardFirstFramePrompt(
            DramaForgeConfig config,
            DramaForgeEpisode episode,
            DramaForgeShot shot,
            List<String> characterRefs,
            List<String> propRefs,
            List<DramaForgeAsset> projectAssets,
            boolean img2img,
            boolean fromCharacterDesign,
            boolean fromSceneDesign,
            boolean chainFromPrevious,
            List<AssetImageBinding> assetBindings) {
        var camera = shot.getCameraNote() != null && !shot.getCameraNote().isBlank()
                ? "构图参考：" + shot.getCameraNote() + "（静态分镜，无实际运镜）。"
                : "";
        var img2imgLead = "";
        if (img2img) {
            if (fromCharacterDesign) {
                img2imgLead = "【角色定妆硬锁】人物五官/发型/服装/体态必须严格匹配下方 @资产图片地址 对应的三维定妆参考图；"
                        + "禁止换成另一张脸或另一套服装；上一镜连贯只影响场景光影构图，不得覆盖定妆身份。";
            } else if (chainFromPrevious) {
                img2imgLead = "【镜间连贯】严格以参考图为上一镜尾帧，保持画面风格、摄影质感、色调与角色外观一致，作为本镜动作起始瞬间的首帧分镜。";
            } else if (fromSceneDesign) {
                img2imgLead = "【场景参考约束】严格以场景空镜参考图为环境结构、材质、色调与光影基准，在本场景内安排角色动作起始构图，禁止改变场景气质。";
            } else {
                img2imgLead = "【参考图约束】以参考图中的画面风格与场景气质为基准，生成同一世界观下的分镜首帧。";
            }
        }
        var plannedScene = shot.getSceneRef() != null && !shot.getSceneRef().isBlank()
                ? "【规划场景】" + shot.getSceneRef() + "。"
                : "";
        return join(
                visualStyleLock(config),
                userStyle(config),
                "【分镜首帧】电影分镜静态关键帧，表现动作起始瞬间，单帧定格，无运动模糊、无拖影、无连续动作。",
                img2imgLead,
                formatAssetAtBindings(assetBindings),
                "第" + episode.getEpisodeNumber() + "集分镜，镜头" + shot.getShotNumber() + "，首帧。",
                extractLensAction(shot.getDescription()) + "。",
                shot.getDialogue() != null && !shot.getDialogue().isBlank()
                        ? "对白氛围：" + shot.getDialogue() + "。"
                        : "",
                camera,
                plannedScene,
                formatCharacterClues(characterRefs, projectAssets),
                formatSceneClue(shot, projectAssets),
                formatPropCluesFromRefs(propRefs, projectAssets),
                continuityConstraint(config),
                styleNegativeConstraint(config)
        );
    }

    /** 资产图在 prompt 中的绑定：角色名=@https://... */
    record AssetImageBinding(String name, String kind, String url) {}

    static String formatAssetAtBindings(List<AssetImageBinding> bindings) {
        if (bindings == null || bindings.isEmpty()) {
            return "";
        }
        var sb = new StringBuilder("【资产绑定】");
        for (var b : bindings) {
            if (b == null || b.url() == null || b.url().isBlank()) {
                continue;
            }
            var kind = b.kind() != null && !b.kind().isBlank() ? b.kind() : "资产";
            var name = b.name() != null && !b.name().isBlank() ? b.name() : kind;
            sb.append(kind).append("「").append(name).append("」=@").append(b.url().trim()).append("；");
        }
        sb.append("image_urls 顺序与上述 @地址一一对应；出场人物外貌必须以对应 @URL 三维定妆为准。");
        return sb.toString();
    }

    /** 分镜尾帧：同镜头动作结束态，以首帧为参考 img2img */
    static String storyboardLastFramePrompt(
            DramaForgeConfig config,
            DramaForgeEpisode episode,
            DramaForgeShot shot,
            List<String> characterRefs,
            List<String> propRefs,
            List<DramaForgeAsset> projectAssets) {
        var camera = shot.getCameraNote() != null && !shot.getCameraNote().isBlank()
                ? "构图可随动作轻微变化：" + shot.getCameraNote() + "。"
                : "";
        var plannedScene = shot.getSceneRef() != null && !shot.getSceneRef().isBlank()
                ? "【规划场景】" + shot.getSceneRef() + "。"
                : "";
        return join(
                visualStyleLock(config),
                userStyle(config),
                "【分镜尾帧】严格以首帧分镜为人物、场景、光影、色调基准，将动作推进至本镜头结束瞬间，生成静态尾帧关键帧。",
                "保持角色服装、五官、场景结构与首帧一致，仅改变姿态与动作进度至结束态。无运动模糊、无拖影。",
                "第" + episode.getEpisodeNumber() + "集分镜，镜头" + shot.getShotNumber() + "，尾帧。",
                extractLensAction(shot.getDescription()) + "，动作结束态。",
                camera,
                plannedScene,
                formatCharacterClues(characterRefs, projectAssets),
                formatSceneClue(shot, projectAssets),
                formatPropCluesFromRefs(propRefs, projectAssets),
                continuityConstraint(config),
                styleNegativeConstraint(config)
        );
    }

    /** 列表预览：分镜首帧专用提示词（未落库时现场拼装） */
    static String previewStoryboardPrompt(
            DramaForgeConfig config,
            DramaForgeEpisode episode,
            DramaForgeShot shot,
            List<String> characterRefs,
            List<String> propRefs,
            List<DramaForgeAsset> projectAssets) {
        return storyboardFirstFramePrompt(
                config, episode, shot, characterRefs, propRefs, projectAssets,
                false, false, false, false);
    }

    static String gridStoryboardPrompt(
            DramaForgeConfig config,
            DramaForgeEpisode episode,
            List<DramaForgeShot> group,
            List<DramaForgeAsset> projectAssets) {
        var sb = new StringBuilder(join(
                visualStyleLock(config),
                userStyle(config),
                "第" + episode.getEpisodeNumber() + "集连续分镜宫格（2x2），每格为独立电影画面，无分格边框、无漫画对话框。",
                formatGlobalCharacterClues(projectAssets),
                styleNegativeConstraint(config)
        ));
        for (var shot : group) {
            sb.append("【格").append(shot.getShotNumber()).append("】").append(shot.getDescription()).append("；");
        }
        return sb.toString();
    }

    static String videoPrompt(
            DramaForgeConfig config,
            DramaForgeShot shot,
            List<String> characterRefs,
            List<DramaForgeAsset> projectAssets,
            boolean fromStoryboard) {
        return videoPromptFromAssets(config, shot, characterRefs, List.of(), projectAssets,
                fromStoryboard ? List.of("分镜定帧") : List.of("参考图"));
    }

    /** 五步流程：用角色/场景/道具设计图直接参考生视频（Seedance @ImageN / @AudioN） */
    static String videoPromptFromAssets(
            DramaForgeConfig config,
            DramaForgeShot shot,
            List<String> characterRefs,
            List<String> propRefs,
            List<DramaForgeAsset> projectAssets,
            List<String> imageLabels) {
        return videoPromptFromAssets(config, shot, characterRefs, propRefs, projectAssets, imageLabels, List.of());
    }

    static String videoPromptFromAssets(
            DramaForgeConfig config,
            DramaForgeShot shot,
            List<String> characterRefs,
            List<String> propRefs,
            List<DramaForgeAsset> projectAssets,
            List<String> imageLabels,
            List<String> audioLabels) {
        return videoPromptFromAssets(
                config, shot, characterRefs, propRefs, projectAssets, imageLabels, audioLabels, null);
    }

    static String videoPromptFromAssets(
            DramaForgeConfig config,
            DramaForgeShot shot,
            List<String> characterRefs,
            List<String> propRefs,
            List<DramaForgeAsset> projectAssets,
            List<String> imageLabels,
            List<String> audioLabels,
            List<String> imageUrls) {
        var mapping = new StringBuilder("【参考映射】");
        for (int i = 0; i < imageLabels.size(); i++) {
            mapping.append("@Image").append(i + 1).append("=").append(imageLabels.get(i));
            if (imageUrls != null && i < imageUrls.size()
                    && imageUrls.get(i) != null && !imageUrls.get(i).isBlank()
                    && (isOpeningFrameLabel(imageLabels.get(i)) || isEndingFrameLabel(imageLabels.get(i)))) {
                mapping.append("(").append(imageUrls.get(i).trim()).append(")");
            }
            mapping.append("；");
        }
        for (int i = 0; i < audioLabels.size(); i++) {
            mapping.append("@Audio").append(i + 1).append("=").append(audioLabels.get(i)).append("；");
        }
        var actionBind = buildActionImageBind(shot, imageLabels);
        var imgLead = """
                【资产参考生视频】严格按 @Image 参考图锁定角色五官/发型/服装/随身物（含书包背包配饰）、\
                场景空间光影、道具外形材质。禁止换脸、换人、改变年龄感或服装；\
                书包/背包颜色款式与背负位置必须与参考图一致，禁止视角间变形、换色或消失。\
                场景结构与道具外观须与参考图一致。按镜头描述安排动作与运镜，允许自然运动，禁止完全静止。""";
        if (hasSmartMultiFramePair(imageLabels)) {
            imgLead = """
                    【全能参考·首尾帧效果】本请求走多模态参考（reference_image），不用首尾帧 API。\
                    @Image1 视为本镜首帧（起始画面），@Image2 视为本镜尾帧（结束画面）；\
                    视频必须从 @Image1 开场，并在结尾精确收敛到 @Image2 的构图/人物姿态；\
                    中间按镜头描述自然过渡，禁止跳切、漂移或偏离首尾帧。\
                    其余 @Image 锁定角色五官/发型/服装与场景道具外观。禁止换脸、换人。""";
        } else if (imageLabels != null && !imageLabels.isEmpty()
                && isOpeningFrameLabel(imageLabels.get(0))) {
            imgLead = """
                    【首帧参考】本请求走多模态参考（reference_image）。\
                    @Image1 视为本镜首帧（起始画面），视频必须从 @Image1 开场，严格锁定首帧构图、角色姿态与光影；\
                    按镜头描述自然发展动作与运镜，禁止跳切或漂移。\
                    其余 @Image 锁定角色五官/发型/服装与场景道具外观。禁止换脸、换人。""";
        }
        var frameUrlBind = buildFirstLastFrameUrlBind(imageLabels, imageUrls);
        var seedanceBind = buildSeedanceIdentityBind(imageLabels);
        var hasDialogue = shot.getDialogue() != null && !shot.getDialogue().isBlank();
        // 无对白时即使误传了 audioLabels 也禁止「必须说话」指令，避免模型瞎编台词
        var audioLead = dialogueAudioLead(hasDialogue, audioLabels);
        var camera = shot.getCameraNote() != null && !shot.getCameraNote().isBlank()
                ? "运镜：" + shot.getCameraNote() + "。"
                : "";
        var plannedScene = shot.getSceneRef() != null && !shot.getSceneRef().isBlank()
                ? "【规划场景】" + shot.getSceneRef() + "。"
                : "";
        var desc = sanitizeShotBodyForVideoPrompt(
                shot.getDescription() != null ? shot.getDescription().trim() : "");
        // 规划阶段已写入【镜头】/【角色线索】时，避免重复拼接资产线索
        var enriched = desc.contains("【镜头】") || desc.contains("【角色线索】");
        var firstLastConsistency = hasSmartMultiFramePair(imageLabels)
                ? "【首尾帧一致性优先级】首帧一致性优先级高于角色与场景一致性；尾帧一致性优先级低于角色与场景一致性。"
                : "";
        return join(
                visualStyleLock(config),
                userStyle(config),
                imgLead,
                frameUrlBind,
                firstLastConsistency,
                seedanceBind,
                audioLead,
                mapping.toString(),
                actionBind,
                enriched ? desc : (desc.isBlank() ? "" : desc + "。"),
                hasDialogue
                        ? dialogueAudioClause(shot.getDialogue().trim())
                        : "【无对白】本镜禁止任何台词、旁白、口型说话或可读字幕；环境音即可。",
                enriched ? "" : camera,
                enriched ? "" : plannedScene,
                enriched ? "" : formatCharacterClues(characterRefs, projectAssets),
                enriched ? "" : formatSceneClue(shot, projectAssets),
                enriched ? "" : formatPropCluesFromRefs(propRefs, projectAssets),
                identityLockClause(characterRefs, projectAssets),
                multiShotClause(shot),
                "【身份锁定】人物必须与角色参考图一致，不得生成另一个人的面孔。",
                continuityConstraint(config),
                styleNegativeConstraint(config),
                hasDialogue ? dialogueAudioNegativeConstraint() : ""
        );
    }

    /**
     * 与角色线索同款：参考名=@https://...
     */
    private static String buildFirstLastFrameUrlBind(List<String> imageLabels, List<String> imageUrls) {
        if (imageUrls == null || imageUrls.isEmpty()
                || imageLabels == null || imageLabels.isEmpty()) {
            return "";
        }
        var firstUrl = imageUrls.get(0) != null ? imageUrls.get(0).trim() : "";
        if (firstUrl.isBlank() || !isOpeningFrameLabel(imageLabels.get(0))) {
            return "";
        }
        if (hasSmartMultiFramePair(imageLabels) && imageUrls.size() >= 2) {
            var lastUrl = imageUrls.get(1) != null ? imageUrls.get(1).trim() : "";
            if (!lastUrl.isBlank()) {
                return "【首尾帧绑定】视频首帧=@" + firstUrl + "；视频尾帧=@" + lastUrl + "；首尾帧优先级最高，不能有任何改动，严格按照这里绑定的视频首尾帧生成视频";
            }
        }
        return "【视频首帧绑定】视频首帧=@" + firstUrl + "；严格按照首帧起始画面生成视频，禁止偏离首帧构图、角色姿态与光影；";
    }

    /** 有对白时的音色/口型引导（优先对接白 TTS 录音做口型同步） */
    static String dialogueAudioLead(boolean hasDialogue, List<String> audioLabels) {
        if (!hasDialogue) {
            return "";
        }
        var labels = audioLabels != null ? audioLabels : List.<String>of();
        var usesDialogueTrack = labels.stream().anyMatch(l -> l != null && l.startsWith("dialogue:"));
        if (usesDialogueTrack) {
            return "【对白锁定】严格口型同步 @Audio1 对白录音，逐字朗读禁止改词；"
                    + "对白清晰响亮置于前景；环境音极低；禁止背景音乐与嘈杂人声盖过台词。";
        }
        if (!labels.isEmpty()) {
            return "【音色锁定】对白须匹配 @Audio1 声线音色与说话气质；口型与对白同步。"
                    + "【音频混音】对白清晰前景，环境音极低，禁止背景音乐与嘈杂噪声盖过台词。";
        }
        return "【音频混音】对白清晰前景，环境音极低，禁止背景音乐与嘈杂噪声盖过台词。";
    }

    static String dialogueAudioClause(String dialogue) {
        var line = dialogue == null ? "" : dialogue.trim();
        return "对白（须清晰可辨，近麦人声）：「" + line + "」。"
                + "【Seedance字幕】请 Seedance 2.0 在生成视频时，根据对白自行在画面底部叠加中文字幕，"
                + "字幕文案必须与对白完全一致：「" + line + "」；"
                + "样式：白字黑边、水平居中、字号适中、全程清晰可读；"
                + "说话时显示、无对白段落可不显示；禁止漏字、改词、错别字或画面完全无字幕。"
                + "Mix note: dialogue loud and clear in foreground; ambient room tone very low; no music.";
    }

    static String dialogueAudioNegativeConstraint() {
        return "【音频负面】禁止：背景音乐、嘈杂环境音盖过对白、多人喧哗、交通/机械轰鸣压过人声、含糊不清的台词。";
    }

    /** 按角色 identityLockStrength / loraRef 强化提示 */
    static String identityLockClause(List<String> characterRefs, List<DramaForgeAsset> projectAssets) {
        if (characterRefs == null || characterRefs.isEmpty() || projectAssets == null) {
            return "";
        }
        var sb = new StringBuilder("【身份强度】");
        var any = false;
        for (var name : characterRefs) {
            var asset = projectAssets.stream()
                    .filter(a -> a.getType() == DramaForgeAssetType.CHARACTER)
                    .filter(a -> a.getName().equalsIgnoreCase(name))
                    .findFirst()
                    .orElse(null);
            if (asset == null) {
                continue;
            }
            any = true;
            var strength = asset.getIdentityLockStrength() != null ? asset.getIdentityLockStrength() : 75;
            strength = Math.max(0, Math.min(100, strength));
            sb.append(asset.getName()).append("锁定").append(strength).append("%");
            if (asset.getLoraRef() != null && !asset.getLoraRef().isBlank()) {
                sb.append("(ID:").append(asset.getLoraRef().trim()).append(")");
            }
            sb.append("；");
            if (strength >= 80) {
                sb.append("严格复用该角色参考图五官与服装，禁止微调外貌。");
            }
        }
        return any ? sb.toString() : "";
    }

    /** Seedance 单次多机位：在 prompt 中列出编号运镜 beat */
    static String multiShotClause(DramaForgeShot shot) {
        if (!Boolean.TRUE.equals(shot.getModelMultiShot())) {
            return "";
        }
        var template = shot.getMultiShotTemplate() != null ? shot.getMultiShotTemplate() : "dialogue";
        var presets = DramaForgeCameraTemplates.resolve(template);
        var sb = new StringBuilder(
                "【多机位叙事】在同一段视频内按编号切换镜头，角色外观/服装/场景光影全程一致，禁止中途换人：");
        for (int i = 0; i < presets.size(); i++) {
            var p = presets.get(i);
            sb.append(" Shot").append(i + 1).append("(").append(p.label()).append(")：")
                    .append(p.cameraNote()).append("。");
        }
        if (shot.getCameraNote() != null && !shot.getCameraNote().isBlank()) {
            sb.append("主运镜补充：").append(shot.getCameraNote()).append("。");
        }
        return sb.toString();
    }

    /**
     * 去掉描述里残留的生成期绑定段（旧 @Image 映射 / 风格锁等），避免与真实提交参考图不一致。
     */
    static String sanitizeShotBodyForVideoPrompt(String description) {
        if (description == null || description.isBlank()) {
            return "";
        }
        var text = description.trim();
        var dropMarkers = List.of(
                "【风格锁定】", "【项目风格】", "【资产参考生视频】", "【资产参考",
                "【Seedance绑定】", "【参考映射】", "【镜头构图】", "【音色锁定】",
                "【身份锁定】", "【身份强度】", "【多机位叙事】", "【一致性】", "【负面约束】", "【合规】");
        for (var marker : dropMarkers) {
            int from;
            while ((from = text.indexOf(marker)) >= 0) {
                int next = findNextSection(text, from + marker.length());
                text = text.substring(0, from) + text.substring(next);
            }
        }
        text = text.replaceAll("@Image\\d+", "")
                .replaceAll("@Audio\\d+", "")
                .replaceAll("\\s{2,}", " ")
                .trim();
        if (text.isBlank()) {
            return extractLensAction(description);
        }
        return text;
    }

    private static int findNextSection(String text, int from) {
        int next = text.indexOf('【', from);
        return next >= 0 ? next : text.length();
    }

    public record MediaRefPair(List<String> urls, List<String> labels) {
        static MediaRefPair empty() {
            return new MediaRefPair(List.of(), List.of());
        }
    }

    /**
     * 与真实生视频提交完全一致的参考图顺序：
     * 出镜角色(有图) → 场景(有图) → 道具(有图)，最多 5 张。
     */
    static MediaRefPair resolveImageRefs(
            DramaForgeShot shot,
            List<String> characterRefs,
            List<String> propRefs,
            List<DramaForgeAsset> projectAssets) {
        var urls = new ArrayList<String>();
        var labels = new ArrayList<String>();
        var seen = new java.util.LinkedHashSet<String>();
        var desc = shot.getDescription() != null ? shot.getDescription() : "";
        var dialogue = shot.getDialogue() != null ? shot.getDialogue() : "";

        if (characterRefs != null) {
            for (var name : characterRefs) {
                if (!includeCharacterInBindings(shot, name, desc, dialogue)) {
                    continue;
                }
                addImageRef(name, DramaForgeAssetType.CHARACTER, projectAssets, urls, labels, seen, "角色");
            }
        }
        if (shot.getSceneRef() != null && !shot.getSceneRef().isBlank()) {
            addImageRef(shot.getSceneRef(), DramaForgeAssetType.SCENE, projectAssets, urls, labels, seen, "场景");
        }
        if (propRefs != null) {
            for (var name : propRefs) {
                addImageRef(name, DramaForgeAssetType.PROP, projectAssets, urls, labels, seen, "道具");
            }
        }

        if (urls.isEmpty() && projectAssets != null) {
            var text = (desc + dialogue).toLowerCase(Locale.ROOT);
            for (var asset : projectAssets) {
                if (asset.getReferenceImageUrl() == null || asset.getReferenceImageUrl().isBlank()) {
                    continue;
                }
                if (!text.contains(asset.getName().toLowerCase(Locale.ROOT))) {
                    continue;
                }
                if (asset.getType() == DramaForgeAssetType.CHARACTER
                        && !includeCharacterInBindings(shot, asset.getName(), desc, dialogue)) {
                    continue;
                }
                var kind = asset.getType() == DramaForgeAssetType.CHARACTER ? "角色"
                        : asset.getType() == DramaForgeAssetType.SCENE ? "场景" : "道具";
                addImageRef(asset.getName(), asset.getType(), projectAssets, urls, labels, seen, kind);
            }
        }

        return trimRefs(urls, labels, 5);
    }

    static MediaRefPair resolveAudioRefs(
            DramaForgeShot shot,
            List<String> characterRefs,
            List<DramaForgeAsset> projectAssets) {
        var urls = new ArrayList<String>();
        var labels = new ArrayList<String>();
        if (characterRefs == null || projectAssets == null) {
            return MediaRefPair.empty();
        }
        var dialogue = shot.getDialogue() != null ? shot.getDialogue() : "";
        // 镜头无对白：不传 @Audio，避免 Seedance 按音色参考乱生成台词
        if (dialogue.isBlank()) {
            return MediaRefPair.empty();
        }

        var desc = shot.getDescription() != null ? shot.getDescription() : "";

        // 优先：对白中出现的角色（说话人）
        for (var name : characterRefs) {
            if (!dialogue.contains(name)) {
                continue;
            }
            if (tryAddAudioRef(name, projectAssets, urls, labels)) {
                return new MediaRefPair(List.copyOf(urls), List.copyOf(labels));
            }
        }

        for (var name : characterRefs) {
            if (!includeCharacterInBindings(shot, name, desc, dialogue)) {
                continue;
            }
            if (tryAddAudioRef(name, projectAssets, urls, labels)) {
                break;
            }
        }
        return new MediaRefPair(List.copyOf(urls), List.copyOf(labels));
    }

    private static boolean tryAddAudioRef(
            String name,
            List<DramaForgeAsset> projectAssets,
            List<String> urls,
            List<String> labels) {
        if (projectAssets == null || name == null) {
            return false;
        }
        var found = projectAssets.stream()
                .filter(a -> a.getType() == DramaForgeAssetType.CHARACTER)
                .filter(a -> a.getName().equalsIgnoreCase(name))
                .filter(a -> a.getVoiceSampleUrl() != null && !a.getVoiceSampleUrl().isBlank())
                .findFirst();
        if (found.isEmpty()) {
            return false;
        }
        var asset = found.get();
        urls.add(asset.getVoiceSampleUrl().trim());
        var voiceTag = asset.getVoiceLabel() != null && !asset.getVoiceLabel().isBlank()
                ? asset.getVoiceLabel()
                : asset.getName();
        labels.add("voice:" + voiceTag);
        return true;
    }

    static boolean includeCharacterInBindings(
            DramaForgeShot shot,
            String name,
            String description,
            String dialogue) {
        if (Boolean.TRUE.equals(shot.getForceCharacterBinding())) {
            return name != null && !name.isBlank();
        }
        return isCharacterVisibleInShot(name, description, dialogue);
    }

    static boolean isCharacterVisibleInShot(String name, String description, String dialogue) {
        if (name == null || name.isBlank()) {
            return false;
        }
        if (description != null && description.contains(name)) {
            return true;
        }
        return dialogue == null || !dialogue.contains(name);
    }

    private static void addImageRef(
            String name,
            DramaForgeAssetType type,
            List<DramaForgeAsset> projectAssets,
            List<String> urls,
            List<String> labels,
            java.util.Set<String> seen,
            String kindLabel) {
        if (name == null || name.isBlank() || projectAssets == null) {
            return;
        }
        projectAssets.stream()
                .filter(a -> a.getType() == type)
                .filter(a -> a.getName().equalsIgnoreCase(name.trim()))
                .filter(a -> a.getReferenceImageUrl() != null && !a.getReferenceImageUrl().isBlank())
                .findFirst()
                .ifPresent(asset -> {
                    var key = asset.getType() + ":" + asset.getName().toLowerCase(Locale.ROOT);
                    if (seen.add(key)) {
                        urls.add(asset.getReferenceImageUrl());
                        labels.add(kindLabel + ":" + asset.getName());
                    }
                });
    }

    private static MediaRefPair trimRefs(List<String> urls, List<String> labels, int max) {
        if (urls.size() <= max) {
            return new MediaRefPair(List.copyOf(urls), List.copyOf(labels));
        }
        return new MediaRefPair(
                List.copyOf(urls.subList(0, max)),
                List.copyOf(labels.subList(0, max)));
    }

    /**
     * 规划阶段展示/落库的完整镜头提示词：镜头动作 + 参与角色/场景/道具线索。
     */
    static String materializePlanningDescription(
            DramaForgeShot shot,
            List<String> characterRefs,
            List<String> propRefs,
            List<DramaForgeAsset> projectAssets) {
        var action = extractLensAction(shot.getDescription());
        return join(
                "【镜头】" + (action.isBlank() ? "待补充画面动作" : action),
                shot.getSceneRef() != null && !shot.getSceneRef().isBlank()
                        ? "【规划场景】" + shot.getSceneRef()
                        : "",
                formatCharacterClues(characterRefs, projectAssets),
                formatSceneClue(shot, projectAssets),
                formatPropCluesFromRefs(propRefs, projectAssets),
                shot.getCameraNote() != null && !shot.getCameraNote().isBlank()
                        ? "【运镜】" + shot.getCameraNote().trim()
                        : ""
        );
    }

    /** 列表/编辑预览：与实际生视频完全同一套参考图/音频映射。 */
    static String previewVideoPrompt(
            DramaForgeConfig config,
            DramaForgeShot shot,
            List<String> characterRefs,
            List<String> propRefs,
            List<DramaForgeAsset> projectAssets) {
        var images = resolveImageRefs(shot, characterRefs, propRefs, projectAssets);
        var audio = resolveAudioRefs(shot, characterRefs, projectAssets);
        var labels = images.labels().isEmpty() ? List.of("参考图(待生成设计图)") : images.labels();
        return videoPromptFromAssets(
                config, shot, characterRefs, propRefs, projectAssets, labels, audio.labels());
    }

    static String extractLensAction(String description) {
        if (description == null || description.isBlank()) {
            return "";
        }
        var text = description.trim();
        if (text.contains("【镜头】")) {
            var start = text.indexOf("【镜头】") + "【镜头】".length();
            var end = text.indexOf('【', start);
            var core = end > start ? text.substring(start, end) : text.substring(start);
            return stripOuterPunct(core);
        }
        var markers = List.of(
                "【角色线索】", "【场景线索】", "【道具线索】", "【规划场景】",
                "【运镜】", "【风格锁定】", "【资产参考", "【Seedance绑定】", "【参考映射】");
        var cut = text.length();
        for (var marker : markers) {
            var idx = text.indexOf(marker);
            if (idx >= 0 && idx < cut) {
                cut = idx;
            }
        }
        if (cut < text.length()) {
            return stripOuterPunct(text.substring(0, cut));
        }
        return text;
    }

    private static String stripOuterPunct(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("^[：:\\s。]+", "").replaceAll("[。\\s]+$", "").trim();
    }

    /** 把镜头动作绑到 @ImageN，符合 Seedance reference-to-video 写法 */
    private static String buildActionImageBind(DramaForgeShot shot, List<String> imageLabels) {
        if (imageLabels == null || imageLabels.isEmpty()) {
            return "";
        }
        String charImg = null;
        String sceneImg = null;
        for (int i = 0; i < imageLabels.size(); i++) {
            var tag = "@Image" + (i + 1);
            var label = imageLabels.get(i);
            if (label.startsWith("角色") && charImg == null) {
                charImg = tag;
            } else if (label.startsWith("场景") && sceneImg == null) {
                sceneImg = tag;
            }
        }
        if (charImg == null) {
            charImg = "@Image1";
        }
        var sb = new StringBuilder("【镜头构图】");
        sb.append(charImg).append(" 出演本镜头，人物五官/发型/肤色必须与").append(charImg).append("参考图一致");
        if (sceneImg != null) {
            sb.append("，场景空间以 ").append(sceneImg).append(" 为基准");
        }
        for (int i = 0; i < imageLabels.size(); i++) {
            if (imageLabels.get(i).startsWith("道具")) {
                sb.append("，").append("@Image").append(i + 1).append(" 为道具外观参考");
            }
        }
        sb.append("。");
        return sb.toString();
    }

    /** Seedance 官方推荐：在 prompt 中显式声明每个 @Image 的用途 */
    private static String buildSeedanceIdentityBind(List<String> imageLabels) {
        if (imageLabels == null || imageLabels.isEmpty()) {
            return "";
        }
        var sb = new StringBuilder("【Seedance绑定】");
        for (int i = 0; i < imageLabels.size(); i++) {
            var label = imageLabels.get(i);
            var tag = "@Image" + (i + 1);
            if (isOpeningFrameLabel(label)) {
                sb.append(tag).append(" 为本镜起始画面（等同首帧），视频必须从此帧开场；");
            } else if (isEndingFrameLabel(label)) {
                sb.append(tag).append(" 为本镜结束画面（等同尾帧），视频结尾必须收敛到此帧；");
            } else if (label.startsWith("角色")) {
                sb.append(tag).append(" 提供角色 ").append(label.substring(label.indexOf(':') + 1))
                        .append(" 的五官/发型/服装身份，禁止换脸；");
            } else if (label.startsWith("场景")) {
                sb.append(tag).append(" 提供场景空间与光影；");
            } else if (label.startsWith("道具")) {
                sb.append(tag).append(" 提供道具 ").append(label.substring(label.indexOf(':') + 1))
                        .append(" 的外形材质；");
            }
        }
        return sb.toString();
    }

    static boolean hasSmartMultiFramePair(List<String> imageLabels) {
        return imageLabels != null
                && imageLabels.size() >= 2
                && isOpeningFrameLabel(imageLabels.get(0))
                && isEndingFrameLabel(imageLabels.get(1));
    }

    private static boolean isOpeningFrameLabel(String label) {
        return label != null && (
                "上一镜成片尾帧".equals(label)
                        || "上一镜分镜尾帧".equals(label)
                        || "上一镜尾帧".equals(label)
                        || "镜头首帧".equals(label)
                        || "分镜首帧".equals(label)
                        || "分镜定帧".equals(label));
    }

    private static boolean isEndingFrameLabel(String label) {
        return label != null && ("镜头尾帧".equals(label) || "分镜尾帧".equals(label));
    }

    static String videoPrompt(DramaForgeConfig config, DramaForgeShot shot) {
        return videoPrompt(config, shot, List.of(), List.of(), false);
    }

    /** 优先使用项目全局配置，否则按内容模式推断 */
    static String resolveAspectRatio(DramaForgeConfig config) {
        if (config.getAspectRatio() != null && !config.getAspectRatio().isBlank()) {
            return config.getAspectRatio().trim();
        }
        return switch (config.getContentMode()) {
            case DRAMA, NARRATION -> "9:16";
            case AD -> "16:9";
        };
    }

    static String aspectRatioLabel(DramaForgeConfig config) {
        var ratio = resolveAspectRatio(config);
        return switch (ratio) {
            case "9:16" -> "竖屏 9:16";
            case "16:9" -> "横屏 16:9";
            case "1:1" -> "方形 1:1";
            case "4:3" -> "横屏 4:3";
            case "3:4" -> "竖屏 3:4";
            default -> ratio;
        };
    }

    /** 按内容模式锁定视觉风格（DramaForge drama / narration / ad） */
    static String visualStyleLock(DramaForgeConfig config) {
        return switch (config.getContentMode()) {
            case DRAMA -> """
                    【风格锁定】虚构短剧影视风格（非真实人物素材），电影级光影与构图，\
                    写实服装与场景材质，浅景深，35mm 镜头质感，清晰 480p，色彩分级统一，%s 构图。\
                    角色为数字虚构形象，禁止真实名人面孔。"""
                    .formatted(aspectRatioLabel(config));
            case NARRATION -> """
                    【风格锁定】写实插画叙事风格，电影感构图与统一色调，细腻光影，\
                    非漫画非二次元，适合有声书画面。""";
            case AD -> """
                    【风格锁定】商业广告影像风格，高质感摄影构图，画面干净，品牌感强，\
                    角色为虚构数字形象，非真人照片。""";
        };
    }

    static String styleNegativeConstraint(DramaForgeConfig config) {
        var common = "禁止出现：漫画分格、四格漫画、对话框、气泡文字、线稿草图、手绘涂鸦、赛璐璐、二次元动漫、卡通 Q 版、low poly、像素风、风格突变、真实名人面孔、证件照。";
        if (config.getContentMode() == DramaForgeContentMode.DRAMA) {
            return "【负面约束】" + common + "禁止把参考图理解为真实人物隐私素材。";
        }
        return "【负面约束】" + common;
    }

    static String continuityConstraint(DramaForgeConfig config) {
        return "【一致性】角色脸型/发型/服装/书包背包等随身物、场景色调、摄影风格须与参考图及已生成画面完全一致。";
    }

    private static String userStyle(DramaForgeConfig config) {
        if (config.getStylePrompt() == null || config.getStylePrompt().isBlank()) {
            return "";
        }
        return "【项目风格】" + config.getStylePrompt().trim() + "。";
    }

    private static String formatCharacterClues(List<String> refs, List<DramaForgeAsset> assets) {
        if (refs == null || refs.isEmpty()) {
            return "";
        }
        var sb = new StringBuilder("【角色线索】");
        for (var name : refs) {
            assets.stream()
                    .filter(a -> a.getType() == DramaForgeAssetType.CHARACTER)
                    .filter(a -> a.getName().equalsIgnoreCase(name))
                    .findFirst()
                    .ifPresent(asset -> {
                        sb.append(asset.getName());
                        if (asset.getReferenceImageUrl() != null && !asset.getReferenceImageUrl().isBlank()) {
                            sb.append("=@").append(asset.getReferenceImageUrl().trim());
                        }
                        if (asset.getDescription() != null && !asset.getDescription().isBlank()) {
                            sb.append("（").append(asset.getDescription().trim()).append("）");
                        }
                        sb.append("；");
                    });
        }
        return sb.toString();
    }

    private static String formatGlobalCharacterClues(List<DramaForgeAsset> assets) {
        var sb = new StringBuilder();
        for (var asset : assets) {
            if (asset.getType() != DramaForgeAssetType.CHARACTER) {
                continue;
            }
            if (asset.getReferenceImageUrl() == null || asset.getReferenceImageUrl().isBlank()) {
                continue;
            }
            if (sb.isEmpty()) {
                sb.append("【角色定妆】");
            }
            sb.append(asset.getName());
            if (asset.getDescription() != null && !asset.getDescription().isBlank()) {
                sb.append("（").append(asset.getDescription().trim()).append("）");
            }
            sb.append("；");
        }
        return sb.toString();
    }

    private static String formatSceneClue(DramaForgeShot shot, List<DramaForgeAsset> assets) {
        if (shot.getSceneRef() != null && !shot.getSceneRef().isBlank()) {
            for (var asset : assets) {
                if (asset.getType() != DramaForgeAssetType.SCENE) {
                    continue;
                }
                if (asset.getName().equalsIgnoreCase(shot.getSceneRef())) {
                    var detail = asset.getDescription() != null ? asset.getDescription() : "";
                    var hasRef = asset.getReferenceImageUrl() != null && !asset.getReferenceImageUrl().isBlank();
                    var sb = new StringBuilder("【场景线索】").append(asset.getName());
                    if (hasRef) {
                        sb.append("=@").append(asset.getReferenceImageUrl().trim());
                    }
                    if (!detail.isBlank()) {
                        sb.append("（").append(detail).append("）");
                    }
                    sb.append(hasRef ? "" : "[待生成场景参考图]").append("。");
                    return sb.toString();
                }
            }
        }
        var description = shot.getDescription() != null ? shot.getDescription().toLowerCase(Locale.ROOT) : "";
        for (var asset : assets) {
            if (asset.getType() != DramaForgeAssetType.SCENE) {
                continue;
            }
            if (description.contains(asset.getName().toLowerCase(Locale.ROOT))) {
                var detail = asset.getDescription() != null ? asset.getDescription() : "";
                var hasRef = asset.getReferenceImageUrl() != null && !asset.getReferenceImageUrl().isBlank();
                var sb = new StringBuilder("【场景线索】").append(asset.getName());
                if (hasRef) {
                    sb.append("=@").append(asset.getReferenceImageUrl().trim());
                }
                if (!detail.isBlank()) {
                    sb.append("（").append(detail).append("）");
                }
                sb.append("。");
                return sb.toString();
            }
        }
        return "";
    }

    private static String formatPropCluesFromRefs(List<String> refs, List<DramaForgeAsset> assets) {
        if (refs == null || refs.isEmpty()) {
            return "";
        }
        var sb = new StringBuilder("【道具线索】");
        for (var name : refs) {
            assets.stream()
                    .filter(a -> a.getType() == DramaForgeAssetType.PROP)
                    .filter(a -> a.getName().equalsIgnoreCase(name))
                    .findFirst()
                    .ifPresent(asset -> {
                        sb.append(asset.getName());
                        if (asset.getReferenceImageUrl() != null && !asset.getReferenceImageUrl().isBlank()) {
                            sb.append("=@").append(asset.getReferenceImageUrl().trim());
                        }
                        if (asset.getDescription() != null && !asset.getDescription().isBlank()) {
                            sb.append("（").append(asset.getDescription().trim()).append("）");
                        }
                        sb.append("；");
                    });
        }
        return sb.toString();
    }

    private static String formatPropClues(DramaForgeShot shot, List<DramaForgeAsset> assets) {
        var description = shot.getDescription() != null ? shot.getDescription().toLowerCase(Locale.ROOT) : "";
        var sb = new StringBuilder();
        for (var asset : assets) {
            if (asset.getType() != DramaForgeAssetType.PROP) {
                continue;
            }
            if (description.contains(asset.getName().toLowerCase(Locale.ROOT))) {
                if (sb.isEmpty()) {
                    sb.append("【道具线索】");
                }
                sb.append(asset.getName());
                if (asset.getDescription() != null && !asset.getDescription().isBlank()) {
                    sb.append("（").append(asset.getDescription().trim()).append("）");
                }
                sb.append("；");
            }
        }
        return sb.toString();
    }

    private static String join(String... parts) {
        var sb = new StringBuilder();
        for (var part : parts) {
            if (part != null && !part.isBlank()) {
                sb.append(part.trim());
                if (!part.endsWith("。") && !part.endsWith("】")) {
                    sb.append("。");
                }
            }
        }
        return sb.toString();
    }

}
