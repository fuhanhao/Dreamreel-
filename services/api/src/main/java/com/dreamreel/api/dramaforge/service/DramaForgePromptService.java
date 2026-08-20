package com.dreamreel.api.dramaforge.service;

import com.dreamreel.api.dramaforge.domain.DramaForgeAsset;
import com.dreamreel.api.dramaforge.domain.DramaForgeAssetType;
import com.dreamreel.api.dramaforge.domain.DramaForgeConfig;
import com.dreamreel.api.dramaforge.dto.DramaForgeDtos.AssetResponse;
import com.dreamreel.api.dramaforge.dto.DramaForgeDtos.OptimizePromptRequest;
import com.dreamreel.api.dramaforge.dto.DramaForgeDtos.OptimizePromptResponse;
import com.dreamreel.api.dramaforge.repository.DramaForgeAssetRepository;
import com.dreamreel.api.dramaforge.repository.DramaForgeConfigRepository;
import com.dreamreel.api.dramaforge.repository.DramaForgeEpisodeRepository;
import com.dreamreel.api.dramaforge.repository.DramaForgeShotRepository;
import com.dreamreel.api.client.TokenFreeClient;
import com.dreamreel.api.config.TokenFreeProperties;
import com.dreamreel.api.service.ProjectApiKeyResolver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@Transactional
public class DramaForgePromptService {

    private static final List<String> FALLBACK_TEXT_MODELS = List.of("qwen-plus", "deepseek-chat");

    private static final String STYLE_SYSTEM = """
            你是 DramaForge 短剧视觉总监。根据项目信息与用户草稿，输出一段用于全剧 AI 生图的风格提示词。
            规则：
            1. 只输出最终提示词正文，不要标题、编号、解释、JSON、markdown
            2. 80～300 字，中文为主
            3. drama 模式必须强调真人实拍、电影摄影、写实肤质、自然光影；严禁漫画、动漫、插画、线稿
            4. narration 模式偏写实插画叙事，但仍禁止漫画分格与二次元
            5. ad 模式强调商业广告实拍与高质感
            6. 包含光影、色调、镜头质感、画质等要素
            """;

    private static final String ASSET_DESIGN_SYSTEM = """
            你是 DramaForge 资产定妆设计师。输出用于生成纯净参考设计图的 AI 绘画提示词。
            规则：
            1. 只输出提示词正文，不要解释
            2. 角色：虚构半写实数字设定三视图（正面/侧面/背面），纯色棚拍背景，全身或膝上，清晰五官发型服装，无场景无剧情；若有书包/背包/配饰，三视图必须同款同色同位置，禁止视角间不一致
            3. 必须声明：虚构角色/CG数字设定稿，非真人照片、非实拍人像、非名人肖像（降低平台隐私拦截）
            4. 场景：空镜环境，无人物无动物，代表性空间视角，材质与光影
            5. 道具：单品孤立展示，简洁背景，无人物无手部，外形材质尺寸清晰；书包等随身物若单独成道具须与角色定妆图完全同款
            6. drama 模式用「半写实棚拍设定卡」而非超写实人像摄影；禁止漫画动漫、电影剧照、复杂背景、明星脸描述
            7. 150～500 字，强调「纯净参考图」而非剧情画面
            """;

    private static final String SHOT_SYSTEM = """
            你是 DramaForge 镜头提示词编剧。优化单镜头完整视频提示词，供规划与 Seedance 生视频共用。
            规则：
            1. 输出须包含：【镜头】动作画面；若有角色须含姓名与可辨识外观；【规划场景】；必要时【运镜】
            2. 保留已有【角色线索】【场景线索】【道具线索】段落中的资产信息，可润色但不得删除角色名
            3. drama 为虚构短剧影视单帧，禁止漫画分格、对话框、线稿、真实名人
            4. 总长 200～800 字，信息完整，不要只输出一句空话
            """;

    private final DramaForgeConfigRepository configRepository;
    private final DramaForgeAssetRepository assetRepository;
    private final DramaForgeEpisodeRepository episodeRepository;
    private final DramaForgeShotRepository shotRepository;
    private final TokenFreeClient tokenFreeClient;
    private final TokenFreeProperties tokenFreeProperties;
    private final ProjectApiKeyResolver projectApiKeyResolver;

    public DramaForgePromptService(
            DramaForgeConfigRepository configRepository,
            DramaForgeAssetRepository assetRepository,
            DramaForgeEpisodeRepository episodeRepository,
            DramaForgeShotRepository shotRepository,
            TokenFreeClient tokenFreeClient,
            TokenFreeProperties tokenFreeProperties,
            ProjectApiKeyResolver projectApiKeyResolver) {
        this.configRepository = configRepository;
        this.assetRepository = assetRepository;
        this.episodeRepository = episodeRepository;
        this.shotRepository = shotRepository;
        this.tokenFreeClient = tokenFreeClient;
        this.tokenFreeProperties = tokenFreeProperties;
        this.projectApiKeyResolver = projectApiKeyResolver;
    }

    public OptimizePromptResponse optimize(UUID projectId, OptimizePromptRequest request, String headerApiKey) {
        var config = requireConfig(projectId);
        var apiKey = projectApiKeyResolver.resolve(projectId, headerApiKey);
        var kind = request.kind().trim().toLowerCase(Locale.ROOT);
        var optimized = switch (kind) {
            case "style" -> optimizeStyle(config, request.draft(), apiKey);
            case "asset_design" -> optimizeAssetDesign(projectId, config, request, apiKey);
            case "shot" -> optimizeShot(projectId, config, request, apiKey);
            default -> throw new IllegalArgumentException("不支持的提示词类型: " + request.kind());
        };
        return new OptimizePromptResponse(optimized);
    }

    public List<AssetResponse> optimizeAllAssetDesignPrompts(UUID projectId, String headerApiKey) {
        var config = requireConfig(projectId);
        var apiKey = projectApiKeyResolver.resolve(projectId, headerApiKey);
        var assets = assetRepository.findByProjectIdOrderBySortOrderAscNameAsc(projectId);
        if (assets.isEmpty()) {
            throw new IllegalStateException("暂无资产可优化");
        }
        var results = new ArrayList<AssetResponse>();
        for (var asset : assets) {
            var draft = firstNonBlank(asset.getDesignPrompt(), asset.getDescription());
            var optimized = optimizeAssetDesign(config, asset, draft, apiKey);
            asset.setDesignPrompt(optimized);
            results.add(AssetResponse.from(assetRepository.save(asset)));
        }
        return results;
    }

    private String optimizeStyle(DramaForgeConfig config, String draft, String apiKey) {
        var user = new StringBuilder();
        user.append("【内容模式】").append(config.getContentMode().name().toLowerCase(Locale.ROOT)).append('\n');
        appendIfPresent(user, "项目概要", config.getProjectSummary());
        appendIfPresent(user, "世界观", config.getWorldview());
        appendIfPresent(user, "当前风格草稿", draft);
        if (config.getSourceText() != null && !config.getSourceText().isBlank()) {
            user.append("【原文节选】\n")
                    .append(DramaForgeSourcePreparer.prepare(config.getSourceText(), 1_500))
                    .append('\n');
        }
        user.append("\n请输出优化后的全剧风格提示词。");
        return callText(config, apiKey, STYLE_SYSTEM, user.toString(), 2000, "风格提示词优化失败");
    }

    private String optimizeAssetDesign(UUID projectId, DramaForgeConfig config, OptimizePromptRequest request, String apiKey) {
        if (request.assetId() != null) {
            var asset = assetRepository.findByIdAndProjectId(request.assetId(), projectId)
                    .orElseThrow(() -> new IllegalArgumentException("资产不存在"));
            var draft = firstNonBlank(request.draft(), asset.getDesignPrompt(), asset.getDescription());
            return optimizeAssetDesign(config, asset, draft, apiKey);
        }
        if (request.assetName() == null || request.assetName().isBlank()) {
            throw new IllegalArgumentException("asset_design 需提供 assetId，或 assetName + assetType");
        }
        var type = parseAssetType(request.assetType());
        var draft = firstNonBlank(request.draft(), "");
        var pseudo = new DramaForgeAsset();
        pseudo.setType(type);
        pseudo.setName(request.assetName().trim());
        pseudo.setDescription(firstNonBlank(request.assetDescription(), draft));
        return optimizeAssetDesign(config, pseudo, draft, apiKey);
    }

    private DramaForgeAssetType parseAssetType(String value) {
        if (value == null || value.isBlank()) {
            return DramaForgeAssetType.CHARACTER;
        }
        return DramaForgeAssetType.valueOf(value.trim().toUpperCase(Locale.ROOT));
    }

    private String optimizeAssetDesign(DramaForgeConfig config, DramaForgeAsset asset, String draft, String apiKey) {
        var user = new StringBuilder();
        user.append("【内容模式】").append(config.getContentMode().name().toLowerCase(Locale.ROOT)).append('\n');
        appendIfPresent(user, "全剧风格", config.getStylePrompt());
        user.append("【资产类型】").append(assetTypeLabel(asset.getType())).append('\n');
        user.append("【资产名称】").append(asset.getName()).append('\n');
        appendIfPresent(user, "资产描述", asset.getDescription());
        appendIfPresent(user, "设计提示词草稿", draft);
        user.append("\n请输出优化后的资产设计图提示词。");
        return callText(config, apiKey, ASSET_DESIGN_SYSTEM, user.toString(), 4000, "资产设计提示词优化失败");
    }

    private String optimizeShot(UUID projectId, DramaForgeConfig config, OptimizePromptRequest request, String apiKey) {
        if (request.episodeId() == null || request.shotId() == null) {
            throw new IllegalArgumentException("shot 需提供 episodeId 与 shotId");
        }
        episodeRepository.findByIdAndProjectId(request.episodeId(), projectId)
                .orElseThrow(() -> new IllegalArgumentException("剧集不存在"));
        var shot = shotRepository.findByIdAndEpisodeId(request.shotId(), request.episodeId())
                .orElseThrow(() -> new IllegalArgumentException("镜头不存在"));
        var draft = firstNonBlank(request.draft(), shot.getDescription());
        var user = new StringBuilder();
        user.append("【内容模式】").append(config.getContentMode().name().toLowerCase(Locale.ROOT)).append('\n');
        appendIfPresent(user, "全剧风格", config.getStylePrompt());
        appendIfPresent(user, "画面描述草稿", draft);
        appendIfPresent(user, "对白氛围", shot.getDialogue());
        appendIfPresent(user, "运镜", shot.getCameraNote());
        user.append("\n请输出优化后的单帧分镜画面描述。");
        return callText(config, apiKey, SHOT_SYSTEM, user.toString(), 4000, "分镜描述优化失败");
    }

    private String callText(
            DramaForgeConfig config,
            String apiKey,
            String systemPrompt,
            String userPrompt,
            int maxLen,
            String action) {
        var primaryModel = resolveTextModel(config);
        var result = invoke(apiKey, primaryModel, systemPrompt, userPrompt);
        if (result != null) {
            return trimOutput(result, maxLen);
        }
        for (var fallback : FALLBACK_TEXT_MODELS) {
            if (fallback.equals(primaryModel)) {
                continue;
            }
            result = invoke(apiKey, fallback, systemPrompt, userPrompt);
            if (result != null) {
                return trimOutput(result, maxLen);
            }
        }
        throw new IllegalStateException(action + "：模型未返回内容");
    }

    private String invoke(String apiKey, String model, String systemPrompt, String userPrompt) {
        var completion = tokenFreeClient.createChatCompletion(apiKey,
                new TokenFreeClient.CreateChatPayload(model, systemPrompt, userPrompt));
        if (!"completed".equalsIgnoreCase(completion.status())
                && !"succeeded".equalsIgnoreCase(completion.status())
                && !"success".equalsIgnoreCase(completion.status())) {
            return null;
        }
        var text = completion.outputText();
        return text != null && !text.isBlank() ? text : null;
    }

    private String trimOutput(String text, int maxLen) {
        var cleaned = cleanModelOutput(text);
        if (cleaned.length() <= maxLen) {
            return cleaned;
        }
        return cleaned.substring(0, maxLen);
    }

    private String cleanModelOutput(String text) {
        var trimmed = text.trim();
        if (trimmed.startsWith("```")) {
            var end = trimmed.lastIndexOf("```");
            if (end > 3) {
                trimmed = trimmed.substring(trimmed.indexOf('\n') + 1, end).trim();
            }
        }
        trimmed = trimmed.replaceAll("^(优化后[：:]\\s*)", "");
        trimmed = trimmed.replaceAll("^[\"「『]|[\"」』]$", "");
        return trimmed.trim();
    }

    private DramaForgeConfig requireConfig(UUID projectId) {
        return configRepository.findByProjectId(projectId)
                .orElseThrow(() -> new IllegalStateException("DramaForge 配置不存在"));
    }

    private String resolveTextModel(DramaForgeConfig config) {
        if (config.getTextBackend() != null && config.getTextBackend().contains("/")) {
            return config.getTextBackend().substring(config.getTextBackend().indexOf('/') + 1);
        }
        var configured = tokenFreeProperties.defaultChatModel();
        return configured != null && !configured.isBlank() ? configured : "qwen-max";
    }

    private void appendIfPresent(StringBuilder sb, String label, String value) {
        if (value != null && !value.isBlank()) {
            sb.append('【').append(label).append("】\n").append(value.trim()).append('\n');
        }
    }

    private String firstNonBlank(String... values) {
        for (var value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private String assetTypeLabel(DramaForgeAssetType type) {
        return switch (type) {
            case CHARACTER -> "角色";
            case SCENE -> "场景";
            case PROP -> "道具";
        };
    }
}
