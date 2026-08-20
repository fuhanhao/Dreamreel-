# -*- coding: utf-8 -*-
from pathlib import Path
import re

ROOT = Path(r"c:\Users\L1822\IdeaProjects\dreamreel")
svc = ROOT / "services/api/src/main/java/com/dreamreel/api/dramaforge/service/DramaForgeService.java"
text = svc.read_text(encoding="utf-8")

# --- imports / self injection ---
if "Propagation" not in text:
    text = text.replace(
        "import org.springframework.transaction.annotation.Transactional;",
        "import org.springframework.context.annotation.Lazy;\n"
        "import org.springframework.transaction.annotation.Propagation;\n"
        "import org.springframework.transaction.annotation.Transactional;",
    )

if "private final DramaForgeService self;" not in text:
    text = text.replace(
        "    private final UploadStorageService uploadStorageService;\n",
        "    private final UploadStorageService uploadStorageService;\n"
        "    private final DramaForgeService self;\n",
    )
    text = text.replace(
        "            UploadStorageService uploadStorageService) {",
        "            UploadStorageService uploadStorageService,\n"
        "            @Lazy DramaForgeService self) {",
    )
    text = text.replace(
        "        this.uploadStorageService = uploadStorageService;\n    }",
        "        this.uploadStorageService = uploadStorageService;\n"
        "        this.self = self;\n    }",
    )

# --- replace generateVideos body via regex ---
gen_videos_pat = re.compile(
    r"public List<ShotResponse> generateVideos\(\s*"
    r"UUID projectId,\s*"
    r"UUID episodeId,\s*"
    r"String apiKeyHeader,\s*"
    r"DramaForgeBatchProgress progress\) \{.*?return results;\n    \}",
    re.S,
)
gen_videos_new = '''public List<ShotResponse> generateVideos(
            UUID projectId,
            UUID episodeId,
            String apiKeyHeader,
            DramaForgeBatchProgress progress) {
        var config = requireConfig(projectId);
        var episode = requireEpisode(projectId, episodeId);
        var shots = shotRepository.findByEpisodeIdOrderByShotNumberAsc(episode.getId()).stream()
                .filter(shot -> shot.getStatus() != DramaForgeShotStatus.VIDEO_DONE)
                .toList();
        if (shots.isEmpty()) {
            throw new IllegalStateException("没有待生成视频的镜头");
        }

        // 批量前先用 AI 规划角色/场景/道具绑定
        shotAssetPlanner.planEpisodeShots(projectId, episodeId, apiKeyHeader);

        var total = shots.size();
        var results = new ArrayList<ShotResponse>();
        // 每个镜头独立事务，避免一镜失败导致整批 rollback-only
        for (int i = 0; i < shots.size(); i++) {
            var shot = shots.get(i);
            if (progress != null) {
                progress.report(i, total,
                        "正在生成镜头 " + shot.getShotNumber() + "（" + (i + 1) + "/" + total + "）");
            }
            try {
                var done = self.generateShotVideoInNewTx(projectId, config, shot.getId(), apiKeyHeader);
                results.add(done);
                if (progress != null) {
                    progress.report(i + 1, total,
                            "已完成镜头 " + shot.getShotNumber() + "（" + (i + 1) + "/" + total + "）");
                }
            } catch (Exception ex) {
                if (progress != null) {
                    var msg = ex.getMessage() != null ? ex.getMessage() : "unknown";
                    progress.report(i + 1, total,
                            "镜头 " + shot.getShotNumber() + " 失败: " + msg);
                }
            }
        }
        if (results.isEmpty()) {
            throw new IllegalStateException("全部镜头视频生成失败，请检查资产设计图与方舟配置后重试");
        }
        return results;
    }'''

m = gen_videos_pat.search(text)
if not m:
    raise SystemExit("generateVideos pattern not found")
text = text[: m.start()] + gen_videos_new + text[m.end() :]

# annotate generateVideos wrappers as NOT_SUPPORTED (no outer TX)
if "@Transactional(propagation = Propagation.NOT_SUPPORTED)\n    public List<ShotResponse> generateVideos(UUID projectId, UUID episodeId, String apiKeyHeader)" not in text:
    text = text.replace(
        "    public List<ShotResponse> generateVideos(UUID projectId, UUID episodeId, String apiKeyHeader) {\n"
        "        return generateVideos(projectId, episodeId, apiKeyHeader, null);\n"
        "    }\n\n"
        "    public List<ShotResponse> generateVideos(",
        "    @Transactional(propagation = Propagation.NOT_SUPPORTED)\n"
        "    public List<ShotResponse> generateVideos(UUID projectId, UUID episodeId, String apiKeyHeader) {\n"
        "        return generateVideos(projectId, episodeId, apiKeyHeader, null);\n"
        "    }\n\n"
        "    @Transactional(propagation = Propagation.NOT_SUPPORTED)\n"
        "    public List<ShotResponse> generateVideos(",
    )

# Add REQUIRES_NEW public method before generateShotVideoInternal
if "generateShotVideoInNewTx" not in text:
    text = text.replace(
        "    private ShotResponse generateShotVideoInternal(",
        '''    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ShotResponse generateShotVideoInNewTx(
            UUID projectId,
            DramaForgeConfig config,
            UUID shotId,
            String apiKeyHeader) {
        var shot = shotRepository.findById(shotId)
                .orElseThrow(() -> new ResourceNotFoundException("镜头不存在: " + shotId));
        return generateShotVideoInternal(projectId, config, shot, apiKeyHeader);
    }

    private ShotResponse generateShotVideoInternal(''',
    )

# Fix video create call: duration + 480p + previous video ref
old_create = '''        var result = videoGenerationService.createForProject(projectId,
                new CreateVideoGenerationRequest(
                        projectId,
                        shot.getId().toString(),
                        resolveVideoModel(config),
                        prompt,
                        5,
                        ratio,
                        resolveVideoQuality(config),
                        "reference-to-video",
                        assetRefs.urls().getFirst(),
                        null,
                        assetRefs.urls(),
                        voiceRefs.urls().isEmpty() ? null : voiceRefs.urls()),
                apiKeyHeader);'''

new_create = '''        // 镜头绑定资产提升到全局；视频固定 480p；时长 2-15s；可接上一段视频
        promoteShotAssetsToGlobal(projectId, shot, projectAssets);
        projectAssets = assetRepository.findByProjectIdOrderBySortOrderAscNameAsc(projectId);
        assetRefs = resolveShotAssetVideoReferences(shot, projectAssets);
        if (assetRefs.urls().isEmpty()) {
            throw new IllegalStateException(
                    "镜头 " + shot.getShotNumber() + " 缺少可用参考图：请先为关联角色/场景/道具生成设计图");
        }

        var result = videoGenerationService.createForProject(projectId,
                new CreateVideoGenerationRequest(
                        projectId,
                        shot.getId().toString(),
                        resolveVideoModel(config),
                        prompt,
                        resolveShotDurationSeconds(shot),
                        ratio,
                        "480p",
                        "reference-to-video",
                        assetRefs.urls().getFirst(),
                        resolvePreviousShotVideoUrl(shot),
                        assetRefs.urls(),
                        voiceRefs.urls().isEmpty() ? null : voiceRefs.urls()),
                apiKeyHeader);'''

if old_create not in text:
    raise SystemExit("createForProject block not found")
text = text.replace(old_create, new_create)

# Fix asset refs empty message earlier block - may still have old message
text = re.sub(
    r'throw new IllegalStateException\(\s*"\?\? " \+ shot\.getShotNumber\(\)\s*\+\s*"[^"]*"\s*\);',
    'throw new IllegalStateException(\n'
    '                    "镜头 " + shot.getShotNumber()\n'
    '                            + " 缺少可用参考图：请先为关联角色/场景/道具生成设计图");',
    text,
)

# Helper methods before resolveEpisodeNumber
helpers = '''
    /** 将镜头中的角色/场景/道具引用提升为项目全局资产（已存在则跳过） */
    public List<AssetResponse> promoteShotAssets(UUID projectId, UUID episodeId, UUID shotId) {
        requireEpisode(projectId, episodeId);
        var shot = requireShot(episodeId, shotId);
        var projectAssets = assetRepository.findByProjectIdOrderBySortOrderAscNameAsc(projectId);
        promoteShotAssetsToGlobal(projectId, shot, projectAssets);
        return listAssets(projectId);
    }

    /** 从上一个镜头提取绑定资产到全局 */
    public List<AssetResponse> promotePreviousShotAssets(UUID projectId, UUID episodeId, UUID shotId) {
        requireEpisode(projectId, episodeId);
        var shot = requireShot(episodeId, shotId);
        var previous = shotRepository.findByEpisodeIdOrderByShotNumberAsc(episodeId).stream()
                .filter(s -> s.getShotNumber() < shot.getShotNumber())
                .reduce((a, b) -> b)
                .orElseThrow(() -> new IllegalStateException("当前镜头没有上一个片段"));
        var projectAssets = assetRepository.findByProjectIdOrderBySortOrderAscNameAsc(projectId);
        promoteShotAssetsToGlobal(projectId, previous, projectAssets);
        // 把上一段的资产引用合并到当前镜头，便于连续生成
        mergeShotAssetRefs(shot, previous);
        shotRepository.save(shot);
        return listAssets(projectId);
    }

    private void mergeShotAssetRefs(DramaForgeShot target, DramaForgeShot source) {
        var chars = new java.util.LinkedHashSet<>(readStringList(target.getCharacterRefsJson()));
        chars.addAll(readStringList(source.getCharacterRefsJson()));
        target.setCharacterRefsJson(writeJson(new ArrayList<>(chars)));
        var props = new java.util.LinkedHashSet<>(readStringList(target.getPropRefsJson()));
        props.addAll(readStringList(source.getPropRefsJson()));
        target.setPropRefsJson(writeJson(new ArrayList<>(props)));
        if ((target.getSceneRef() == null || target.getSceneRef().isBlank())
                && source.getSceneRef() != null && !source.getSceneRef().isBlank()) {
            target.setSceneRef(source.getSceneRef());
        }
    }

    private void promoteShotAssetsToGlobal(
            UUID projectId,
            DramaForgeShot shot,
            List<DramaForgeAsset> projectAssets) {
        for (var name : readStringList(shot.getCharacterRefsJson())) {
            ensureGlobalAsset(projectId, DramaForgeAssetType.CHARACTER, name, projectAssets);
        }
        if (shot.getSceneRef() != null && !shot.getSceneRef().isBlank()) {
            ensureGlobalAsset(projectId, DramaForgeAssetType.SCENE, shot.getSceneRef(), projectAssets);
        }
        for (var name : readStringList(shot.getPropRefsJson())) {
            ensureGlobalAsset(projectId, DramaForgeAssetType.PROP, name, projectAssets);
        }
    }

    private void ensureGlobalAsset(
            UUID projectId,
            DramaForgeAssetType type,
            String name,
            List<DramaForgeAsset> projectAssets) {
        if (name == null || name.isBlank()) {
            return;
        }
        var trimmed = name.trim();
        var exists = projectAssets.stream()
                .anyMatch(a -> a.getType() == type && a.getName().equalsIgnoreCase(trimmed));
        if (exists) {
            return;
        }
        var asset = new DramaForgeAsset();
        asset.setProjectId(projectId);
        asset.setType(type);
        asset.setName(trimmed);
        asset.setDescription(trimmed);
        asset.setSortOrder(projectAssets.size());
        projectAssets.add(assetRepository.save(asset));
    }

    /** Seedance 平台时长规则：2–15 秒 */
    private int resolveShotDurationSeconds(DramaForgeShot shot) {
        if (shot.getDurationSeconds() != null && shot.getDurationSeconds() > 0) {
            return Math.max(2, Math.min(15, shot.getDurationSeconds()));
        }
        return estimateDurationSeconds(shot.getDescription(), shot.getDialogue());
    }

    static int estimateDurationSeconds(String description, String dialogue) {
        var descLen = description != null ? description.trim().length() : 0;
        var dialLen = dialogue != null ? dialogue.trim().length() : 0;
        // 粗估：对白约 4 字/秒，画面描述额外 2–4 秒
        var fromDialogue = dialLen > 0 ? (int) Math.ceil(dialLen / 4.0) + 1 : 0;
        var fromDesc = descLen > 80 ? 8 : descLen > 40 ? 6 : 5;
        var seconds = Math.max(fromDialogue, fromDesc);
        return Math.max(2, Math.min(15, seconds));
    }

    private String resolvePreviousShotVideoUrl(DramaForgeShot shot) {
        if (shot.getReferenceVideoUrl() != null && !shot.getReferenceVideoUrl().isBlank()) {
            return shot.getReferenceVideoUrl();
        }
        return shotRepository.findByEpisodeIdOrderByShotNumberAsc(shot.getEpisodeId()).stream()
                .filter(s -> s.getShotNumber() < shot.getShotNumber())
                .filter(s -> s.getVideoJobId() != null)
                .reduce((a, b) -> b)
                .map(statusCalculator::resolveVideoUrl)
                .filter(url -> url != null && !url.isBlank())
                .orElse(null);
    }

'''

if "promoteShotAssetsToGlobal" not in text:
    text = text.replace(
        "    private int resolveEpisodeNumber(UUID projectId, Integer requested) {",
        helpers + "    private int resolveEpisodeNumber(UUID projectId, Integer requested) {",
    )

# ShotResponse should include durationSeconds
old_shot_resp = '''        return new ShotResponse(
                shot.getId(),
                shot.getEpisodeId(),
                shot.getShotNumber(),
                shot.getDescription(),
                shot.getDialogue(),
                shot.getCameraNote(),
                readStringList(shot.getCharacterRefsJson()),
                shot.getSceneRef(),
                readStringList(shot.getPropRefsJson()),
                shot.getStoryboardUrl(),
                shot.getVideoJobId(),
                statusCalculator.resolveVideoUrl(shot),
                shot.getStatus().name().toLowerCase(),
                shot.getCreatedAt(),
                shot.getUpdatedAt()
        );'''
new_shot_resp = '''        return new ShotResponse(
                shot.getId(),
                shot.getEpisodeId(),
                shot.getShotNumber(),
                shot.getDescription(),
                shot.getDialogue(),
                shot.getCameraNote(),
                readStringList(shot.getCharacterRefsJson()),
                shot.getSceneRef(),
                readStringList(shot.getPropRefsJson()),
                shot.getStoryboardUrl(),
                shot.getVideoJobId(),
                statusCalculator.resolveVideoUrl(shot),
                resolveShotDurationSeconds(shot),
                shot.getStatus().name().toLowerCase(),
                shot.getCreatedAt(),
                shot.getUpdatedAt()
        );'''
if old_shot_resp in text:
    text = text.replace(old_shot_resp, new_shot_resp)

# resolveVideoQuality force 480p
text = re.sub(
    r"private String resolveVideoQuality\(DramaForgeConfig config\) \{.*?\}",
    'private String resolveVideoQuality(DramaForgeConfig config) {\n'
    '        return "480p";\n'
    '    }',
    text,
    count=1,
    flags=re.S,
)

# parseShots: set duration from JSON or estimate
if "setDurationSeconds" not in text:
    text = text.replace(
        "shot.setDescription(description.trim());\n                        shot.setDialogue(firstText(shotNode, \"dialogue\", \"narration\"));",
        "shot.setDescription(description.trim());\n"
        "                        shot.setDialogue(firstText(shotNode, \"dialogue\", \"narration\"));\n"
        "                        shot.setDurationSeconds(parseDurationSeconds(shotNode, shot.getDescription(), shot.getDialogue()));",
    )
    # also for the shots[] branch
    text = text.replace(
        "shot.setDescription(description.trim());\n                    shot.setDialogue(firstText(shotNode, \"dialogue\", \"narration\"));\n                    shot.setCameraNote(firstText(shotNode, \"camera\", \"camera_note\"));\n                    shot.setSceneRef(firstText(shotNode, \"scene\"));",
        "shot.setDescription(description.trim());\n"
        "                    shot.setDialogue(firstText(shotNode, \"dialogue\", \"narration\"));\n"
        "                    shot.setDurationSeconds(parseDurationSeconds(shotNode, shot.getDescription(), shot.getDialogue()));\n"
        "                    shot.setCameraNote(firstText(shotNode, \"camera\", \"camera_note\"));\n"
        "                    shot.setSceneRef(firstText(shotNode, \"scene\"));",
    )

if "parseDurationSeconds" not in text:
    text = text.replace(
        "    static int estimateDurationSeconds(String description, String dialogue) {",
        '''    private Integer parseDurationSeconds(JsonNode shotNode, String description, String dialogue) {
        for (var key : List.of("duration", "duration_seconds", "seconds", "时长")) {
            if (shotNode.has(key) && !shotNode.get(key).isNull()) {
                try {
                    var v = shotNode.get(key).isNumber()
                            ? shotNode.get(key).asInt()
                            : Integer.parseInt(shotNode.get(key).asText().replaceAll("[^0-9]", ""));
                    if (v > 0) {
                        return Math.max(2, Math.min(15, v));
                    }
                } catch (Exception ignored) {
                }
            }
        }
        return estimateDurationSeconds(description, dialogue);
    }

    static int estimateDurationSeconds(String description, String dialogue) {''',
    )

# updateShot duration
if "request.durationSeconds()" not in text:
    text = text.replace(
        "        if (request.status() != null) {\n            shot.setStatus(parseShotStatus(request.status()));\n        }\n        return toShotResponse(shotRepository.save(shot));",
        "        if (request.status() != null) {\n            shot.setStatus(parseShotStatus(request.status()));\n        }\n"
        "        if (request.durationSeconds() != null) {\n"
        "            shot.setDurationSeconds(Math.max(2, Math.min(15, request.durationSeconds())));\n"
        "        }\n"
        "        return toShotResponse(shotRepository.save(shot));",
    )

# Fix more garbled IllegalStateExceptions
repls = {
    'throw new IllegalStateException("???????? JSON");':
        'throw new IllegalStateException("剧集缺少剧本 JSON");',
    'throw new IllegalStateException("?? JSON ??? scenes[].shots ??shots ??");':
        'throw new IllegalStateException("剧本 JSON 需包含 scenes[].shots 或 shots 数组");',
    'throw new IllegalStateException("????? JSON ?????????");':
        'throw new IllegalStateException("未能从剧本 JSON 解析出任何镜头");',
    '.orElseThrow(() -> new ResourceNotFoundException("?????? " + assetId));':
        '.orElseThrow(() -> new ResourceNotFoundException("资产不存在: " + assetId));',
    '.orElseThrow(() -> new ResourceNotFoundException("?????? " + episodeId));':
        '.orElseThrow(() -> new ResourceNotFoundException("剧集不存在: " + episodeId));',
    '.orElseThrow(() -> new ResourceNotFoundException("?????? " + shotId));':
        '.orElseThrow(() -> new ResourceNotFoundException("镜头不存在: " + shotId));',
}
for o, n in repls.items():
    text = text.replace(o, n)

svc.write_text(text, encoding="utf-8")
print("DramaForgeService updated")
