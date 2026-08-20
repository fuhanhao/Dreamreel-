# -*- coding: utf-8 -*-
"""Fix DramaForgeService encoding + batch video progress messages."""
from pathlib import Path

ROOT = Path(r"c:\Users\L1822\IdeaProjects\dreamreel")
path = ROOT / "services/api/src/main/java/com/dreamreel/api/dramaforge/service/DramaForgeService.java"
text = path.read_text(encoding="utf-8")

replacements = [
    (
        'throw new IllegalStateException("?????????");\n        }\n\n        // ???????agent ???????????/??/??\n        shotAssetPlanner.planEpisodeShots(projectId, episodeId, apiKeyHeader);\n\n        var total = shots.size();\n        var results = new ArrayList<ShotResponse>();\n        // ?????????????????????????????\n        for (int i = 0; i < shots.size(); i++) {\n            var shot = shots.get(i);\n            if (progress != null) {\n                progress.report(i, total, "???????? " + shot.getShotNumber() + "?" + (i + 1) + "/" + total + "?");\n            }\n            try {\n                results.add(generateShotVideoInternal(projectId, config, shot, apiKeyHeader));\n            } catch (IllegalStateException ignored) {\n                // ??????????????\n            }\n            if (progress != null) {\n                progress.report(i + 1, total, "??????? " + shot.getShotNumber() + "?" + (i + 1) + "/" + total + "?");\n            }\n        }\n        if (results.isEmpty()) {\n            throw new IllegalStateException("??????????????????????????????????");\n        }',
        'throw new IllegalStateException("没有待生成视频的镜头");\n        }\n\n        // 批量前先用 AI 规划角色/场景/道具绑定\n        shotAssetPlanner.planEpisodeShots(projectId, episodeId, apiKeyHeader);\n\n        var total = shots.size();\n        var results = new ArrayList<ShotResponse>();\n        // 每个镜头独立事务，避免一镜失败整批 rollback-only\n        for (int i = 0; i < shots.size(); i++) {\n            var shot = shots.get(i);\n            if (progress != null) {\n                progress.report(i, total, "正在生成镜头 " + shot.getShotNumber() + "（" + (i + 1) + "/" + total + "）");\n            }\n            try {\n                results.add(self.generateShotVideoInNewTx(projectId, config, shot.getId(), apiKeyHeader));\n            } catch (Exception ex) {\n                if (progress != null) {\n                    progress.report(i + 1, total,\n                            "镜头 " + shot.getShotNumber() + " 失败: "\n                                    + (ex.getMessage() != null ? ex.getMessage() : "unknown"));\n                }\n            }\n            if (progress != null && results.stream().anyMatch(r -> r.shotNumber() == shot.getShotNumber())) {\n                progress.report(i + 1, total, "已完成镜头 " + shot.getShotNumber() + "（" + (i + 1) + "/" + total + "）");\n            }\n        }\n        if (results.isEmpty()) {\n            throw new IllegalStateException("全部镜头视频生成失败，请检查资产设计图与方舟配置后重试");\n        }',
    ),
    (
        '        var assetRefs = resolveShotAssetVideoReferences(shot, projectAssets);\n        if (assetRefs.urls().isEmpty()) {\n            throw new IllegalStateException(\n                    "?? " + shot.getShotNumber()\n                            + " ??????????????/??/???????????????????");\n        }',
        '        // 镜头绑定资产自动提升到全局资产库\n        promoteShotAssetsToGlobal(projectId, shot, projectAssets);\n        projectAssets = assetRepository.findByProjectIdOrderBySortOrderAscNameAsc(projectId);\n\n        var assetRefs = resolveShotAssetVideoReferences(shot, projectAssets);\n        if (assetRefs.urls().isEmpty()) {\n            throw new IllegalStateException(\n                    "镜头 " + shot.getShotNumber()\n                            + " 缺少可用参考图：请先为关联角色/场景/道具生成设计图");\n        }',
    ),
    (
        '                        prompt,\n                        5,\n                        ratio,\n                        resolveVideoQuality(config),\n                        "reference-to-video",\n                        assetRefs.urls().getFirst(),\n                        null,\n                        assetRefs.urls(),\n                        voiceRefs.urls().isEmpty() ? null : voiceRefs.urls()),',
        '                        prompt,\n                        resolveShotDurationSeconds(shot),\n                        ratio,\n                        "480p",\n                        "reference-to-video",\n                        assetRefs.urls().getFirst(),\n                        resolvePreviousShotVideoUrl(shot),\n                        assetRefs.urls(),\n                        voiceRefs.urls().isEmpty() ? null : voiceRefs.urls()),',
    ),
    (
        'throw new IllegalStateException("???????? JSON");',
        'throw new IllegalStateException("剧集缺少剧本 JSON");',
    ),
    (
        '// ??????????????????',
        '// 重新解析前清理旧镜头',
    ),
    (
        'throw new IllegalStateException("?? JSON ??? scenes[].shots ??shots ??");',
        'throw new IllegalStateException("剧本 JSON 需包含 scenes[].shots 或 shots 数组");',
    ),
    (
        'throw new IllegalStateException("????? JSON ?????????");',
        'throw new IllegalStateException("未能从剧本 JSON 解析出任何镜头");',
    ),
    (
        'throw new IllegalStateException("?????????");',
        'throw new IllegalStateException("没有待生成设计图的资产");',
    ),
    (
        '.orElseThrow(() -> new ResourceNotFoundException("?????? " + assetId));',
        '.orElseThrow(() -> new ResourceNotFoundException("资产不存在: " + assetId));',
    ),
    (
        '.orElseThrow(() -> new ResourceNotFoundException("?????? " + episodeId));',
        '.orElseThrow(() -> new ResourceNotFoundException("剧集不存在: " + episodeId));',
    ),
    (
        '.orElseThrow(() -> new ResourceNotFoundException("?????? " + shotId));',
        '.orElseThrow(() -> new ResourceNotFoundException("镜头不存在: " + shotId));',
    ),
]

# Fix remaining progress messages for asset designs / storyboards if corrupted
more = [
    ('progress.report(i, total, "??????????" + asset.getName());',
     'progress.report(i, total, "正在生成设计图：" + asset.getName());'),
    ('progress.report(i + 1, total, "??????" + asset.getName());',
     'progress.report(i + 1, total, "已完成设计图：" + asset.getName());'),
]

count = 0
for old, new in replacements + more:
    if old in text:
        text = text.replace(old, new)
        count += 1
    else:
        print("MISS:", old[:80].replace("\n", "\\n"))

path.write_text(text, encoding="utf-8")
print(f"applied={count}")
