"use client";

import type { Edge, Node } from "@xyflow/react";
import type { CanvasNodeData } from "@dreamreel/shared-types";
import { resolveUpstreamContext } from "@/lib/canvas-graph";
import { useT } from "@/i18n/locale-provider";
import { ImageNodePanel } from "./image-node-panel";
import { TextNodePanel } from "./text-node-panel";
import { VideoNodePanel } from "./video-node-panel";

interface NodeInspectorProps {
  projectId: string;
  nodeId: string;
  nodeData: CanvasNodeData;
  nodes: Node[];
  edges: Edge[];
  onUpdate: (patch: Partial<CanvasNodeData>) => void;
}

export function NodeInspector({
  projectId,
  nodeId,
  nodeData,
  nodes,
  edges,
  onUpdate,
}: NodeInspectorProps) {
  const t = useT();
  const upstream = resolveUpstreamContext(nodeId, nodes, edges);

  switch (nodeData.nodeType) {
    case "text":
      return (
        <TextNodePanel
          projectId={projectId}
          nodeId={nodeId}
          nodeType="text"
          nodeData={nodeData}
          upstream={upstream}
          onUpdate={onUpdate}
          title={t("dramaforge.canvas.nodeText")}
          placeholder={t("dramaforge.canvas.textNodePlaceholder")}
        />
      );
    case "script":
      return (
        <TextNodePanel
          projectId={projectId}
          nodeId={nodeId}
          nodeType="script"
          nodeData={nodeData}
          upstream={upstream}
          onUpdate={onUpdate}
          title={t("dramaforge.canvas.nodeScript")}
          placeholder={t("dramaforge.canvas.scriptNodePlaceholder")}
        />
      );
    case "image":
      return (
        <ImageNodePanel
          projectId={projectId}
          nodeId={nodeId}
          nodeData={nodeData}
          upstream={upstream}
          onUpdate={onUpdate}
        />
      );
    case "video":
      return (
        <VideoNodePanel
          projectId={projectId}
          nodeId={nodeId}
          nodeData={nodeData}
          upstream={upstream}
          onUpdate={onUpdate}
        />
      );
    default:
      return (
        <aside className="w-80 shrink-0 border-l border-white/70 bg-white/78 p-4 shadow-[-8px_0_28px_rgba(15,55,95,.08)] backdrop-blur-xl">
          <h2 className="text-sm font-semibold text-slate-900">{nodeData.label}</h2>
          <p className="mt-2 text-xs text-slate-500">{t("dramaforge.canvas.panelInDevelopment")}</p>
        </aside>
      );
  }
}
