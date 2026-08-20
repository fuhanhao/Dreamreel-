"use client";

import {
  Background,
  Controls,
  MiniMap,
  ReactFlow,
  ReactFlowProvider,
  addEdge,
  useEdgesState,
  useNodesState,
  useReactFlow,
  type Connection,
  type Edge,
  type Node,
} from "@xyflow/react";
import "@xyflow/react/dist/style.css";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useT } from "@/i18n/locale-provider";
import { CanvasToolbar } from "./canvas-toolbar";
import { DramaNode } from "./drama-node";
import { NodeInspector } from "./node-inspector";
import type { CanvasNodeData, CanvasNodeType } from "@dreamreel/shared-types";
import { saveProjectCanvas } from "@/lib/api";
import {
  CANVAS_SYNC_ALL,
  isDefaultOrEmptyCanvas,
  listCanvasSyncEpisodes,
  loadCanvasFromDramaForge,
  type CanvasSyncEpisodeOption,
} from "@/lib/dramaforge-to-canvas";

const nodeTypes = {
  drama: DramaNode,
};

let nodeId = 1000;

function createNode(type: CanvasNodeType, position: { x: number; y: number }, labels: Record<CanvasNodeType, string>): Node {
  nodeId += 1;
  return {
    id: `${type}-${nodeId}`,
    type: "drama",
    position,
    data: { label: labels[type], nodeType: type, status: "idle" },
  };
}

type SaveStatus = "saved" | "saving" | "error" | "dirty";

interface CanvasEditorProps {
  projectId: string;
  projectName: string;
  initialNodes: unknown[];
  initialEdges: unknown[];
  autoSyncFromPipeline?: boolean;
  onBackToStoryboard?: () => void;
}

function CanvasEditorInner({
  projectId,
  projectName,
  initialNodes,
  initialEdges,
  autoSyncFromPipeline = true,
  onBackToStoryboard,
}: CanvasEditorProps) {
  const [nodes, setNodes, onNodesChange] = useNodesState(initialNodes as Node[]);
  const [edges, setEdges, onEdgesChange] = useEdgesState(initialEdges as Edge[]);
  const [selectedNodeId, setSelectedNodeId] = useState<string | null>(null);
  const [saveStatus, setSaveStatus] = useState<SaveStatus>("saved");
  const [syncing, setSyncing] = useState(false);
  const [syncError, setSyncError] = useState<string | null>(null);
  const [episodes, setEpisodes] = useState<CanvasSyncEpisodeOption[]>([]);
  const [syncEpisodeId, setSyncEpisodeId] = useState("");
  const saveTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const isFirstRender = useRef(true);
  const skipNextPersist = useRef(false);
  const didAutoSync = useRef(false);
  const { fitView } = useReactFlow();
  const t = useT();

  const nodeLabels = useMemo<Record<CanvasNodeType, string>>(
    () => ({
      text: t("dramaforge.canvas.nodeText"),
      script: t("dramaforge.canvas.nodeScript"),
      image: t("dramaforge.canvas.nodeImage"),
      video: t("dramaforge.canvas.nodeVideo"),
      audio: t("dramaforge.canvas.nodeAudio"),
      compose: t("dramaforge.canvas.nodeCompose"),
    }),
    [t],
  );

  const selectedNode = nodes.find((n) => n.id === selectedNodeId);
  const selectedData = selectedNode?.data as CanvasNodeData | undefined;
  const showMiniMap = nodes.length <= 60;

  const persistCanvas = useCallback(async () => {
    setSaveStatus("saving");
    try {
      await saveProjectCanvas(projectId, { nodes, edges });
      setSaveStatus("saved");
    } catch {
      setSaveStatus("error");
    }
  }, [projectId, nodes, edges]);

  useEffect(() => {
    if (isFirstRender.current) {
      isFirstRender.current = false;
      return;
    }
    if (skipNextPersist.current) {
      skipNextPersist.current = false;
      return;
    }

    setSaveStatus("dirty");
    if (saveTimerRef.current) {
      clearTimeout(saveTimerRef.current);
    }

    saveTimerRef.current = setTimeout(() => {
      void persistCanvas();
    }, 1500);

    return () => {
      if (saveTimerRef.current) {
        clearTimeout(saveTimerRef.current);
      }
    };
  }, [nodes, edges, persistCanvas]);

  useEffect(() => {
    let cancelled = false;
    void listCanvasSyncEpisodes(projectId)
      .then((list) => {
        if (cancelled) return;
        setEpisodes(list);
        setSyncEpisodeId((prev) => prev || list[0]?.id || "");
      })
      .catch(() => {
        /* 分集列表失败不影响画布 */
      });
    return () => {
      cancelled = true;
    };
  }, [projectId]);

  const applyGraph = useCallback(
    async (nextNodes: Node[], nextEdges: Edge[], persist = true) => {
      skipNextPersist.current = true;
      setNodes(nextNodes);
      setEdges(nextEdges);
      setSelectedNodeId(null);
      requestAnimationFrame(() => {
        fitView({ padding: 0.2, duration: 200 });
      });
      if (persist) {
        setSaveStatus("saving");
        try {
          await saveProjectCanvas(projectId, { nodes: nextNodes, edges: nextEdges });
          setSaveStatus("saved");
        } catch {
          setSaveStatus("error");
        }
      }
    },
    [fitView, projectId, setEdges, setNodes],
  );

  const syncFromPipeline = useCallback(
    async (episodeId?: string) => {
      const target = episodeId || syncEpisodeId || undefined;
      setSyncing(true);
      setSyncError(null);
      try {
        const graph = await loadCanvasFromDramaForge(projectId, {
          episodeId: target || undefined,
        });
        if (graph.syncedEpisodeId) {
          setSyncEpisodeId(graph.syncedEpisodeId);
        }
        await applyGraph(graph.nodes as Node[], graph.edges as Edge[], true);
      } catch (err) {
        setSyncError(err instanceof Error ? err.message : t("dramaforge.canvas.syncFailed"));
      } finally {
        setSyncing(false);
      }
    },
    [applyGraph, projectId, syncEpisodeId, t],
  );

  useEffect(() => {
    if (!autoSyncFromPipeline || didAutoSync.current) return;
    didAutoSync.current = true;
    if (isDefaultOrEmptyCanvas(initialNodes)) {
      const timer = window.setTimeout(() => {
        void syncFromPipeline();
      }, 0);
      return () => window.clearTimeout(timer);
    } else if (initialNodes.length > 80) {
      // 已有超大图：提示可按集重同步，不自动全量刷新
      const timer = window.setTimeout(() => {
        setSyncError(t("dramaforge.canvas.largeCanvasHint"));
      }, 0);
      return () => window.clearTimeout(timer);
    }
  }, [autoSyncFromPipeline, initialNodes, syncFromPipeline, t]);

  const updateNodeData = useCallback(
    (id: string, patch: Partial<CanvasNodeData>) => {
      setNodes((nds) =>
        nds.map((node) =>
          node.id === id
            ? { ...node, data: { ...(node.data as unknown as CanvasNodeData), ...patch } }
            : node,
        ),
      );
    },
    [setNodes],
  );

  const onConnect = useCallback(
    (connection: Connection) => {
      setEdges((eds) =>
        addEdge(
          {
            ...connection,
            animated: false,
            style: { stroke: "rgba(15,118,110,0.42)", strokeWidth: 1.5 },
          },
          eds,
        ),
      );
      if (connection.target) {
        setSelectedNodeId(connection.target);
      }
    },
    [setEdges],
  );

  const onAddNode = useCallback(
    (type: CanvasNodeType) => {
      setNodes((nds) => [
        ...nds,
        createNode(type, { x: 120 + nds.length * 24, y: 80 + nds.length * 24 }, nodeLabels),
      ]);
    },
    [nodeLabels, setNodes],
  );

  return (
    <div className="flex h-full w-full flex-col bg-transparent text-foreground">
      <CanvasToolbar
        projectName={projectName}
        saveStatus={saveStatus}
        onAddNode={onAddNode}
        nodeCount={nodes.length}
        edgeCount={edges.length}
        syncing={syncing}
        episodes={episodes}
        syncEpisodeId={syncEpisodeId}
        onSyncEpisodeChange={setSyncEpisodeId}
        onSyncFromPipeline={() => void syncFromPipeline()}
        onBackToStoryboard={onBackToStoryboard}
      />
      {syncError && (
        <div className="shrink-0 border-b border-amber-300 bg-amber-50 px-4 py-1.5 text-xs text-amber-800">
          {syncError}
          {syncError === t("dramaforge.canvas.largeCanvasHint") && (
            <button
              type="button"
              className="ml-2 underline"
              onClick={() => {
                setSyncError(null);
                void syncFromPipeline(episodes[0]?.id || syncEpisodeId);
              }}
            >
              {t("dramaforge.canvas.syncFirstEpisodeOnly")}
            </button>
          )}
        </div>
      )}
      <div className="relative flex flex-1 min-h-0 min-w-0">
        <div className="relative h-full w-full min-h-0">
          <ReactFlow
            nodes={nodes}
            edges={edges}
            onNodesChange={onNodesChange}
            onEdgesChange={onEdgesChange}
            onConnect={onConnect}
            onNodeClick={(_, node) => setSelectedNodeId(node.id)}
            onPaneClick={() => setSelectedNodeId(null)}
            nodeTypes={nodeTypes}
            fitView
            onlyRenderVisibleElements
            minZoom={0.15}
            maxZoom={1.5}
            defaultEdgeOptions={{
              animated: false,
              style: { stroke: "rgba(15,118,110,0.42)", strokeWidth: 1.5 },
            }}
            className="h-full w-full bg-[#eef6fb]"
            proOptions={{ hideAttribution: true }}
          >
            <Background gap={28} size={1} color="#bfd5e3" />
            <Controls className="!border-white/80 !bg-white/90 !text-slate-700 !shadow-[0_10px_30px_rgba(15,55,95,.14)]" />
            {showMiniMap && (
              <MiniMap
                pannable
                zoomable
                className="!border-white/80 !bg-white/90 !shadow-[0_10px_30px_rgba(15,55,95,.12)]"
                maskColor="rgba(203, 222, 235, 0.58)"
                nodeColor={(node) => {
                  const type = (node.data as { nodeType?: CanvasNodeType } | undefined)?.nodeType;
                  const colors: Record<CanvasNodeType, string> = {
                    text: "#7c3aed",
                    script: "#0891b2",
                    image: "#ec4899",
                    video: "#f59e0b",
                    audio: "#8b5cf6",
                    compose: "#475569",
                  };
                  return type ? colors[type] : "#64748b";
                }}
              />
            )}
          </ReactFlow>
          {syncing && (
            <div className="pointer-events-none absolute inset-0 flex items-center justify-center bg-slate-900/15 backdrop-blur-[2px]">
              <div className="rounded-xl border border-white/80 bg-white/95 px-4 py-2 text-sm text-slate-700 shadow-xl">
                {syncEpisodeId === CANVAS_SYNC_ALL
                  ? t("dramaforge.canvas.syncingAllEpisodes")
                  : t("dramaforge.canvas.syncingEpisode")}
              </div>
            </div>
          )}
        </div>

        {selectedNodeId && selectedData && (
          <NodeInspector
            projectId={projectId}
            nodeId={selectedNodeId}
            nodeData={selectedData}
            nodes={nodes}
            edges={edges}
            onUpdate={(patch) => updateNodeData(selectedNodeId, patch)}
          />
        )}
      </div>
    </div>
  );
}

export function CanvasEditor(props: CanvasEditorProps) {
  return (
    <ReactFlowProvider>
      <CanvasEditorInner {...props} />
    </ReactFlowProvider>
  );
}
