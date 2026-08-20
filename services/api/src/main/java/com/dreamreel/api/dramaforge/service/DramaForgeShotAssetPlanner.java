package com.dreamreel.api.dramaforge.service;

import com.dreamreel.api.dramaforge.domain.DramaForgeAsset;
import com.dreamreel.api.dramaforge.domain.DramaForgeAssetType;
import com.dreamreel.api.dramaforge.domain.DramaForgeConfig;
import com.dreamreel.api.dramaforge.domain.DramaForgeShot;
import com.dreamreel.api.dramaforge.repository.DramaForgeAssetRepository;
import com.dreamreel.api.dramaforge.repository.DramaForgeConfigRepository;
import com.dreamreel.api.dramaforge.repository.DramaForgeShotRepository;
import com.dreamreel.api.client.TokenFreeClient;
import com.dreamreel.api.config.TokenFreeProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * 视频生成前规划镜头出场角色/场景/道具，名称对齐资源库，供视频模型多图参考。
 */
@Service
@Transactional
public class DramaForgeShotAssetPlanner {

    private static final List<String> FALLBACK_TEXT_MODELS = List.of("qwen-plus", "deepseek-chat");

    private static final String PLAN_SYSTEM = """
            你是 DramaForge 镜头资产规划师。根据镜头描述，从【资源库资产清单】中挑选本镜头出现的角色、场景、道具。
            这些资产设计图会直接送给视频生成模型作为参考，不做分镜图。
            规则：
            1. 只能使用清单中已有的名称，禁止编造
            2. 每个镜头输出：characters（数组，0～3 个）、scene（单个场景名）、props（数组，0～3 个）
            3. 根据画面描述与对白推断出镜角色与所在场景；描述/对白未点名任何角色时 characters 必须为 []，禁止塞入全剧主角
            4. 资源库有场景时，scene 不要留空：电话对白、反应镜头、特写也应继承本集主场景（如厂房/室内）
            5. 只输出 JSON，格式：{"shots":[{"shotNumber":1,"characters":["角色A"],"scene":"场景名","props":[]}]}
            6. 空镜/环境镜头（仅场景无人物）characters 必须为 []
            """;

    private final DramaForgeShotRepository shotRepository;
    private final DramaForgeAssetRepository assetRepository;
    private final DramaForgeConfigRepository configRepository;
    private final TokenFreeClient tokenFreeClient;
    private final TokenFreeProperties tokenFreeProperties;
    private final ObjectMapper objectMapper;

    public DramaForgeShotAssetPlanner(
            DramaForgeShotRepository shotRepository,
            DramaForgeAssetRepository assetRepository,
            DramaForgeConfigRepository configRepository,
            TokenFreeClient tokenFreeClient,
            TokenFreeProperties tokenFreeProperties,
            ObjectMapper objectMapper) {
        this.shotRepository = shotRepository;
        this.assetRepository = assetRepository;
        this.configRepository = configRepository;
        this.tokenFreeClient = tokenFreeClient;
        this.tokenFreeProperties = tokenFreeProperties;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void planEpisodeShots(UUID projectId, UUID episodeId, String apiKey) {
        var assets = assetRepository.findByProjectIdOrderBySortOrderAscNameAsc(projectId);
        var shots = shotRepository.findByEpisodeIdOrderByShotNumberAsc(episodeId);
        if (shots.isEmpty()) {
            return;
        }
        var config = configRepository.findByProjectId(projectId).orElse(null);
        // 先场景后角色，并在 LLM 前补齐场景，避免「空角色被脑补主角 → 再补场景」
        var preserveEmptyCharacters = snapshotPreserveEmptyCharacters(shots);
        for (var shot : shots) {
            applySceneAndPropHeuristic(shot, assets, config);
            shotRepository.save(shot);
        }
        fillMissingScenes(shots, assets, config);
        for (var shot : shots) {
            applyCharacterHeuristic(shot, assets, preserveEmptyCharacters.contains(shot.getId()));
            shotRepository.save(shot);
        }
        if (apiKey != null && !apiKey.isBlank()) {
            try {
                applyLlmPlan(projectId, shots, assets, apiKey, preserveEmptyCharacters);
                for (var shot : shots) {
                    shotRepository.save(shot);
                }
            } catch (Exception ignored) {
                // 启发式结果已保存，LLM 失败不阻断视频生成
            }
        }
        for (var shot : shots) {
            materializePlanningPrompt(shot, assets);
            shotRepository.save(shot);
        }
    }

    /** 与调用方同一事务内执行启发式规划（解析剧本后使用，避免 NOT_SUPPORTED 脏会话） */
    public void planEpisodeShotsLocally(UUID projectId, UUID episodeId, List<DramaForgeAsset> assets) {
        var config = configRepository.findByProjectId(projectId).orElse(null);
        var shots = shotRepository.findByEpisodeIdOrderByShotNumberAsc(episodeId);
        if (shots.isEmpty()) {
            return;
        }
        var preserveEmptyCharacters = snapshotPreserveEmptyCharacters(shots);
        for (var shot : shots) {
            applySceneAndPropHeuristic(shot, assets, config);
        }
        fillMissingScenes(shots, assets, config);
        for (var shot : shots) {
            applyCharacterHeuristic(shot, assets, preserveEmptyCharacters.contains(shot.getId()));
        }
        for (var shot : shots) {
            materializePlanningPrompt(shot, assets);
            shotRepository.save(shot);
        }
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void planShot(UUID projectId, DramaForgeShot shot, List<DramaForgeAsset> projectAssets, String apiKey) {
        var config = configRepository.findByProjectId(projectId).orElse(null);
        var preserveEmptyCharacters = snapshotPreserveEmptyCharacters(List.of(shot));
        applySceneAndPropHeuristic(shot, projectAssets, config);
        ensureSceneRef(shot, projectAssets, config, List.of());
        applyCharacterHeuristic(shot, projectAssets, preserveEmptyCharacters.contains(shot.getId()));
        shotRepository.save(shot);
        if (apiKey != null && !apiKey.isBlank()) {
            try {
                applyLlmPlan(projectId, List.of(shot), projectAssets, apiKey, preserveEmptyCharacters);
                shotRepository.save(shot);
            } catch (Exception ignored) {
            }
        }
        materializePlanningPrompt(shot, projectAssets);
        shotRepository.save(shot);
    }

    /** 把角色/场景/道具线索写入镜头 description，规划阶段即可看到完整提示词 */
    void materializePlanningPrompt(DramaForgeShot shot, List<DramaForgeAsset> assets) {
        var chars = readStringList(shot.getCharacterRefsJson());
        var props = readStringList(shot.getPropRefsJson());
        if (chars.isEmpty()
                && (shot.getSceneRef() == null || shot.getSceneRef().isBlank())
                && props.isEmpty()) {
            return;
        }
        shot.setDescription(DramaForgeStylePrompts.materializePlanningDescription(shot, chars, props, assets));
    }

    /**
     * 仅「显式空角色列表 {@code []} + 已有场景」视为空镜，禁止再补主角。
     * {@code characterRefsJson == null} 表示尚未规划（如 scenes[].shots 只继承了父场景），仍允许文案匹配角色。
     */
    private Set<UUID> snapshotPreserveEmptyCharacters(List<DramaForgeShot> shots) {
        var ids = new HashSet<UUID>();
        for (var shot : shots) {
            if (isExplicitEmptyCharacterRefs(shot.getCharacterRefsJson())
                    && shot.getSceneRef() != null
                    && !shot.getSceneRef().isBlank()) {
                ids.add(shot.getId());
            }
        }
        return ids;
    }

    /** 显式空数组；null/空白视为未规划。 */
    static boolean isExplicitEmptyCharacterRefs(String characterRefsJson) {
        return characterRefsJson != null && "[]".equals(characterRefsJson.trim());
    }

    private void applySceneAndPropHeuristic(DramaForgeShot shot, List<DramaForgeAsset> assets, DramaForgeConfig config) {
        var text = combinedShotText(shot, config);
        if (shot.getSceneRef() == null || shot.getSceneRef().isBlank()) {
            shot.setSceneRef(fuzzyMatchScene(text, assets));
        } else {
            shot.setSceneRef(validateSingleName(shot.getSceneRef(), assets, DramaForgeAssetType.SCENE));
        }

        var existingProps = readStringList(shot.getPropRefsJson());
        if (existingProps.isEmpty()) {
            shot.setPropRefsJson(writeJson(matchAssetNames(text, assets, DramaForgeAssetType.PROP)));
        } else {
            shot.setPropRefsJson(writeJson(validateNames(existingProps, assets, DramaForgeAssetType.PROP)));
        }
    }

    private void applyCharacterHeuristic(
            DramaForgeShot shot,
            List<DramaForgeAsset> assets,
            boolean preserveEmptyCharacters) {
        var existingChars = readStringList(shot.getCharacterRefsJson());
        if (existingChars.isEmpty()) {
            if (preserveEmptyCharacters) {
                // 已有场景的空镜：保持空角色
                return;
            }
            // 仅匹配镜头描述/对白中显式出现的角色名，不用风格总文案以免误伤
            var text = shotBodyText(shot);
            shot.setCharacterRefsJson(writeJson(matchAssetNames(text, assets, DramaForgeAssetType.CHARACTER)));
        } else {
            shot.setCharacterRefsJson(writeJson(validateNames(existingChars, assets, DramaForgeAssetType.CHARACTER)));
        }
    }

    /** 本集镜头规划后：仍缺场景的镜头继承主场景或从风格文案匹配 */
    private void fillMissingScenes(List<DramaForgeShot> shots, List<DramaForgeAsset> assets, DramaForgeConfig config) {
        for (var shot : shots) {
            ensureSceneRef(shot, assets, config, shots);
        }
        var majority = majorityScene(shots);
        if (majority == null) {
            return;
        }
        for (var shot : shots) {
            if (shot.getSceneRef() == null || shot.getSceneRef().isBlank()) {
                shot.setSceneRef(majority);
            }
        }
    }

    private void ensureSceneRef(
            DramaForgeShot shot,
            List<DramaForgeAsset> assets,
            DramaForgeConfig config,
            List<DramaForgeShot> siblings) {
        if (shot.getSceneRef() != null && !shot.getSceneRef().isBlank()) {
            var validated = validateSingleName(shot.getSceneRef(), assets, DramaForgeAssetType.SCENE);
            if (validated != null) {
                shot.setSceneRef(validated);
                return;
            }
            shot.setSceneRef(null);
        }
        var matched = fuzzyMatchScene(combinedShotText(shot, config), assets);
        if (matched != null) {
            shot.setSceneRef(matched);
            return;
        }
        var majority = majorityScene(siblings);
        if (majority != null) {
            shot.setSceneRef(majority);
            return;
        }
        var sole = soleSceneWithImage(assets);
        if (sole != null) {
            shot.setSceneRef(sole);
        }
    }

    private static String majorityScene(List<DramaForgeShot> shots) {
        if (shots == null || shots.isEmpty()) {
            return null;
        }
        var counts = new java.util.HashMap<String, Integer>();
        for (var shot : shots) {
            if (shot.getSceneRef() == null || shot.getSceneRef().isBlank()) {
                continue;
            }
            counts.merge(shot.getSceneRef(), 1, Integer::sum);
        }
        return counts.entrySet().stream()
                .max(java.util.Map.Entry.comparingByValue())
                .map(java.util.Map.Entry::getKey)
                .orElse(null);
    }

    private static String soleSceneWithImage(List<DramaForgeAsset> assets) {
        var scenes = assets.stream()
                .filter(a -> a.getType() == DramaForgeAssetType.SCENE)
                .filter(a -> a.getReferenceImageUrl() != null && !a.getReferenceImageUrl().isBlank())
                .toList();
        return scenes.size() == 1 ? scenes.getFirst().getName() : null;
    }

    /** 精确命中名称，或文案命中场景名中的连续片段（如「厂房」匹配「破旧厂房」） */
    static String fuzzyMatchScene(String text, List<DramaForgeAsset> assets) {
        var exact = matchSingleAssetName(text, assets, DramaForgeAssetType.SCENE);
        if (exact != null) {
            return exact;
        }
        if (text == null || text.isBlank()) {
            return null;
        }
        var lower = text.toLowerCase(Locale.ROOT);
        String best = null;
        int bestScore = 0;
        for (var asset : assets) {
            if (asset.getType() != DramaForgeAssetType.SCENE) {
                continue;
            }
            var name = asset.getName();
            if (name == null || name.isBlank()) {
                continue;
            }
            var nameLower = name.toLowerCase(Locale.ROOT);
            if (lower.contains(nameLower) && name.length() > bestScore) {
                best = name;
                bestScore = name.length();
                continue;
            }
            for (int len = name.length() - 1; len >= 2; len--) {
                for (int i = 0; i + len <= name.length(); i++) {
                    var part = nameLower.substring(i, i + len);
                    if (lower.contains(part) && len > bestScore) {
                        best = name;
                        bestScore = len;
                    }
                }
            }
        }
        return best;
    }

    private void applyLlmPlan(
            UUID projectId,
            List<DramaForgeShot> shots,
            List<DramaForgeAsset> assets,
            String apiKey,
            Set<UUID> preserveEmptyCharacters) {
        var config = configRepository.findByProjectId(projectId).orElse(null);
        var user = buildPlannerUserPrompt(shots, assets);
        var json = callPlannerLlm(config, apiKey, user);
        if (json == null || json.isBlank()) {
            return;
        }
        try {
            var root = objectMapper.readTree(cleanJson(json));
            var plans = root.has("shots") ? root.get("shots") : root;
            if (!plans.isArray()) {
                return;
            }
            for (var plan : plans) {
                var shotNumber = plan.path("shotNumber").asInt(-1);
                if (shotNumber < 0) {
                    continue;
                }
                shots.stream()
                        .filter(s -> s.getShotNumber() == shotNumber)
                        .findFirst()
                        .ifPresent(shot -> applyPlanNode(
                                shot, plan, assets, preserveEmptyCharacters.contains(shot.getId())));
            }
        } catch (Exception ignored) {
        }
    }

    private void applyPlanNode(
            DramaForgeShot shot,
            JsonNode plan,
            List<DramaForgeAsset> assets,
            boolean preserveEmptyCharacters) {
        if (plan.has("characters") && plan.get("characters").isArray()) {
            var existingChars = readStringList(shot.getCharacterRefsJson());
            // 空角色列表一律不让 LLM 脑补（含「本轮才补上 scene」的空镜）
            if (!existingChars.isEmpty() && !preserveEmptyCharacters) {
                var names = new ArrayList<String>();
                plan.get("characters").forEach(node -> {
                    var name = validateSingleName(node.asText(), assets, DramaForgeAssetType.CHARACTER);
                    if (name != null) {
                        names.add(name);
                    }
                });
                if (!names.isEmpty()) {
                    shot.setCharacterRefsJson(writeJson(names));
                }
            }
        }
        if (plan.has("scene")) {
            var scene = validateSingleName(plan.get("scene").asText(), assets, DramaForgeAssetType.SCENE);
            if (scene != null) {
                shot.setSceneRef(scene);
            }
        }
        if (plan.has("props") && plan.get("props").isArray()) {
            var props = new ArrayList<String>();
            plan.get("props").forEach(node -> {
                var name = validateSingleName(node.asText(), assets, DramaForgeAssetType.PROP);
                if (name != null) {
                    props.add(name);
                }
            });
            if (!props.isEmpty()) {
                shot.setPropRefsJson(writeJson(props));
            }
        }
    }

    private String buildPlannerUserPrompt(List<DramaForgeShot> shots, List<DramaForgeAsset> assets) {
        var sb = new StringBuilder("【资源库资产清单】\n");
        appendAssetGroup(sb, assets, DramaForgeAssetType.CHARACTER, "角色");
        appendAssetGroup(sb, assets, DramaForgeAssetType.SCENE, "场景");
        appendAssetGroup(sb, assets, DramaForgeAssetType.PROP, "道具");
        sb.append("\n【待规划镜头】\n");
        for (var shot : shots) {
            sb.append("镜头").append(shot.getShotNumber()).append("：")
                    .append(shot.getDescription());
            if (shot.getDialogue() != null && !shot.getDialogue().isBlank()) {
                sb.append(" | 对白：").append(shot.getDialogue());
            }
            sb.append('\n');
        }
        sb.append("\n请为每个镜头规划 characters、scene、props。资源库有场景时，每个镜头都必须给出 scene，不要留空。");
        return sb.toString();
    }

    private void appendAssetGroup(StringBuilder sb, List<DramaForgeAsset> assets, DramaForgeAssetType type, String label) {
        sb.append(label).append("：");
        var names = assets.stream().filter(a -> a.getType() == type).map(DramaForgeAsset::getName).toList();
        if (names.isEmpty()) {
            sb.append("（无）");
        } else {
            sb.append(String.join("、", names));
        }
        sb.append('\n');
    }

    private String callPlannerLlm(DramaForgeConfig config, String apiKey, String userPrompt) {
        var primary = resolveTextModel(config);
        var result = invoke(apiKey, primary, PLAN_SYSTEM, userPrompt);
        if (result != null) {
            return result;
        }
        for (var fallback : FALLBACK_TEXT_MODELS) {
            if (fallback.equals(primary)) {
                continue;
            }
            result = invoke(apiKey, fallback, PLAN_SYSTEM, userPrompt);
            if (result != null) {
                return result;
            }
        }
        return null;
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

    private String resolveTextModel(DramaForgeConfig config) {
        if (config != null && config.getTextBackend() != null && config.getTextBackend().contains("/")) {
            return config.getTextBackend().substring(config.getTextBackend().indexOf('/') + 1);
        }
        var configured = tokenFreeProperties.defaultChatModel();
        return configured != null && !configured.isBlank() ? configured : "qwen-max";
    }

    static List<String> matchAssetNames(String text, List<DramaForgeAsset> assets, DramaForgeAssetType type) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        var lower = text.toLowerCase(Locale.ROOT);
        var matched = new LinkedHashSet<String>();
        assets.stream()
                .filter(a -> a.getType() == type)
                .sorted(Comparator.comparingInt((DramaForgeAsset a) -> a.getName().length()).reversed())
                .forEach(asset -> {
                    if (lower.contains(asset.getName().toLowerCase(Locale.ROOT))) {
                        matched.add(asset.getName());
                    }
                });
        return new ArrayList<>(matched);
    }

    static String matchSingleAssetName(String text, List<DramaForgeAsset> assets, DramaForgeAssetType type) {
        var names = matchAssetNames(text, assets, type);
        return names.isEmpty() ? null : names.getFirst();
    }

    private static String validateSingleName(String name, List<DramaForgeAsset> assets, DramaForgeAssetType type) {
        if (name == null || name.isBlank()) {
            return null;
        }
        return assets.stream()
                .filter(a -> a.getType() == type)
                .filter(a -> a.getName().equalsIgnoreCase(name.trim()))
                .map(DramaForgeAsset::getName)
                .findFirst()
                .orElse(null);
    }

    private static List<String> validateNames(List<String> names, List<DramaForgeAsset> assets, DramaForgeAssetType type) {
        var valid = new ArrayList<String>();
        for (var name : names) {
            var resolved = validateSingleName(name, assets, type);
            if (resolved != null) {
                valid.add(resolved);
            }
        }
        return valid;
    }

    private static String combinedShotText(DramaForgeShot shot, DramaForgeConfig config) {
        var sb = new StringBuilder(shotBodyText(shot));
        if (shot.getSceneRef() != null && !shot.getSceneRef().isBlank()) {
            sb.append(shot.getSceneRef());
        }
        if (config != null && config.getStylePrompt() != null && !config.getStylePrompt().isBlank()) {
            sb.append(config.getStylePrompt());
        }
        return sb.toString();
    }

    /** 仅镜头描述+对白，用于角色名匹配，避免风格总览里的主角名污染空镜 */
    private static String shotBodyText(DramaForgeShot shot) {
        var sb = new StringBuilder();
        if (shot.getDescription() != null) {
            sb.append(shot.getDescription());
        }
        if (shot.getDialogue() != null && !shot.getDialogue().isBlank()) {
            sb.append(shot.getDialogue());
        }
        return sb.toString();
    }

    private static String combinedShotText(DramaForgeShot shot) {
        return combinedShotText(shot, null);
    }

    private String cleanJson(String text) {
        var trimmed = text.trim();
        if (trimmed.startsWith("```")) {
            var end = trimmed.lastIndexOf("```");
            if (end > 3) {
                trimmed = trimmed.substring(trimmed.indexOf('\n') + 1, end).trim();
            }
        }
        var start = trimmed.indexOf('{');
        var end = trimmed.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return trimmed.substring(start, end + 1);
        }
        return trimmed;
    }

    private List<String> readStringList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {});
        } catch (Exception ex) {
            return List.of();
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return "[]";
        }
    }
}
