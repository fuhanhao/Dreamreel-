"use client";

import type { CanvasNodeType } from "@dreamreel/shared-types";
import { useT } from "@/i18n/locale-provider";
import { DfSelect } from "@/components/ui/df-select";
import { CANVAS_SYNC_ALL, type CanvasSyncEpisodeOption } from "@/lib/dramaforge-to-canvas";

const saveStatusColor: Record<string, string> = {
  saved: "text-[#7c3aed]",
  saving: "text-amber-700",
  dirty: "text-slate-500",
  error: "text-rose-700",
};

interface CanvasToolbarProps {
  projectName: string;
  saveStatus: string;
  onAddNode: (type: CanvasNodeType) => void;
  nodeCount: number;
  edgeCount: number;
  syncing?: boolean;
  episodes?: CanvasSyncEpisodeOption[];
  syncEpisodeId?: string;
  onSyncEpisodeChange?: (episodeId: string) => void;
  onSyncFromPipeline?: () => void;
  onBackToStoryboard?: () => void;
}

export function CanvasToolbar({
  projectName,
  saveStatus,
  onAddNode,
  nodeCount,
  edgeCount,
  syncing,
  episodes = [],
  syncEpisodeId,
  onSyncEpisodeChange,
  onSyncFromPipeline,
  onBackToStoryboard,
}: CanvasToolbarProps) {
  const t = useT();

  const nodeOptions: { type: CanvasNodeType; label: string }[] = [
    { type: "text", label: t("dramaforge.canvas.nodeText") },
    { type: "script", label: t("dramaforge.canvas.nodePrompt") },
    { type: "image", label: t("dramaforge.canvas.nodeImage") },
    { type: "video", label: t("dramaforge.canvas.nodeVideo") },
    { type: "audio", label: t("dramaforge.canvas.nodeAudio") },
    { type: "compose", label: t("dramaforge.canvas.nodeCompose") },
  ];

  const saveStatusText: Record<string, string> = {
    saved: t("dramaforge.canvas.statusSaved"),
    saving: t("dramaforge.canvas.statusSaving"),
    dirty: t("dramaforge.canvas.statusDirty"),
    error: t("dramaforge.canvas.statusError"),
  };
  const episodeOptions = [
    ...episodes.map((ep) => ({
      value: ep.id,
      label: ep.shotCount != null ? t("dramaforge.canvas.episodeWithShots", { episode: ep.label, count: ep.shotCount }) : ep.label,
    })),
    { value: CANVAS_SYNC_ALL, label: t("dramaforge.canvas.allEpisodes") },
  ];

  return (
    <header className="flex h-12 shrink-0 items-center justify-between gap-4 border-b border-white/70 bg-white/78 px-4 shadow-sm backdrop-blur-xl">
      <div className="flex items-center gap-3 min-w-0">
        <h1 className="truncate text-sm font-semibold text-slate-900">{projectName}</h1>
        <span className={`shrink-0 text-[11px] ${saveStatusColor[saveStatus] ?? "text-slate-500"}`}>
          {saveStatusText[saveStatus] ?? saveStatus}
        </span>
      </div>

      <div className="flex items-center rounded-full border border-slate-200 bg-slate-100/80 p-0.5">
        <button
          type="button"
          className="rounded-full border border-[#e5e7eb] bg-white px-3.5 py-1 text-xs font-medium text-[#17131f]"
        >
          {t("dramaforge.canvas.workflow")}
        </button>
        <button
          type="button"
          onClick={onBackToStoryboard}
          className="rounded-full px-3.5 py-1 text-xs text-slate-500 transition hover:bg-white/70 hover:text-slate-900"
        >
          {t("dramaforge.canvas.storyboard")}
        </button>
      </div>

      <div className="flex items-center gap-2 shrink-0">
        <div className="hidden xl:flex items-center gap-1.5">
          {nodeOptions.map((opt) => (
            <button
              key={opt.type}
              type="button"
              onClick={() => onAddNode(opt.type)}
              className="rounded-md border border-slate-200 bg-white px-2 py-1 text-[11px] text-slate-600 transition hover:border-[#7c3aed] hover:bg-[#f4ffd6] hover:text-[#17131f]"
            >
              + {opt.label}
            </button>
          ))}
        </div>
        {episodes.length > 0 && onSyncEpisodeChange && (
          <div className="w-[160px]">
            <DfSelect
              value={syncEpisodeId || episodes[0]?.id || ""}
              onChange={onSyncEpisodeChange}
              options={episodeOptions}
              placeholder={t("dramaforge.canvas.selectEpisode")}
              size="sm"
              variant="light"
            />
          </div>
        )}
        {onSyncFromPipeline && (
          <button
            type="button"
            disabled={syncing}
            onClick={onSyncFromPipeline}
            className="rounded-md bg-[#7c3aed] px-2.5 py-1 text-[11px] font-semibold text-[#17131f] transition hover:bg-[#6d28d9] disabled:opacity-50"
          >
            {syncing ? t("dramaforge.canvas.syncing") : t("dramaforge.canvas.syncEpisode")}
          </button>
        )}
        <span className="text-[11px] text-slate-500 tabular-nums">
          {nodeCount} · {edgeCount}
        </span>
      </div>
    </header>
  );
}
