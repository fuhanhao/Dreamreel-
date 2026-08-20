"use client";

import dynamic from "next/dynamic";
import { useEffect, useState } from "react";
import { useSearchParams } from "next/navigation";
import { useCurrentProject } from "@/components/shell/current-project";
import { DramaForgeWorkspace } from "@/modules/dramaforge/dramaforge-workspace";
import { fetchProject, parseCanvasData } from "@/lib/api";
import { useT } from "@/i18n/locale-provider";
import "@/modules/dramaforge/dramaforge-theme.css";

function LoadingCanvas() {
  const t = useT();
  return (
    <div className="flex h-full items-center justify-center text-sm text-muted-foreground">
      {t("dramaforge.studio.loadingCanvas")}
    </div>
  );
}

const CanvasEditor = dynamic(
  () => import("@/components/canvas/canvas-editor").then((m) => m.CanvasEditor),
  {
    ssr: false,
    loading: () => <LoadingCanvas />,
  },
);

interface StudioWorkspaceProps {
  projectId: string;
  projectName: string;
  initialNodes: unknown[];
  initialEdges: unknown[];
}

type StudioMode = "canvas" | "dramaforge";

export function StudioWorkspace({
  projectId,
  projectName,
  initialNodes,
  initialEdges,
}: StudioWorkspaceProps) {
  const t = useT();
  const searchParams = useSearchParams();
  const initialMode: StudioMode = searchParams.get("mode") === "canvas" ? "canvas" : "dramaforge";
  const [mode, setMode] = useState<StudioMode>(initialMode);
  const [nodes, setNodes] = useState(initialNodes);
  const [edges, setEdges] = useState(initialEdges);
  const [canvasReady, setCanvasReady] = useState(initialNodes.length > 0 || initialMode !== "canvas");
  const { setCurrentProjectId } = useCurrentProject();

  useEffect(() => {
    setCurrentProjectId(projectId);
  }, [projectId, setCurrentProjectId]);

  useEffect(() => {
    if (mode !== "canvas" || canvasReady) return;
    let cancelled = false;
    void fetchProject(projectId)
      .then((res) => {
        if (cancelled) return;
        const canvas = parseCanvasData(res.data.canvasData);
        setNodes(canvas.nodes);
        setEdges(canvas.edges);
        setCanvasReady(true);
      })
      .catch(() => {
        if (!cancelled) setCanvasReady(true);
      });
    return () => {
      cancelled = true;
    };
  }, [mode, canvasReady, projectId]);

  function openCanvas() {
    setCanvasReady(nodes.length > 0);
    setMode("canvas");
  }

  return (
    <div className="flex h-full w-full flex-col overflow-hidden bg-transparent text-foreground">
      <div className="min-h-0 flex-1">
        {mode === "canvas" ? (
          canvasReady ? (
            <CanvasEditor
              projectId={projectId}
              projectName={projectName}
              initialNodes={nodes}
              initialEdges={edges}
              autoSyncFromPipeline
              onBackToStoryboard={() => setMode("dramaforge")}
            />
          ) : (
            <div className="flex h-full items-center justify-center text-sm text-muted-foreground">
              {t("dramaforge.studio.loadingCanvas")}
            </div>
          )
        ) : (
          <div className="dramaforge-theme df-theme h-full">
            <DramaForgeWorkspace
              projectId={projectId}
              projectName={projectName}
              onOpenCanvas={openCanvas}
            />
          </div>
        )}
      </div>
    </div>
  );
}
