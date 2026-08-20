import type { Edge, Node } from "@xyflow/react";
import type { CanvasNodeData, CanvasNodeType } from "@dreamreel/shared-types";

export interface LinkedNode {
  id: string;
  label: string;
  nodeType: CanvasNodeType;
  data: CanvasNodeData;
}

export interface UpstreamContext {
  texts: LinkedNode[];
  scripts: LinkedNode[];
  images: LinkedNode[];
  videos: LinkedNode[];
  /** 合并后的参考提示词（剧本 → 分镜 → 当前） */
  mergedPrompt: string;
  /** 最近上游分镜图 URL */
  referenceImageUrl?: string;
  /** 推断的生成模式 */
  videoMode: "text-to-video" | "image-to-video";
}

function asNodeData(node: Node): CanvasNodeData {
  return node.data as unknown as CanvasNodeData;
}

function textFromNode(data: CanvasNodeData): string {
  const config = data.config ?? {};
  return String(config.content ?? config.prompt ?? config.text ?? "").trim();
}

/** 获取直接上游节点（连入当前节点的 source） */
export function getDirectUpstream(nodeId: string, nodes: Node[], edges: Edge[]): LinkedNode[] {
  const upstreamIds = edges.filter((e) => e.target === nodeId).map((e) => e.source);
  return nodes
    .filter((n) => upstreamIds.includes(n.id))
    .map((n) => {
      const data = asNodeData(n);
      return { id: n.id, label: data.label, nodeType: data.nodeType, data };
    });
}

/** 递归获取所有上游节点（用于完整链路） */
export function getAllUpstream(nodeId: string, nodes: Node[], edges: Edge[]): LinkedNode[] {
  const visited = new Set<string>();
  const result: LinkedNode[] = [];

  function walk(id: string) {
    for (const linked of getDirectUpstream(id, nodes, edges)) {
      if (visited.has(linked.id)) continue;
      visited.add(linked.id);
      result.push(linked);
      walk(linked.id);
    }
  }

  walk(nodeId);
  return result;
}

/** 解析当前节点的上游上下文 */
export function resolveUpstreamContext(
  nodeId: string,
  nodes: Node[],
  edges: Edge[],
): UpstreamContext {
  const upstream = getAllUpstream(nodeId, nodes, edges);
  const texts = upstream.filter((n) => n.nodeType === "text");
  const scripts = upstream.filter((n) => n.nodeType === "script");
  const images = upstream.filter((n) => n.nodeType === "image");
  const videos = upstream.filter((n) => n.nodeType === "video");

  const promptParts: string[] = [];
  for (const t of texts) {
    const content = textFromNode(t.data);
    if (content) promptParts.push(content);
  }
  for (const s of scripts) {
    const content = textFromNode(s.data);
    if (content) promptParts.push(content);
  }

  // 直接上游分镜图优先
  const directImages = getDirectUpstream(nodeId, nodes, edges).filter((n) => n.nodeType === "image");
  const imageCandidates = directImages.length > 0 ? directImages : images;
  const referenceImageUrl = imageCandidates
    .map((n) => n.data.outputUrl ?? String(n.data.config?.imageUrl ?? ""))
    .find((url) => url && url.length > 0);

  const videoMode = referenceImageUrl ? "image-to-video" : "text-to-video";

  return {
    texts,
    scripts,
    images,
    videos,
    mergedPrompt: promptParts.join("\n\n"),
    referenceImageUrl: referenceImageUrl || undefined,
    videoMode,
  };
}

export const I2V_MODELS = [
  "doubao-seedance-2-5-260628",
  "doubao-seedance-2-0-260128",
  "doubao-seedance-2-0-fast-260128",
  "wan/2-6-image-to-video",
  "kling-2.6/image-to-video",
  "bytedance/v1-pro-fast-image-to-video",
  "bytedance/seedance-2.5",
  "bytedance/seedance-2",
  "happyhorse/reference-to-video",
];

export const T2V_MODELS = [
  "doubao-seedance-2-5-260628",
  "doubao-seedance-2-0-260128",
  "doubao-seedance-2-0-fast-260128",
  "bytedance/seedance-2.5",
  "bytedance/seedance-2",
  "bytedance/seedance-2-fast",
  "kling-3.0/video",
  "wan/2-6-text-to-video",
  "happyhorse/text-to-video",
];

export function pickModelsForMode(mode: "text-to-video" | "image-to-video", allModels: string[]) {
  const preferred = mode === "image-to-video" ? I2V_MODELS : T2V_MODELS;
  const matched = preferred.filter((id) => allModels.includes(id));
  if (matched.length > 0) return matched;
  if (mode === "image-to-video") {
    return allModels.filter((id) => id.includes("image-to-video") || id.includes("i2v"));
  }
  return allModels.filter((id) => id.includes("text-to-video") || id.includes("t2v")).slice(0, 10);
}
