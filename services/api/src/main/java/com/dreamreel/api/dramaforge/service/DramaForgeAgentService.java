package com.dreamreel.api.dramaforge.service;

import com.dreamreel.api.dramaforge.domain.DramaForgeJobType;
import com.dreamreel.api.dramaforge.dto.DramaForgeDtos.*;
import com.dreamreel.api.dramaforge.repository.DramaForgeEpisodeRepository;
import com.dreamreel.api.client.TokenFreeClient;
import com.dreamreel.api.client.TokenFreeClient.ChatMessage;
import com.dreamreel.api.config.TokenFreeProperties;
import com.dreamreel.api.service.ProjectApiKeyResolver;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Transactional
public class DramaForgeAgentService {

    private static final Pattern EPISODE_NUMBER = Pattern.compile("第\\s*(\\d+)\\s*集");

    private static final String SYSTEM_PROMPT = """
            你是 DramaForge 短剧制作智能体，帮助用户通过对话推进项目。
            六步流程（TagoMovie SOP）：①写故事 → ②定剧本 → ③建资产 → ④画分镜 → ⑤出成片 → ⑥AI剪辑导出。
            默认视频路径：分镜图 i2v（storyboard_to_video）；高级模式可用设计图直出（reference_to_video）。
            镜头粒度：按情节节拍拆镜，一镜一视频；不要把同一段对白拆成多条镜头。

            你可以执行以下工具（在 actions 数组中返回，可同时执行多个）：
            - extract_assets：从原文提取角色/场景/道具
            - generate_script：AI 生成第 1 集剧本 JSON
            - generate_asset_designs：批量生成资产设计图
            - parse_shots：从剧本解析镜头（需 episodeNumber）
            - generate_storyboards：批量生成分镜图（需 episodeNumber）
            - generate_videos：生成镜头视频（需 episodeNumber；设计图直出，无需确认分镜）
            - compose：合成成片（需 episodeNumber）
            - run_workflow：一键推进流水线
            - update_summary / update_worldview / update_style_prompt

            必须严格输出 JSON，不要 markdown 代码块：
            {"reply":"给用户的中文回复","actions":[{"tool":"工具名","episodeNumber":1,"text":"可选文本"}]}

            若用户只是咨询进度、问问题、不需要执行操作，actions 返回空数组。
            """;

    private final DramaForgeService dramaForgeService;
    private final DramaForgeWorkflowService workflowService;
    private final DramaForgeEnqueueService enqueueService;
    private final DramaForgeEpisodeRepository episodeRepository;
    private final TokenFreeClient tokenFreeClient;
    private final TokenFreeProperties tokenFreeProperties;
    private final ProjectApiKeyResolver projectApiKeyResolver;
    private final ObjectMapper objectMapper;

    public DramaForgeAgentService(
            DramaForgeService dramaForgeService,
            DramaForgeWorkflowService workflowService,
            DramaForgeEnqueueService enqueueService,
            DramaForgeEpisodeRepository episodeRepository,
            TokenFreeClient tokenFreeClient,
            TokenFreeProperties tokenFreeProperties,
            ProjectApiKeyResolver projectApiKeyResolver,
            ObjectMapper objectMapper) {
        this.dramaForgeService = dramaForgeService;
        this.workflowService = workflowService;
        this.enqueueService = enqueueService;
        this.episodeRepository = episodeRepository;
        this.tokenFreeClient = tokenFreeClient;
        this.tokenFreeProperties = tokenFreeProperties;
        this.projectApiKeyResolver = projectApiKeyResolver;
        this.objectMapper = objectMapper;
    }

    public AgentChatResponse chat(UUID projectId, AgentChatRequest request, String headerApiKey) {
        var apiKey = projectApiKeyResolver.resolve(projectId, headerApiKey);
        var overview = dramaForgeService.getOverview(projectId);
        var config = dramaForgeService.getConfig(projectId);
        var episodes = episodeRepository.findByProjectIdOrderByEpisodeNumberAsc(projectId);

        var quick = tryQuickAction(projectId, request, apiKey, episodes);
        if (quick != null) {
            return quick;
        }

        var context = buildContext(overview, config, episodes, request.selectedEpisodeId());
        var messages = new ArrayList<ChatMessage>();
        messages.add(new ChatMessage("system", SYSTEM_PROMPT + "\n\n当前项目上下文：\n" + context));
        if (request.history() != null) {
            for (var item : request.history()) {
                if (item.role() == null || item.content() == null || item.content().isBlank()) continue;
                var role = "assistant".equalsIgnoreCase(item.role()) ? "assistant" : "user";
                messages.add(new ChatMessage(role, item.content().trim()));
            }
        }
        messages.add(new ChatMessage("user", request.message().trim()));

        var model = tokenFreeProperties.defaultChatModel();
        var result = tokenFreeClient.createChatCompletion(apiKey, model, messages);
        if (result.outputText() == null || result.outputText().isBlank()) {
            throw new IllegalStateException("模型无返回: " + (result.errorMessage() != null ? result.errorMessage() : "unknown"));
        }

        return parseAndExecute(projectId, apiKey, result.outputText(), episodes, request.selectedEpisodeId());
    }

    private AgentChatResponse tryQuickAction(
            UUID projectId,
            AgentChatRequest request,
            String apiKey,
            List<com.dreamreel.api.dramaforge.domain.DramaForgeEpisode> episodes) {
        var text = request.message().trim().toLowerCase(Locale.ROOT);
        var episodeId = resolveEpisodeId(episodes, request.selectedEpisodeId(), extractEpisodeNumber(request.message()));

        if (containsAny(text, "提取资产", "提取角色", "分析资产", "extract asset")) {
            var job = enqueueService.enqueue(projectId, DramaForgeJobType.EXTRACT_ASSETS, null, apiKey);
            return new AgentChatResponse(
                    "已提交资产提取任务，请在右侧任务队列查看进度。",
                    List.of(new AgentActionDto("extract_assets", "queued", job.id().toString()))
            );
        }
        if (containsAny(text, "生成剧本", "写剧本", "generate script")) {
            var job = enqueueService.enqueue(projectId, DramaForgeJobType.GENERATE_SCRIPT, null, apiKey);
            return new AgentChatResponse(
                    "已提交剧本生成任务。",
                    List.of(new AgentActionDto("generate_script", "queued", job.id().toString()))
            );
        }
        if (containsAny(text, "设计图", "资产图", "generate design")) {
            var job = enqueueService.enqueue(projectId, DramaForgeJobType.ASSET_DESIGN, null, apiKey);
            return new AgentChatResponse(
                    "已提交批量生成资产设计图任务。",
                    List.of(new AgentActionDto("generate_asset_designs", "queued", job.id().toString()))
            );
        }
        if (containsAny(text, "解析镜头", "解析分镜", "parse shot")) {
            if (episodeId == null) {
                return new AgentChatResponse("请先创建并选择剧集，或说明「第几集」。", List.of());
            }
            dramaForgeService.parseShotsFromScript(projectId, episodeId);
            return new AgentChatResponse(
                    "已从剧本重新解析镜头（已清空旧镜头）。",
                    List.of(new AgentActionDto("parse_shots", "completed", episodeId.toString()))
            );
        }
        if (containsAny(text, "分镜图", "生成分镜", "storyboard")) {
            if (episodeId == null) {
                return new AgentChatResponse("请先选择要生成分镜的剧集。", List.of());
            }
            var job = enqueueService.enqueue(projectId, DramaForgeJobType.STORYBOARD, episodeId, apiKey);
            return new AgentChatResponse(
                    "已提交分镜图生成任务。",
                    List.of(new AgentActionDto("generate_storyboards", "queued", job.id().toString()))
            );
        }
        if (containsAny(text, "生成视频", "镜头视频", "图生视频", "generate video")) {
            if (episodeId == null) {
                return new AgentChatResponse("请先选择要生成视频的剧集。", List.of());
            }
            var job = enqueueService.enqueue(projectId, DramaForgeJobType.VIDEO, episodeId, apiKey);
            enqueueService.enqueue(projectId, DramaForgeJobType.SYNC_VIDEOS, episodeId, apiKey);
            return new AgentChatResponse(
                    "已提交镜头视频任务（设计图直出视频）。",
                    List.of(new AgentActionDto("generate_videos", "queued", job.id().toString()))
            );
        }
        if (containsAny(text, "合成", "成片", "compose")) {
            if (episodeId == null) {
                return new AgentChatResponse("请先选择要合成的剧集。", List.of());
            }
            var job = enqueueService.enqueue(projectId, DramaForgeJobType.COMPOSE, episodeId, null);
            return new AgentChatResponse(
                    "已提交合成成片任务。",
                    List.of(new AgentActionDto("compose", "queued", job.id().toString()))
            );
        }
        if (containsAny(text, "推进", "流水线", "下一步", "workflow")) {
            workflowService.runPipeline(projectId, apiKey);
            return new AgentChatResponse(
                    "已按当前阶段推进流水线。",
                    List.of(new AgentActionDto("run_workflow", "completed", projectId.toString()))
            );
        }
        return null;
    }

    private AgentChatResponse parseAndExecute(
            UUID projectId,
            String apiKey,
            String raw,
            List<com.dreamreel.api.dramaforge.domain.DramaForgeEpisode> episodes,
            UUID selectedEpisodeId) {
        try {
            var json = extractJson(raw);
            var node = objectMapper.readTree(json);
            var reply = node.path("reply").asText("好的，已处理你的请求。");
            var actions = new ArrayList<AgentActionDto>();
            var actionNodes = node.path("actions");
            if (actionNodes.isArray()) {
                for (var actionNode : actionNodes) {
                    actions.add(executeAction(projectId, apiKey, actionNode, episodes, selectedEpisodeId));
                }
            }
            return new AgentChatResponse(reply, actions);
        } catch (Exception e) {
            return new AgentChatResponse(raw.trim(), List.of());
        }
    }

    private AgentActionDto executeAction(
            UUID projectId,
            String apiKey,
            JsonNode actionNode,
            List<com.dreamreel.api.dramaforge.domain.DramaForgeEpisode> episodes,
            UUID selectedEpisodeId) {
        var tool = actionNode.path("tool").asText("").toLowerCase(Locale.ROOT);
        var episodeNumber = actionNode.has("episodeNumber") ? actionNode.get("episodeNumber").asInt(0) : 0;
        var text = actionNode.path("text").asText(null);
        var episodeId = resolveEpisodeId(episodes, selectedEpisodeId, episodeNumber > 0 ? episodeNumber : null);

        try {
            return switch (tool) {
                case "extract_assets" -> {
                    var job = enqueueService.enqueue(projectId, DramaForgeJobType.EXTRACT_ASSETS, null, apiKey);
                    yield new AgentActionDto(tool, "queued", job.id().toString());
                }
                case "generate_script" -> {
                    var job = enqueueService.enqueue(projectId, DramaForgeJobType.GENERATE_SCRIPT, null, apiKey);
                    yield new AgentActionDto(tool, "queued", job.id().toString());
                }
                case "generate_asset_designs" -> {
                    var job = enqueueService.enqueue(projectId, DramaForgeJobType.ASSET_DESIGN, null, apiKey);
                    yield new AgentActionDto(tool, "queued", job.id().toString());
                }
                case "parse_shots" -> {
                    if (episodeId == null) throw new IllegalStateException("未找到目标剧集");
                    dramaForgeService.parseShotsFromScript(projectId, episodeId);
                    yield new AgentActionDto(tool, "completed", episodeId.toString());
                }
                case "generate_storyboards" -> {
                    if (episodeId == null) throw new IllegalStateException("未找到目标剧集");
                    var job = enqueueService.enqueue(projectId, DramaForgeJobType.STORYBOARD, episodeId, apiKey);
                    yield new AgentActionDto(tool, "queued", job.id().toString());
                }
                case "generate_videos" -> {
                    if (episodeId == null) throw new IllegalStateException("未找到目标剧集");
                    var job = enqueueService.enqueue(projectId, DramaForgeJobType.VIDEO, episodeId, apiKey);
                    enqueueService.enqueue(projectId, DramaForgeJobType.SYNC_VIDEOS, episodeId, apiKey);
                    yield new AgentActionDto(tool, "queued", job.id().toString());
                }
                case "compose" -> {
                    if (episodeId == null) throw new IllegalStateException("未找到目标剧集");
                    var job = enqueueService.enqueue(projectId, DramaForgeJobType.COMPOSE, episodeId, null);
                    yield new AgentActionDto(tool, "queued", job.id().toString());
                }
                case "run_workflow" -> {
                    workflowService.runPipeline(projectId, apiKey);
                    yield new AgentActionDto(tool, "completed", projectId.toString());
                }
                case "update_summary" -> {
                    dramaForgeService.updateConfig(projectId, new UpdateConfigRequest(
                            null, null, null, null, null, null, null, null, text, null, null, null, null, null,
                            null, null, null, null, null));
                    yield new AgentActionDto(tool, "completed", "project_summary");
                }
                case "update_worldview" -> {
                    dramaForgeService.updateConfig(projectId, new UpdateConfigRequest(
                            null, null, null, null, null, null, null, null, null, text, null, null, null, null,
                            null, null, null, null, null));
                    yield new AgentActionDto(tool, "completed", "worldview");
                }
                case "update_style_prompt" -> {
                    dramaForgeService.updateConfig(projectId, new UpdateConfigRequest(
                            null, null, null, null, null, null, text, null, null, null, null, null, null, null,
                            null, null, null, null, null));
                    yield new AgentActionDto(tool, "completed", "style_prompt");
                }
                default -> new AgentActionDto(tool.isBlank() ? "unknown" : tool, "skipped", "unsupported");
            };
        } catch (Exception e) {
            return new AgentActionDto(tool, "failed", e.getMessage());
        }
    }

    private String buildContext(
            PipelineOverviewResponse overview,
            ConfigResponse config,
            List<com.dreamreel.api.dramaforge.domain.DramaForgeEpisode> episodes,
            UUID selectedEpisodeId) {
        var sb = new StringBuilder();
        sb.append("阶段: ").append(overview.stage()).append(" (").append(overview.progress()).append("%)\n");
        sb.append("角色/场景/道具: ")
                .append(overview.assetCounts().getOrDefault("character", 0L)).append("/")
                .append(overview.assetCounts().getOrDefault("scene", 0L)).append("/")
                .append(overview.assetCounts().getOrDefault("prop", 0L)).append("\n");
        sb.append("剧集数: ").append(overview.episodeCount())
                .append(", 镜头: ").append(overview.shotCount())
                .append(", 视频完成: ").append(overview.videoDoneCount()).append("\n");
        sb.append("流程说明: 素材配置 → 定剧本 → 定资产 → 出成片 → 合成；出成片无需确认分镜。\n");
        if (config.projectSummary() != null && !config.projectSummary().isBlank()) {
            sb.append("项目概要: ").append(truncate(config.projectSummary(), 500)).append("\n");
        }
        if (config.worldview() != null && !config.worldview().isBlank()) {
            sb.append("世界观: ").append(truncate(config.worldview(), 500)).append("\n");
        }
        sb.append("剧集列表: ");
        for (var ep : episodes) {
            sb.append("E").append(ep.getEpisodeNumber()).append("·").append(ep.getTitle());
            if (ep.getId().equals(selectedEpisodeId)) sb.append("(当前选中)");
            sb.append("; ");
        }
        return sb.toString();
    }

    private UUID resolveEpisodeId(
            List<com.dreamreel.api.dramaforge.domain.DramaForgeEpisode> episodes,
            UUID selectedEpisodeId,
            Integer episodeNumber) {
        if (episodeNumber != null) {
            return episodes.stream()
                    .filter(ep -> ep.getEpisodeNumber() == episodeNumber)
                    .map(com.dreamreel.api.dramaforge.domain.DramaForgeEpisode::getId)
                    .findFirst()
                    .orElse(null);
        }
        if (selectedEpisodeId != null) {
            return episodes.stream()
                    .filter(ep -> ep.getId().equals(selectedEpisodeId))
                    .map(com.dreamreel.api.dramaforge.domain.DramaForgeEpisode::getId)
                    .findFirst()
                    .orElse(selectedEpisodeId);
        }
        return episodes.isEmpty() ? null : episodes.getFirst().getId();
    }

    private Integer extractEpisodeNumber(String message) {
        Matcher matcher = EPISODE_NUMBER.matcher(message);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }
        return null;
    }

    private boolean containsAny(String text, String... keywords) {
        for (var keyword : keywords) {
            if (text.contains(keyword.toLowerCase(Locale.ROOT))) return true;
        }
        return false;
    }

    private String extractJson(String raw) {
        var trimmed = raw.trim();
        if (trimmed.startsWith("```")) {
            var start = trimmed.indexOf('{');
            var end = trimmed.lastIndexOf('}');
            if (start >= 0 && end > start) return trimmed.substring(start, end + 1);
        }
        var start = trimmed.indexOf('{');
        var end = trimmed.lastIndexOf('}');
        if (start >= 0 && end > start) return trimmed.substring(start, end + 1);
        return trimmed;
    }

    private String truncate(String text, int max) {
        if (text.length() <= max) return text;
        return text.substring(0, max) + "…";
    }
}
