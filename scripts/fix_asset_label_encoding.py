#!/usr/bin/env python3
from pathlib import Path

TARGET = Path(__file__).resolve().parents[1] / (
    "services/api/src/main/java/com/dreamreel/api/dramaforge/service/DramaForgeService.java"
)

BLOCK_OLD = '''    /** ?????????????? ? ?? ? ????? 5 ??Seedance ?????? */
    private ShotAssetVideoRefs resolveShotAssetVideoReferences(DramaForgeShot shot, List<DramaForgeAsset> projectAssets) {
        var urls = new ArrayList<String>();
        var labels = new ArrayList<String>();
        var seen = new java.util.HashSet<String>();
        var desc = shot.getDescription() != null ? shot.getDescription() : "";
        var dialogue = shot.getDialogue() != null ? shot.getDialogue() : "";

        for (var name : readStringList(shot.getCharacterRefsJson())) {
            if (!isCharacterVisibleInShot(name, desc, dialogue)) {
                continue;
            }
            addAssetImageRef(name, DramaForgeAssetType.CHARACTER, projectAssets, urls, labels, seen, "??");
        }
        if (shot.getSceneRef() != null && !shot.getSceneRef().isBlank()) {
            addAssetImageRef(shot.getSceneRef(), DramaForgeAssetType.SCENE, projectAssets, urls, labels, seen, "??");
        }
        for (var name : readStringList(shot.getPropRefsJson())) {
            addAssetImageRef(name, DramaForgeAssetType.PROP, projectAssets, urls, labels, seen, "??");
        }

        if (urls.isEmpty()) {
            var text = (desc + dialogue).toLowerCase(Locale.ROOT);
            for (var asset : projectAssets) {
                if (asset.getReferenceImageUrl() == null || asset.getReferenceImageUrl().isBlank()) {
                    continue;
                }
                if (text.contains(asset.getName().toLowerCase(Locale.ROOT))) {
                    if (asset.getType() == DramaForgeAssetType.CHARACTER
                            && !isCharacterVisibleInShot(asset.getName(), desc, dialogue)) {
                        continue;
                    }
                    addAssetImageRef(asset.getName(), asset.getType(), projectAssets, urls, labels, seen,
                            asset.getType() == DramaForgeAssetType.CHARACTER ? "??"
                                    : asset.getType() == DramaForgeAssetType.SCENE ? "??" : "??");
                }
            }
        }

        return trimVideoReferences(urls, labels, 5);
    }

    /** ?????????????????????????? @Image ??????? */
    private static boolean isCharacterVisibleInShot(String name, String description, String dialogue) {'''

BLOCK_NEW = '''    /** 解析镜头视频参考图：出镜角色 → 场景 → 道具，最多 5 张（Seedance 建议少而精） */
    private ShotAssetVideoRefs resolveShotAssetVideoReferences(DramaForgeShot shot, List<DramaForgeAsset> projectAssets) {
        var urls = new ArrayList<String>();
        var labels = new ArrayList<String>();
        var seen = new java.util.HashSet<String>();
        var desc = shot.getDescription() != null ? shot.getDescription() : "";
        var dialogue = shot.getDialogue() != null ? shot.getDialogue() : "";

        for (var name : readStringList(shot.getCharacterRefsJson())) {
            if (!isCharacterVisibleInShot(name, desc, dialogue)) {
                continue;
            }
            addAssetImageRef(name, DramaForgeAssetType.CHARACTER, projectAssets, urls, labels, seen, "角色");
        }
        if (shot.getSceneRef() != null && !shot.getSceneRef().isBlank()) {
            addAssetImageRef(shot.getSceneRef(), DramaForgeAssetType.SCENE, projectAssets, urls, labels, seen, "场景");
        }
        for (var name : readStringList(shot.getPropRefsJson())) {
            addAssetImageRef(name, DramaForgeAssetType.PROP, projectAssets, urls, labels, seen, "道具");
        }

        if (urls.isEmpty()) {
            var text = (desc + dialogue).toLowerCase(Locale.ROOT);
            for (var asset : projectAssets) {
                if (asset.getReferenceImageUrl() == null || asset.getReferenceImageUrl().isBlank()) {
                    continue;
                }
                if (text.contains(asset.getName().toLowerCase(Locale.ROOT))) {
                    if (asset.getType() == DramaForgeAssetType.CHARACTER
                            && !isCharacterVisibleInShot(asset.getName(), desc, dialogue)) {
                        continue;
                    }
                    addAssetImageRef(asset.getName(), asset.getType(), projectAssets, urls, labels, seen,
                            asset.getType() == DramaForgeAssetType.CHARACTER ? "角色"
                                    : asset.getType() == DramaForgeAssetType.SCENE ? "场景" : "道具");
                }
            }
        }

        return trimVideoReferences(urls, labels, 5);
    }

    /** 仅对白出镜、画面描述未提及的角色不传参考图（避免占用 @Image 槽位稀释身份） */
    private static boolean isCharacterVisibleInShot(String name, String description, String dialogue) {'''

ERROR_OLD = '''                    "?? " + shot.getShotNumber()
                            + " ???????????????/??/??????????????????????");'''
ERROR_NEW = '''                    "镜头 " + shot.getShotNumber()
                            + " 缺少可用参考图：请先生成角色/场景/道具设计图，并确认镜头已规划出场资产。");'''


def main() -> None:
    text = TARGET.read_text(encoding="utf-8")
    if BLOCK_OLD not in text:
        raise SystemExit("block not found")
    text = text.replace(BLOCK_OLD, BLOCK_NEW)
    text = text.replace(ERROR_OLD, ERROR_NEW)
    text = text.replace(
        '                    "?? " + shot.getShotNumber()\n'
        '                            + " ??????????????/??/???????????????????");',
        '                    "镜头 " + shot.getShotNumber()\n'
        '                            + " 缺少可用参考图：请先生成角色/场景/道具设计图，并确认镜头已规划出场资产。");',
    )
    text = text.replace('l.startsWith("??")', 'l.startsWith("场景")', 1)
    TARGET.write_text(text, encoding="utf-8", newline="\n")
    print("fixed")


if __name__ == "__main__":
    main()
