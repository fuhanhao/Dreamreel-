import type { Edge, Node } from "@xyflow/react";
import type {
  CanvasData,
  CanvasNodeData,
  DramaForgeAsset,
  DramaForgeEpisode,
  DramaForgeShot,
} from "@dreamreel/shared-types";
import {
  fetchDramaForgeAssets,
  fetchDramaForgeEpisodes,
  fetchDramaForgeShots,
} from "@/modules/dramaforge/api";

const COL = {
  asset: 40,
  frame: 360,
  prompt: 700,
  video: 1060,
} as const;

const ROW_GAP = 280;
const ASSET_GAP = 200;

/** 默认只同步一集，避免一次塞满画布 */
export const CANVAS_SYNC_ALL = "__all__";

export type CanvasSyncEpisodeOption = {
  id: string;
  label: string;
  shotCount?: number;
};

function dramaNode(
  id: string,
  position: { x: number; y: number },
  data: CanvasNodeData,
): Node {
  return {
    id,
    type: "drama",
    position,
    data: data as unknown as Record<string, unknown>,
  };
}

function edge(id: string, source: string, target: string): Edge {
  return {
    id,
    source,
    target,
    animated: false,
    style: { stroke: "rgba(255,255,255,0.22)", strokeWidth: 1.25 },
  };
}

function shotImageUrl(shot: DramaForgeShot): string | null {
  return (
    shot.storyboardUrl ||
    shot.firstFrameUrl ||
    shot.imageBindings?.find((b) => b.url)?.url ||
    null
  );
}

function collectReferencedAssetIds(
  shots: DramaForgeShot[],
  assetIdByName: Map<string, string>,
): Set<string> {
  const ids = new Set<string>();
  for (const shot of shots) {
    const names = [
      ...(shot.characterRefs ?? []),
      ...(shot.sceneRef ? [shot.sceneRef] : []),
      ...(shot.propRefs ?? []),
      ...(shot.imageBindings ?? []).map((b) => b.label),
    ];
    for (const name of names) {
      const id = assetIdByName.get(name.trim().toLowerCase());
      if (id) ids.add(id);
    }
  }
  return ids;
}

/**
 * 按 DramaForge 流水线结果生成画布：
 * 左：相关资产 → 中：镜头参考/提示词 → 右：镜头视频
 */
export function buildCanvasFromDramaForge(input: {
  assets: DramaForgeAsset[];
  episodes: DramaForgeEpisode[];
  shotsByEpisode: Record<string, DramaForgeShot[]>;
}): CanvasData {
  const nodes: Node[] = [];
  const edges: Edge[] = [];

  const allShots = input.episodes.flatMap((ep) => input.shotsByEpisode[ep.id] ?? []);
  const assetsWithImage = input.assets.filter((a) => a.referenceImageUrl);
  const assetIdByName = new Map(
    assetsWithImage.map((a) => [a.name.trim().toLowerCase(), a.id] as const),
  );
  const referencedIds = collectReferencedAssetIds(allShots, assetIdByName);
  const visibleAssets =
    referencedIds.size > 0
      ? assetsWithImage.filter((a) => referencedIds.has(a.id))
      : assetsWithImage.slice(0, 12);

  visibleAssets.forEach((asset, index) => {
    const id = `asset-${asset.id}`;
    nodes.push(
      dramaNode(id, { x: COL.asset, y: 40 + index * ASSET_GAP }, {
        label: asset.name,
        nodeType: "image",
        status: "success",
        outputUrl: asset.referenceImageUrl ?? undefined,
        config: {
          prompt: asset.designPrompt ?? asset.description ?? "",
          source: "dramaforge-asset",
          assetId: asset.id,
          assetType: asset.type,
          imageUrl: asset.referenceImageUrl,
        },
      }),
    );
  });

  let row = 0;
  for (const episode of input.episodes) {
    const shots = input.shotsByEpisode[episode.id] ?? [];
    for (const shot of shots) {
      const y = 40 + row * ROW_GAP;
      const frameId = `shot-frame-${shot.id}`;
      const promptId = `shot-prompt-${shot.id}`;
      const videoId = `shot-video-${shot.id}`;
      const frameUrl = shotImageUrl(shot);
      const promptText =
        (shot.videoPrompt || shot.description || "").trim() ||
        `第${episode.episodeNumber}集 · 镜头 ${shot.shotNumber}`;

      nodes.push(
        dramaNode(frameId, { x: COL.frame, y }, {
          label: `E${episode.episodeNumber}·镜${shot.shotNumber} 参考`,
          nodeType: "image",
          status: frameUrl ? "success" : "idle",
          outputUrl: frameUrl ?? undefined,
          config: {
            prompt: promptText,
            source: "dramaforge-shot",
            shotId: shot.id,
            episodeId: shot.episodeId,
            imageUrl: frameUrl,
            ratio: "16:9",
          },
        }),
      );

      nodes.push(
        dramaNode(promptId, { x: COL.prompt, y }, {
          label: `E${episode.episodeNumber}·镜${shot.shotNumber} 提示词`,
          nodeType: "script",
          status: promptText ? "success" : "idle",
          config: {
            content: promptText,
            source: "dramaforge-shot",
            shotId: shot.id,
            episodeId: shot.episodeId,
          },
        }),
      );

      nodes.push(
        dramaNode(videoId, { x: COL.video, y }, {
          label: `E${episode.episodeNumber}·镜${shot.shotNumber} 视频`,
          nodeType: "video",
          status: shot.videoUrl ? "success" : shot.videoJobId ? "running" : "idle",
          outputUrl: shot.videoUrl ?? undefined,
          config: {
            prompt: promptText,
            source: "dramaforge-shot",
            shotId: shot.id,
            episodeId: shot.episodeId,
            seconds: shot.durationSeconds ?? 5,
            mode: "reference-to-video",
          },
        }),
      );

      edges.push(edge(`e-${frameId}-${promptId}`, frameId, promptId));
      edges.push(edge(`e-${promptId}-${videoId}`, promptId, videoId));

      const linkedAssetIds = new Set<string>();
      const refNames = [
        ...(shot.characterRefs ?? []),
        ...(shot.sceneRef ? [shot.sceneRef] : []),
        ...(shot.propRefs ?? []),
      ];
      for (const name of refNames) {
        const assetId = assetIdByName.get(name.trim().toLowerCase());
        if (!assetId || linkedAssetIds.has(assetId)) continue;
        linkedAssetIds.add(assetId);
        edges.push(edge(`e-asset-${assetId}-${frameId}`, `asset-${assetId}`, frameId));
      }
      for (const binding of shot.imageBindings ?? []) {
        const assetId = assetIdByName.get(binding.label.trim().toLowerCase());
        if (!assetId || linkedAssetIds.has(assetId)) continue;
        linkedAssetIds.add(assetId);
        edges.push(edge(`e-asset-${assetId}-${frameId}`, `asset-${assetId}`, frameId));
      }

      row += 1;
    }
  }

  if (nodes.length === 0) {
    nodes.push(
      dramaNode("empty-hint", { x: 200, y: 120 }, {
        label: "暂无流水线产物",
        nodeType: "text",
        status: "idle",
        config: {
          content:
            "请先在流水线工作台完成资产定妆与镜头视频，再点击「从流水线同步」生成工作流画布。",
        },
      }),
    );
  }

  return { nodes, edges };
}

export function isDefaultOrEmptyCanvas(nodes: unknown[]): boolean {
  if (!Array.isArray(nodes) || nodes.length === 0) return true;
  const ids = nodes
    .map((n) => (n && typeof n === "object" && "id" in n ? String((n as { id: unknown }).id) : ""))
    .filter(Boolean);
  if (ids.length <= 6 && ids.every((id) => /^(text|script|image|video|audio|compose)-/.test(id))) {
    const hasDramaforge = nodes.some((n) => {
      const data = (n as { data?: { config?: { source?: string } } })?.data;
      return data?.config?.source === "dramaforge-shot" || data?.config?.source === "dramaforge-asset";
    });
    return !hasDramaforge;
  }
  return false;
}

export async function listCanvasSyncEpisodes(projectId: string): Promise<CanvasSyncEpisodeOption[]> {
  const episodesRes = await fetchDramaForgeEpisodes(projectId);
  const episodes = episodesRes.data ?? [];
  return episodes.map((ep) => ({
    id: ep.id,
    label: `第 ${ep.episodeNumber} 集${ep.title ? ` · ${ep.title}` : ""}`,
    shotCount: ep.shotCount,
  }));
}

/**
 * 拉取流水线并生成画布。
 * - episodeId 缺省：只同步第一集（性能优先）
 * - episodeId = CANVAS_SYNC_ALL：同步全部集
 */
export async function loadCanvasFromDramaForge(
  projectId: string,
  options?: { episodeId?: string },
): Promise<CanvasData & { syncedEpisodeId: string }> {
  const [assetsRes, episodesRes] = await Promise.all([
    fetchDramaForgeAssets(projectId),
    fetchDramaForgeEpisodes(projectId),
  ]);
  const assets = assetsRes.data ?? [];
  const allEpisodes = episodesRes.data ?? [];

  const requested = options?.episodeId;
  const episodes =
    requested === CANVAS_SYNC_ALL
      ? allEpisodes
      : allEpisodes.filter((ep) => ep.id === (requested || allEpisodes[0]?.id)).slice(0, 1);

  const shotsByEpisode: Record<string, DramaForgeShot[]> = {};
  await Promise.all(
    episodes.map(async (ep) => {
      const shotsRes = await fetchDramaForgeShots(projectId, ep.id);
      shotsByEpisode[ep.id] = shotsRes.data ?? [];
    }),
  );

  const graph = buildCanvasFromDramaForge({ assets, episodes, shotsByEpisode });
  return {
    ...graph,
    syncedEpisodeId: requested === CANVAS_SYNC_ALL ? CANVAS_SYNC_ALL : episodes[0]?.id ?? "",
  };
}
