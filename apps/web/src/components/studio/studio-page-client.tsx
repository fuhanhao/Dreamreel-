"use client";

import { useEffect, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { DfInShellError, DfPageLoading } from "@/components/shell/df-in-shell-state";
import { useAuth } from "@/components/auth/auth-provider";
import { useT } from "@/i18n/locale-provider";
import { StudioWorkspace } from "@/modules/dramaforge/studio-workspace";
import { fetchProject, parseCanvasData } from "@/lib/api";

interface StudioPageClientProps {
  projectId: string;
}

export function StudioPageClient({ projectId }: StudioPageClientProps) {
  const { user, loading: authLoading } = useAuth();
  const t = useT();
  const router = useRouter();
  const searchParams = useSearchParams();
  const mode = searchParams.get("mode");
  const needsCanvas = mode === "canvas";
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [projectName, setProjectName] = useState("");
  const [initialNodes, setInitialNodes] = useState<unknown[]>([]);
  const [initialEdges, setInitialEdges] = useState<unknown[]>([]);

  useEffect(() => {
    if (authLoading) return;
    if (!user) {
      const redirect = `/studio/${projectId}${mode ? `?mode=${mode}` : ""}`;
      router.replace(`/login?redirect=${encodeURIComponent(redirect)}`);
      return;
    }

    let cancelled = false;
    const timer = window.setTimeout(() => {
      setLoading(true);
      setError(null);
    }, 0);

    // DramaForge 首屏不需要 canvasData（可达百 KB+），用 summary 只拉元数据。
    fetchProject(projectId, { summary: !needsCanvas })
      .then((res) => {
        if (cancelled) return;
        setProjectName(res.data.name);
        if (needsCanvas) {
          const canvas = parseCanvasData(res.data.canvasData);
          setInitialNodes(canvas.nodes);
          setInitialEdges(canvas.edges);
        } else {
          setInitialNodes([]);
          setInitialEdges([]);
        }
      })
      .catch((err) => {
        if (cancelled) return;
        setError(err instanceof Error ? err.message : t("projects.loadFailed"));
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });

    return () => {
      cancelled = true;
      window.clearTimeout(timer);
    };
  }, [authLoading, user, projectId, mode, needsCanvas, router, t]);

  if (authLoading || loading) {
    return <DfPageLoading message={t("projects.loadingProject")} variant="shell" />;
  }

  if (error) {
    return (
      <DfInShellError
        message={error}
        onRetry={() => router.refresh()}
      />
    );
  }

  return (
    <StudioWorkspace
      projectId={projectId}
      projectName={projectName}
      initialNodes={initialNodes}
      initialEdges={initialEdges}
    />
  );
}
