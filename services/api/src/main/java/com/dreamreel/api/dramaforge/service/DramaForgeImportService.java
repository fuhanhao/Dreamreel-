package com.dreamreel.api.dramaforge.service;

import com.dreamreel.api.dramaforge.domain.DramaForgeAsset;
import com.dreamreel.api.dramaforge.domain.DramaForgeAssetType;
import com.dreamreel.api.dramaforge.domain.DramaForgeConfig;
import com.dreamreel.api.dramaforge.domain.DramaForgeEpisode;
import com.dreamreel.api.dramaforge.repository.DramaForgeAssetRepository;
import com.dreamreel.api.dramaforge.repository.DramaForgeConfigRepository;
import com.dreamreel.api.dramaforge.repository.DramaForgeEpisodeRepository;
import com.dreamreel.api.config.TokenFreeProperties;
import com.dreamreel.api.domain.GenerationStatus;
import com.dreamreel.api.dramaforge.dto.DramaForgeDtos.PlanEpisodeOutline;
import com.dreamreel.api.dramaforge.dto.DramaForgeDtos.PlanEpisodesResponse;
import com.dreamreel.api.dto.CreateTextGenerationRequest;
import com.dreamreel.api.dto.TextGenerationResponse;
import com.dreamreel.api.service.TextGenerationService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class DramaForgeImportService {

    private static final int EXTRACT_MAX_CHARS = 30_000;
    private static final int SCRIPT_MAX_CHARS = 30_000;
    private static final List<String> FALLBACK_TEXT_MODELS = List.of("qwen-plus", "deepseek-chat");

    private static final String EXTRACT_PROMPT = """
            你是影视改编策划。请对以下文学文本做专业分析，提取角色、场景、道具，输出严格 JSON：
            {
              "characters":[{"name":"角色名","description":"外观与性格"}],
              "scenes":[{"name":"场景名","description":"环境特征"}],
              "props":[{"name":"道具名","description":"外观与剧情作用"}]
            }
            只输出 JSON，不要解释。
            """;

    private static final String SCRIPT_PROMPT = """
            你是短剧编剧。根据原文生成第1集结构化剧本 JSON：
            {
              "title":"第1集标题",
              "scenes":[{"name":"场景名","shots":[
                {"description":"完整画面提示词：写出镜角色姓名与外观动作、场景环境光影、主景别，约60-120字","dialogue":"对白","camera":"运镜","scene":"场景名","characters":["角色名"],"props":["道具名"]}
              ]}]
            }
            拆镜规则：
            1. 按情节节拍与场景切换拆镜，一镜对应一个可独立生成视频的完整画面
            2. 长集按剧情需要拆分，镜头数量不设上限
            3. 不要把同一段对白或同一动作拆成全景/中景/特写多条；景别切换由视频模型在单镜内完成
            每个镜头还必须：
            1. 标注 scene 与 characters（出镜角色），名称须与资产库一致
            2. description 写足画面信息（谁在做什么、在哪、光线氛围），禁止空话
            只输出 JSON，不要解释。
            """;

    private static final String STRUCTURE_SCRIPT_PROMPT = """
            你是短剧编剧。把用户提供的本集正文（小说片段、大纲或纯文本）整理为结构化剧本 JSON（仅场次与对白，不要拆镜头）：
            {
              "title":"本集标题",
              "scenes":[
                {
                  "name":"场景名（如：内景·车厢·夜）",
                  "description":"本场戏的环境、人物动作与情节节拍（80-200字）",
                  "dialogue":"本场主要对白（可多行，格式：角色：台词）",
                  "characters":["出场角色名"],
                  "location":"地点",
                  "time":"日/夜/晨/昏"
                }
              ]
            }
            规则：
            1. 保留原文角色名、对白与场景，不得擅自替换为无关名人
            2. 按场景切换划分 scenes，一场戏一个 scene；不要输出 shots 数组
            3. 对白写进 dialogue 字段，不要在此步骤拆成镜头
            只输出 JSON，不要解释。
            """;

    private static final String STRUCTURE_SHOTS_PROMPT = """
            你是短剧分镜编剧。根据以下结构化剧本（含 scenes 场次），为每个场次拆分为可独立生视频的镜头 JSON：
            {
              "title":"集标题",
              "scenes":[{"name":"场景名","shots":[
                {"description":"完整画面提示词：写出镜角色姓名与外观动作、场景环境光影、主景别，约60-120字","dialogue":"对白或空字符串","camera":"运镜","scene":"场景名","characters":["角色名"],"props":["道具名"]}
              ]}]
            }
            规则：
            1. 保留剧本中的角色名、对白意图与场景信息
            2. 按情节节拍与场景切换拆镜；一镜一个可独立生成的完整画面
            3. 不要把同一动作拆成多景别多条
            4. description 必须写足画面信息，禁止空话
            只输出 JSON，不要解释。
            """;

    private final DramaForgeAssetRepository assetRepository;
    private final DramaForgeConfigRepository configRepository;
    private final DramaForgeEpisodeRepository episodeRepository;
    private final TextGenerationService textGenerationService;
    private final ObjectMapper objectMapper;
    private final TokenFreeProperties tokenFreeProperties;

    public DramaForgeImportService(
            DramaForgeAssetRepository assetRepository,
            DramaForgeConfigRepository configRepository,
            DramaForgeEpisodeRepository episodeRepository,
            TextGenerationService textGenerationService,
            ObjectMapper objectMapper,
            TokenFreeProperties tokenFreeProperties) {
        this.assetRepository = assetRepository;
        this.configRepository = configRepository;
        this.episodeRepository = episodeRepository;
        this.textGenerationService = textGenerationService;
        this.objectMapper = objectMapper;
        this.tokenFreeProperties = tokenFreeProperties;
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public int extractAssets(UUID projectId, String apiKey) {
        var config = configRepository.findByProjectId(projectId)
                .orElseThrow(() -> new IllegalStateException("DramaForge 配置不存在"));
        var episodes = episodeRepository.findByProjectIdOrderByEpisodeNumberAsc(projectId);
        var fromScripts = episodes.stream()
                .filter(ep -> ep.getScriptJson() != null && !ep.getScriptJson().isBlank())
                .map(ep -> "【第" + ep.getEpisodeNumber() + "集 " + ep.getTitle() + "】\n" + ep.getScriptJson())
                .reduce((a, b) -> a + "\n\n" + b)
                .orElse("");
        // 优先从已解析的分集剧本提取（定剧本 → 建资产）；否则回退项目原文
        var sourceText = !fromScripts.isBlank()
                ? fromScripts
                : requireConfigWithSource(projectId).getSourceText();
        var result = callTextWithFallback(
                projectId,
                apiKey,
                config,
                EXTRACT_PROMPT,
                EXTRACT_MAX_CHARS,
                "资产提取失败",
                sourceText);
        try {
            var root = objectMapper.readTree(cleanJson(result.outputText()));
            int count = 0;
            count += importAssets(projectId, root.get("characters"), DramaForgeAssetType.CHARACTER);
            count += importAssets(projectId, root.get("scenes"), DramaForgeAssetType.SCENE);
            count += importAssets(projectId, root.get("props"), DramaForgeAssetType.PROP);
            return count;
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("资产提取失败: " + ex.getMessage());
        }
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public DramaForgeEpisode generateEpisodeScript(UUID projectId, String apiKey) {
        var config = requireConfigWithSource(projectId);
        var result = callTextWithFallback(
                projectId,
                apiKey,
                config,
                SCRIPT_PROMPT,
                SCRIPT_MAX_CHARS,
                "剧本生成失败");
        var json = cleanJson(result.outputText());
        var episode = new DramaForgeEpisode();
        episode.setProjectId(projectId);
        episode.setEpisodeNumber(episodeRepository.findTopByProjectIdOrderByEpisodeNumberDesc(projectId)
                .map(e -> e.getEpisodeNumber() + 1).orElse(1));
        try {
            var root = objectMapper.readTree(json);
            episode.setTitle(root.path("title").asText("第 " + episode.getEpisodeNumber() + " 集"));
        } catch (Exception ex) {
            episode.setTitle("第 " + episode.getEpisodeNumber() + " 集");
        }
        episode.setScriptJson(json);
        episode = episodeRepository.save(episode);
        if (config.getProjectSummary() == null || config.getProjectSummary().isBlank()) {
            try {
                var root = objectMapper.readTree(json);
                config.setProjectSummary(buildScriptSummary(root, episode.getTitle()));
            } catch (Exception ignored) {
                config.setProjectSummary(episode.getTitle());
            }
            configRepository.save(config);
        }
        return episode;
    }

    private static final String PLAN_EPISODES_PROMPT = """
            你是短剧策划。根据原文规划分集大纲，输出严格 JSON：
            {
              "episodes":[
                {"episodeNumber":1,"title":"第1集标题","summary":"本集剧情摘要（80-150字）"}
              ]
            }
            规则：按剧情自然断点分集，每集摘要须可独立成篇；集数由内容决定（通常 1-12 集）。
            只输出 JSON，不要解释。
            """;

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public PlanEpisodesResponse planEpisodes(UUID projectId, String apiKey) {
        var config = requireConfigWithSource(projectId);
        var result = callTextWithFallback(
                projectId,
                apiKey,
                config,
                PLAN_EPISODES_PROMPT,
                SCRIPT_MAX_CHARS,
                "分集规划失败");
        try {
            var root = objectMapper.readTree(cleanJson(result.outputText()));
            var episodesNode = root.get("episodes");
            if (episodesNode == null || !episodesNode.isArray() || episodesNode.isEmpty()) {
                throw new IllegalStateException("模型未返回分集大纲");
            }
            var outlines = new ArrayList<PlanEpisodeOutline>();
            for (var node : episodesNode) {
                outlines.add(new PlanEpisodeOutline(
                        node.path("episodeNumber").asInt(outlines.size() + 1),
                        node.path("title").asText("第 " + (outlines.size() + 1) + " 集"),
                        node.path("summary").asText("")));
            }
            return new PlanEpisodesResponse(outlines.size(), outlines);
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("分集规划失败: " + ex.getMessage());
        }
    }

    /** 规划分集并创建剧集记录（正文初始为 summary，后续可编辑再解析为剧本） */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public PlanEpisodesResponse planEpisodesAndApply(UUID projectId, String apiKey) {
        var plan = planEpisodes(projectId, apiKey);
        var existing = episodeRepository.findByProjectIdOrderByEpisodeNumberAsc(projectId);
        var existingNumbers = existing.stream()
                .map(DramaForgeEpisode::getEpisodeNumber)
                .collect(java.util.stream.Collectors.toSet());

        for (var outline : plan.episodes()) {
            if (existingNumbers.contains(outline.episodeNumber())) {
                continue;
            }
            var episode = new DramaForgeEpisode();
            episode.setProjectId(projectId);
            episode.setEpisodeNumber(outline.episodeNumber());
            episode.setTitle(outline.title());
            var body = outline.summary();
            if (body != null && !body.isBlank()) {
                episode.setScriptJson(body.trim());
            }
            episodeRepository.save(episode);
            existingNumbers.add(outline.episodeNumber());
        }
        return plan;
    }

    /**
     * Step ②a：本集正文 → 结构化剧本（scenes，不含 shots）。
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public String structureEpisodeScriptFromBody(UUID projectId, UUID episodeId, String draft, String apiKey) {
        if (draft == null || draft.isBlank()) {
            throw new IllegalStateException("本集正文为空，请先粘贴或编辑内容");
        }
        var config = configRepository.findByProjectId(projectId)
                .orElseThrow(() -> new IllegalStateException("DramaForge 配置不存在"));

        JsonNode existingRoot = null;
        try {
            existingRoot = objectMapper.readTree(draft.trim());
        } catch (Exception ignored) {
        }
        if (existingRoot != null && hasScriptStructure(existingRoot) && !hasShotStructure(existingRoot)) {
            saveEpisodeScript(projectId, episodeId, draft.trim());
            return draft.trim();
        }

        var primaryModel = resolveTextModel(config);
        var excerpt = draft.length() > SCRIPT_MAX_CHARS ? draft.substring(0, SCRIPT_MAX_CHARS) : draft;
        var result = invokeTextWithEpisodeContext(
                projectId, episodeId, apiKey, primaryModel, STRUCTURE_SCRIPT_PROMPT, excerpt);
        if (!isUsable(result)) {
            for (var fallbackModel : FALLBACK_TEXT_MODELS) {
                if (fallbackModel.equals(primaryModel)) {
                    continue;
                }
                result = invokeTextWithEpisodeContext(
                        projectId, episodeId, apiKey, fallbackModel, STRUCTURE_SCRIPT_PROMPT, excerpt);
                if (isUsable(result)) {
                    break;
                }
            }
        }
        requireTextResult(result, "解析为剧本");
        var json = cleanJson(result.outputText());
        try {
            var root = objectMapper.readTree(json);
            if (!hasScriptStructure(root)) {
                throw new IllegalStateException("模型未返回可用的剧本结构（需含 scenes）");
            }
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("剧本结构化失败: " + ex.getMessage());
        }
        saveEpisodeScript(projectId, episodeId, json);
        return json;
    }

    /**
     * Step ②b：结构化剧本 → 带 shots 的分镜剧本 JSON。
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public String structureEpisodeShotsFromScript(UUID projectId, UUID episodeId, String apiKey) {
        var episode = episodeRepository.findByIdAndProjectId(episodeId, projectId)
                .orElseThrow(() -> new IllegalStateException("剧集不存在"));
        var script = episode.getScriptJson();
        if (script == null || script.isBlank()) {
            throw new IllegalStateException("请先保存本集剧本或正文");
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(script);
        } catch (Exception ex) {
            throw new IllegalStateException("请先点击「正文→剧本」生成分场结构");
        }
        if (!hasScriptStructure(root)) {
            throw new IllegalStateException("剧本缺少 scenes 结构，请先点击「正文→剧本」");
        }
        if (hasShotStructure(root)) {
            return script;
        }

        var config = configRepository.findByProjectId(projectId)
                .orElseThrow(() -> new IllegalStateException("DramaForge 配置不存在"));
        var primaryModel = resolveTextModel(config);
        var userContent = "结构化剧本：\n" + script;
        var result = invokeTextWithEpisodeContext(
                projectId, episodeId, apiKey, primaryModel, STRUCTURE_SHOTS_PROMPT, userContent);
        if (!isUsable(result)) {
            for (var fallbackModel : FALLBACK_TEXT_MODELS) {
                if (fallbackModel.equals(primaryModel)) {
                    continue;
                }
                result = invokeTextWithEpisodeContext(
                        projectId, episodeId, apiKey, fallbackModel, STRUCTURE_SHOTS_PROMPT, userContent);
                if (isUsable(result)) {
                    break;
                }
            }
        }
        requireTextResult(result, "解析为镜头结构");
        var json = cleanJson(result.outputText());
        try {
            var outRoot = objectMapper.readTree(json);
            if (!hasShotStructure(outRoot)) {
                throw new IllegalStateException("模型未返回可用的镜头结构");
            }
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("镜头结构化失败: " + ex.getMessage());
        }
        saveEpisodeScript(projectId, episodeId, json);
        return json;
    }

    /** @deprecated 兼容旧调用，等同 structureEpisodeShotsFromScript */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public String structureEpisodeScript(UUID projectId, UUID episodeId, String draft, String apiKey) {
        structureEpisodeScriptFromBody(projectId, episodeId, draft, apiKey);
        return structureEpisodeShotsFromScript(projectId, episodeId, apiKey);
    }

    private void saveEpisodeScript(UUID projectId, UUID episodeId, String json) {
        var episode = episodeRepository.findByIdAndProjectId(episodeId, projectId)
                .orElseThrow(() -> new IllegalStateException("剧集不存在"));
        episode.setScriptJson(json);
        try {
            var root = objectMapper.readTree(json);
            var title = root.path("title").asText("").trim();
            if (!title.isBlank()) {
                episode.setTitle(title);
            }
        } catch (Exception ignored) {
        }
        episodeRepository.save(episode);
    }

    private TextGenerationResponse invokeTextWithEpisodeContext(
            UUID projectId,
            UUID episodeId,
            String apiKey,
            String model,
            String prompt,
            String excerpt) {
        var episode = episodeRepository.findByIdAndProjectId(episodeId, projectId).orElse(null);
        var prefix = episode != null
                ? "第" + episode.getEpisodeNumber() + "集《" + episode.getTitle() + "》\n"
                : "";
        return invokeText(projectId, apiKey, model, prompt, prefix + excerpt);
    }

    public static boolean hasScriptStructure(JsonNode root) {
        if (root == null || root.isNull()) {
            return false;
        }
        return root.has("scenes") && root.get("scenes").isArray() && !root.get("scenes").isEmpty();
    }

    public static boolean hasShotStructure(JsonNode root) {
        if (root == null || root.isNull()) {
            return false;
        }
        if (root.has("shots") && root.get("shots").isArray() && !root.get("shots").isEmpty()) {
            return true;
        }
        if (root.has("scenes") && root.get("scenes").isArray()) {
            for (var scene : root.get("scenes")) {
                if (scene.has("shots") && scene.get("shots").isArray() && !scene.get("shots").isEmpty()) {
                    return true;
                }
            }
        }
        return false;
    }

    private String buildScriptSummary(JsonNode root, String title) {
        var firstShot = root.path("scenes").path(0).path("shots").path(0).path("description").asText("");
        if (!firstShot.isBlank()) {
            return title + "：" + firstShot;
        }
        return title;
    }

    private TextGenerationResponse callTextWithFallback(
            UUID projectId,
            String apiKey,
            DramaForgeConfig config,
            String systemUserPrompt,
            int maxChars,
            String action) {
        return callTextWithFallback(
                projectId, apiKey, config, systemUserPrompt, maxChars, action, config.getSourceText());
    }

    private TextGenerationResponse callTextWithFallback(
            UUID projectId,
            String apiKey,
            DramaForgeConfig config,
            String systemUserPrompt,
            int maxChars,
            String action,
            String sourceText) {
        var primaryModel = resolveTextModel(config);
        var excerpt = DramaForgeSourcePreparer.prepare(sourceText, maxChars);
        var result = invokeText(projectId, apiKey, primaryModel, systemUserPrompt, excerpt);
        if (isUsable(result)) {
            return result;
        }
        if (DramaForgeSourcePreparer.isModerationError(result.errorMessage())) {
            // 更短、跳过更多前言后重试
            var retryExcerpt = DramaForgeSourcePreparer.prepare(sourceText, maxChars / 2, 1_500);
            for (var fallbackModel : FALLBACK_TEXT_MODELS) {
                if (fallbackModel.equals(primaryModel)) {
                    continue;
                }
                result = invokeText(projectId, apiKey, fallbackModel, systemUserPrompt, retryExcerpt);
                if (isUsable(result)) {
                    return result;
                }
            }
            throw new IllegalStateException(DramaForgeSourcePreparer.moderationHint(action));
        }
        requireTextResult(result, action);
        return result;
    }

    private TextGenerationResponse invokeText(
            UUID projectId,
            String apiKey,
            String model,
            String prompt,
            String excerpt) {
        return textGenerationService.createForProject(projectId,
                new CreateTextGenerationRequest(
                        projectId,
                        null,
                        model,
                        prompt,
                        "text",
                        "原文：\n" + excerpt),
                apiKey);
    }

    private boolean isUsable(TextGenerationResponse result) {
        return result.status() != GenerationStatus.FAILED
                && result.outputText() != null
                && !result.outputText().isBlank();
    }

    private void requireTextResult(TextGenerationResponse result, String action) {
        if (isUsable(result)) {
            return;
        }
        var detail = result.errorMessage();
        if (DramaForgeSourcePreparer.isModerationError(detail)) {
            throw new IllegalStateException(DramaForgeSourcePreparer.moderationHint(action));
        }
        if (detail != null && !detail.isBlank()) {
            throw new IllegalStateException(action + "：" + detail);
        }
        throw new IllegalStateException(action + "：模型未返回内容");
    }

    private int importAssets(UUID projectId, JsonNode array, DramaForgeAssetType type) {
        if (array == null || !array.isArray()) {
            return 0;
        }
        int count = 0;
        for (var node : array) {
            var name = node.path("name").asText("").trim();
            if (name.isBlank()) {
                continue;
            }
            boolean exists = assetRepository.findByProjectIdOrderBySortOrderAscNameAsc(projectId).stream()
                    .anyMatch(asset -> asset.getName().equalsIgnoreCase(name));
            if (exists) {
                continue;
            }
            var asset = new DramaForgeAsset();
            asset.setProjectId(projectId);
            asset.setType(type);
            asset.setName(name);
            asset.setDescription(node.path("description").asText(null));
            asset.setSortOrder(count);
            assetRepository.save(asset);
            count++;
        }
        return count;
    }

    private DramaForgeConfig requireConfigWithSource(UUID projectId) {
        var config = configRepository.findByProjectId(projectId)
                .orElseThrow(() -> new IllegalStateException("DramaForge 配置不存在"));
        var sourceText = config.getSourceText();
        if (sourceText == null || sourceText.isBlank()) {
            throw new IllegalStateException("请先导入小说/剧本文本");
        }
        return config;
    }

    private String resolveTextModel(DramaForgeConfig config) {
        if (config.getTextBackend() != null && config.getTextBackend().contains("/")) {
            return config.getTextBackend().substring(config.getTextBackend().indexOf('/') + 1);
        }
        var configured = tokenFreeProperties.defaultChatModel();
        return configured != null && !configured.isBlank() ? configured : "qwen-max";
    }

    private String cleanJson(String text) {
        var trimmed = text.trim();
        if (trimmed.startsWith("```")) {
            var end = trimmed.lastIndexOf("```");
            if (end > 3) {
                trimmed = trimmed.substring(trimmed.indexOf('\n') + 1, end).trim();
            }
        }
        return trimmed;
    }
}
