package com.dreamreel.api.service;

import com.dreamreel.api.client.TokenFreeClient;
import com.dreamreel.api.config.TokenFreeApiKeyResolver;
import com.dreamreel.api.config.TokenFreeProperties;
import com.dreamreel.api.domain.GenerationJob;
import com.dreamreel.api.domain.GenerationMediaType;
import com.dreamreel.api.domain.GenerationStatus;
import com.dreamreel.api.dto.CreateTextGenerationRequest;
import com.dreamreel.api.dto.TextGenerationResponse;
import com.dreamreel.api.dto.TextModelResponse;
import com.dreamreel.api.exception.ResourceNotFoundException;
import com.dreamreel.api.repository.GenerationJobRepository;
import com.dreamreel.api.security.CurrentUserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@Transactional
public class TextGenerationService {

    private static final String SCRIPT_SYSTEM_PROMPT = """
            你是一位专业的竖屏短剧分镜编剧。根据用户提供的剧本或创意，输出结构化的分镜脚本。
            要求：
            1. 按镜头编号（镜1、镜2…）逐条描述
            2. 每条包含：画面描述、运镜方式、时长建议、对白/旁白（如有）
            3. 语言简洁，适合 AI 生图/生视频使用
            4. 直接输出分镜内容，不要额外解释
            """;

    private static final String TEXT_SYSTEM_PROMPT = """
            你是一位专业的竖屏短剧编剧。根据用户的创意、主题或要求，撰写完整的短剧剧本。
            要求：
            1. 包含故事梗概、主要角色、场景设定
            2. 按场次编写，含对白和动作描述
            3. 适合 1-3 分钟竖屏短剧，节奏紧凑
            4. 直接输出剧本内容，不要额外解释
            """;

    private static final String PROMPT_SYSTEM_PROMPT = """
            你是专业的 AI 视频/图片提示词工程师，服务于竖屏短剧创作。
            规则：
            1. 只输出优化后的提示词正文，不要标题、编号、解释、markdown、JSON
            2. 以中文为主；可在文末另起一段附简短英文版，但不要按字段拆分
            3. 保留用户原文中的角色名、关键动作、情绪、对白意图、运镜与场景细节，不得编造无关名人或改掉核心剧情
            4. 将剧本/分镜改写为可直接用于 AI 生视频的连贯画面描述：人物外观与表情、动作、环境、光影、镜头运动、氛围与画质
            5. 禁止按「主体 / 场景 / 光影 / 镜头 / 风格 / 画质」分条罗列；输出一整段或按镜头分段的连贯描述
            6. 总长约 150～600 字，信息密度高，可直接粘贴到文生视频
            """;

    private final TokenFreeClient tokenFreeClient;
    private final TokenFreeProperties properties;
    private final TokenFreeApiKeyResolver apiKeyResolver;
    private final GenerationJobRepository generationJobRepository;
    private final CurrentUserService currentUserService;
    private final ProjectApiKeyResolver projectApiKeyResolver;

    public TextGenerationService(
            TokenFreeClient tokenFreeClient,
            TokenFreeProperties properties,
            TokenFreeApiKeyResolver apiKeyResolver,
            GenerationJobRepository generationJobRepository,
            CurrentUserService currentUserService,
            ProjectApiKeyResolver projectApiKeyResolver) {
        this.tokenFreeClient = tokenFreeClient;
        this.properties = properties;
        this.apiKeyResolver = apiKeyResolver;
        this.generationJobRepository = generationJobRepository;
        this.currentUserService = currentUserService;
        this.projectApiKeyResolver = projectApiKeyResolver;
    }

    @Transactional(readOnly = true)
    public List<TextModelResponse> listModels(String headerApiKey) {
        var user = currentUserService.requireUserEntity();
        var apiKey = requireApiKey(headerApiKey, user.getTokenfreeApiKey());
        return tokenFreeClient.listChatModels(apiKey).stream()
                .map(model -> new TextModelResponse(model.id(), model.provider()))
                .toList();
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public TextGenerationResponse create(CreateTextGenerationRequest request, String headerApiKey) {
        var user = currentUserService.requireUserEntity();
        return createInternal(user.getId(), request, requireApiKey(headerApiKey, user.getTokenfreeApiKey()));
    }

    /** DramaForge 后台任务：无 HTTP 登录上下文时按项目归属用户解析 API Key */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public TextGenerationResponse createForProject(UUID projectId, CreateTextGenerationRequest request, String headerApiKey) {
        var userId = projectApiKeyResolver.resolveOwnerId(projectId);
        var apiKey = projectApiKeyResolver.resolve(projectId, headerApiKey);
        return createInternal(userId, request, apiKey);
    }

    private TextGenerationResponse createInternal(UUID userId, CreateTextGenerationRequest request, String apiKey) {

        var model = request.model() != null && !request.model().isBlank()
                ? request.model()
                : properties.defaultChatModel();
        var nodeType = request.nodeType();
        var systemPrompt = switch (nodeType) {
            case "script" -> SCRIPT_SYSTEM_PROMPT;
            case "prompt" -> PROMPT_SYSTEM_PROMPT;
            default -> TEXT_SYSTEM_PROMPT;
        };
        var userPrompt = buildUserPrompt(request, nodeType);

        var result = tokenFreeClient.createChatCompletion(apiKey, new TokenFreeClient.CreateChatPayload(
                model,
                systemPrompt,
                userPrompt
        ));

        var job = new GenerationJob();
        job.setUserId(userId);
        job.setProjectId(request.projectId());
        job.setNodeId(request.nodeId());
        job.setMediaType(GenerationMediaType.TEXT);
        job.setProviderTaskId("sync-" + UUID.randomUUID());
        job.setModel(model);
        job.setPrompt(summarizePrompt(request.prompt()));
        job.setStatus(mapStatus(result.status()));
        job.setProgress(result.status().equals("completed") ? 100 : null);
        job.setOutputText(result.outputText());
        job.setErrorMessage(truncateError(result.errorMessage()));

        return TextGenerationResponse.from(generationJobRepository.save(job), nodeType);
    }

    @Transactional(readOnly = true)
    public TextGenerationResponse get(UUID id) {
        var job = generationJobRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("生成任务不存在: " + id));
        return TextGenerationResponse.from(job, "text");
    }

    private String buildUserPrompt(CreateTextGenerationRequest request, String nodeType) {
        var sb = new StringBuilder();
        if (request.context() != null && !request.context().isBlank()) {
            sb.append("【上游参考内容】\n").append(request.context().trim()).append("\n\n");
        }
        return switch (nodeType) {
            case "script" -> {
                sb.append("【分镜需求】\n").append(request.prompt().trim());
                if (request.context() == null || request.context().isBlank()) {
                    sb.append("\n\n请根据以上需求生成分镜脚本。");
                } else {
                    sb.append("\n\n请根据上游剧本内容，拆解为详细分镜脚本。");
                }
                yield sb.toString();
            }
            case "prompt" -> {
                sb.append("【创作描述】\n").append(request.prompt().trim());
                sb.append("\n\n请将以上内容优化为可直接用于 AI 生图/生视频的连贯提示词。");
                sb.append("保留角色、动作、对白意图与运镜；不要拆成主体/场景等条目列表。");
                yield sb.toString();
            }
            default -> {
                sb.append("【创作需求】\n").append(request.prompt().trim());
                if (request.context() != null && !request.context().isBlank()) {
                    sb.append("\n\n").append(request.context().trim());
                }
                yield sb.toString();
            }
        };
    }

    private String summarizePrompt(String prompt) {
        if (prompt == null) {
            return "";
        }
        var trimmed = prompt.trim();
        if (trimmed.length() <= 500) {
            return trimmed;
        }
        return trimmed.substring(0, 500) + "…（共 " + trimmed.length() + " 字，已截断存档）";
    }

    private String truncateError(String message) {
        if (message == null) {
            return null;
        }
        if (message.length() <= 2000) {
            return message;
        }
        return message.substring(0, 2000) + "…";
    }

    private String requireApiKey(String headerApiKey, String userApiKey) {
        var apiKey = apiKeyResolver.resolve(headerApiKey, userApiKey);
        if (apiKey == null) {
            throw new IllegalStateException("请配置 TokenFree API Key（个人设置或环境变量 TOKENFREE_API_KEY）");
        }
        return apiKey;
    }

    private GenerationStatus mapStatus(String providerStatus) {
        if (providerStatus == null) {
            return GenerationStatus.QUEUED;
        }
        return switch (providerStatus.toLowerCase(Locale.ROOT)) {
            case "completed", "succeeded", "success" -> GenerationStatus.COMPLETED;
            case "failed", "error", "cancelled" -> GenerationStatus.FAILED;
            default -> GenerationStatus.QUEUED;
        };
    }
}
