"use client";

import { memo } from "react";
import { Handle, Position, type NodeProps } from "@xyflow/react";
import type { CanvasNodeData } from "@dreamreel/shared-types";
import { useT } from "@/i18n/locale-provider";

const statusDot: Record<string, string> = {
  idle: "bg-slate-400",
  queued: "bg-amber-500",
  running: "bg-sky-500 animate-pulse",
  success: "bg-[#7c3aed]",
  failed: "bg-rose-500",
};

function textPreview(nodeData: CanvasNodeData): string {
  const config = (nodeData.config ?? {}) as Record<string, unknown>;
  const raw = String(config.content ?? config.prompt ?? config.text ?? "").trim();
  if (!raw) return "";
  return raw.length > 90 ? `${raw.slice(0, 90)}…` : raw;
}

function mediaMeta(nodeData: CanvasNodeData): string {
  const config = (nodeData.config ?? {}) as Record<string, unknown>;
  if (nodeData.nodeType === "video") {
    const sec = config.seconds;
    return sec != null ? `${sec}s · 16:9` : "16:9";
  }
  if (nodeData.nodeType === "image") {
    return String(config.ratio ?? "1024×768");
  }
  return nodeData.nodeType;
}

function DramaNodeInner({ data, selected }: NodeProps) {
  const t = useT();
  const nodeData = data as unknown as CanvasNodeData;
  const previewText = textPreview(nodeData);
  const imageUrl = nodeData.outputUrl ?? String((nodeData.config as Record<string, unknown>)?.imageUrl ?? "");
  const videoUrl = nodeData.outputUrl;
  const hasImage = nodeData.nodeType === "image" && Boolean(imageUrl);
  const hasVideo = nodeData.nodeType === "video" && Boolean(videoUrl);
  const isPrompt = nodeData.nodeType === "text" || nodeData.nodeType === "script";

  return (
    <div
      className={`overflow-hidden rounded-xl border bg-white/92 shadow-[0_10px_28px_rgba(15,55,95,0.14)] backdrop-blur ${
        selected ? "border-[#7c3aed] ring-2 ring-[#7c3aed]/20" : "border-white"
      } ${isPrompt ? "min-w-[240px] max-w-[280px]" : "min-w-[180px] max-w-[220px]"}`}
    >
      <Handle
        type="target"
        position={Position.Left}
        className="!-left-1.5 !h-2.5 !w-2.5 !border-2 !border-white !bg-[#7c3aed]"
      />

      <div className="flex items-center justify-between gap-2 border-b border-slate-200/70 bg-slate-50/70 px-2.5 py-1.5">
        <span className="truncate text-[11px] text-slate-500">{mediaMeta(nodeData)}</span>
        <span className={`h-1.5 w-1.5 shrink-0 rounded-full ${statusDot[nodeData.status] ?? statusDot.idle}`} />
      </div>

      {hasImage ? (
        <div className="relative aspect-[4/3] w-full bg-slate-100">
          {/* eslint-disable-next-line @next/next/no-img-element */}
          <img
            src={imageUrl}
            alt=""
            loading="lazy"
            decoding="async"
            className="h-full w-full object-cover"
            draggable={false}
          />
        </div>
      ) : hasVideo ? (
        <div className="relative flex aspect-[4/3] w-full items-center justify-center bg-slate-100">
          {/* 视频仅在选中时挂载，避免大量 video 同时解码 */}
          {selected ? (
            <video
              src={videoUrl}
              muted
              playsInline
              preload="metadata"
              className="h-full w-full object-cover"
            />
          ) : (
            <div className="flex flex-col items-center gap-1 text-slate-500">
              <span className="flex h-8 w-8 items-center justify-center rounded-full border border-slate-300 bg-white text-sm text-[#17131f] shadow-sm">
                ▶
              </span>
              <span className="text-[10px]">{t("dramaforge.canvas.clickToPreview")}</span>
            </div>
          )}
        </div>
      ) : isPrompt ? (
        <div className="max-h-[88px] overflow-hidden px-3 py-2">
          <p className="line-clamp-4 text-[11px] leading-relaxed text-slate-700">
            {previewText || t("dramaforge.canvas.emptyPrompt")}
          </p>
        </div>
      ) : (
        <div className="px-3 py-4 text-center text-xs text-slate-500">
          {nodeData.label}
          {nodeData.status === "running" && (
            <p className="mt-1 text-[10px] text-sky-400">{t("dramaforge.canvas.generating")}</p>
          )}
        </div>
      )}

      <div className="border-t border-slate-200/70 px-2.5 py-1.5">
        <span className="block truncate text-[11px] font-semibold text-slate-800">{nodeData.label}</span>
      </div>

      <Handle
        type="source"
        position={Position.Right}
        className="!-right-1.5 !h-2.5 !w-2.5 !border-2 !border-white !bg-sky-500"
      />
    </div>
  );
}

export const DramaNode = memo(DramaNodeInner);
