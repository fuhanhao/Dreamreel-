"use client";

import type { CreationItem } from "./creation-types";
import {
  formatCreationTime,
  getCreationTitle,
  getCreationTypeLabel,
} from "./creation-types";
import { LoaderCircle, Trash2 } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { useLocale, useT } from "@/i18n/locale-provider";

interface CreationCardProps {
  item: CreationItem;
  onClick?: () => void;
  onDelete?: (item: CreationItem) => void;
}

export function CreationCard({ item, onClick, onDelete }: CreationCardProps) {
  const { locale } = useLocale();
  const t = useT();
  const isCompleted = item.status === "COMPLETED";
  const isFailed = item.status === "FAILED";
  const isRunning = item.status === "IN_PROGRESS" || item.status === "QUEUED";
  const progress = item.progress ?? 0;

  const statusBadge = isCompleted ? (
    <Badge className="bg-[#7c3aed]/25 text-[#5c8200] hover:bg-[#7c3aed]/25">{t("creator.statusCompleted")}</Badge>
  ) : isFailed ? (
    <Badge
      variant="outline"
      className="border-rose-200"
      style={{ backgroundColor: "#fff1f2", color: "#9f1239" }}
    >
      {t("creator.statusFailed")}
    </Badge>
  ) : (
    <Badge variant="secondary"><LoaderCircle className="mr-1 size-3 animate-spin" />{t("creator.statusGenerating")}</Badge>
  );

  return (
    <div className="group relative w-full">
      {onDelete && (
        <button
          type="button"
          title={t("common.delete")}
          onClick={(e) => {
            e.stopPropagation();
            onDelete(item);
          }}
          className="absolute right-2 top-2 z-[1] flex h-7 w-7 items-center justify-center rounded-full bg-black/50 text-white opacity-0 backdrop-blur-sm transition-opacity hover:bg-red-500 group-hover:opacity-100"
        >
          <Trash2 className="size-3.5" />
        </button>
      )}

      <button
        type="button"
        onClick={onClick}
        className="w-full overflow-hidden rounded-xl border border-[#e5e7eb] bg-white text-left transition hover:-translate-y-0.5 hover:border-[#cbd0d6] hover:shadow-md focus:outline-none focus:ring-2 focus:ring-[#7c3aed]/40"
      >
        <div className="relative isolate aspect-[4/3] overflow-hidden bg-[#f1f3f5]">
          <span className="absolute left-2 top-2 rounded-md bg-black/55 px-1.5 py-0.5 text-[10px] font-medium text-white backdrop-blur-sm">
            {getCreationTypeLabel(item, locale)}
          </span>

          {isFailed ? (
            <div className="flex h-full flex-col items-center justify-center gap-2 p-4 text-center">
              <p className="text-[11px] font-medium" style={{ color: "#9f1239" }}>{t("creator.generationFailed")}</p>
              <p className="line-clamp-2 text-[10px] text-muted-foreground">
                {item.errorMessage ?? t("creator.retryLater")}
              </p>
            </div>
          ) : item.mode === "video" && item.outputUrl ? (
            <>
              <video
                src={`${item.outputUrl}${item.outputUrl.includes("?") ? "&" : "?"}v=${item.id}`}
                className="h-full w-full object-cover"
                muted
              />
              <div className="absolute inset-0 flex items-center justify-center bg-black/20 opacity-0 transition-opacity group-hover:opacity-100">
                <div className="flex h-10 w-10 items-center justify-center rounded-full bg-white/90">
                  <svg className="h-5 w-5 text-zinc-800" fill="currentColor" viewBox="0 0 24 24">
                    <path d="M8 5v14l11-7z" />
                  </svg>
                </div>
              </div>
            </>
          ) : item.mode === "image" && item.outputUrl ? (
            // eslint-disable-next-line @next/next/no-img-element
            <img
              src={`${item.outputUrl}${item.outputUrl.includes("?") ? "&" : "?"}v=${item.id}`}
              alt={item.prompt}
              className="h-full w-full object-cover"
            />
          ) : item.mode === "prompt" && item.outputText ? (
            <div className="flex h-full items-start overflow-y-auto p-3">
              <p className="line-clamp-6 text-[11px] leading-relaxed text-muted-foreground">
                {item.outputText}
              </p>
            </div>
          ) : isRunning ? (
            <div className="flex h-full flex-col items-center justify-center gap-2">
              <div className="h-8 w-8 animate-spin rounded-full border-2 border-[#7c3aed]/25 border-t-[#7c3aed]" />
              <span className="text-[11px] text-muted-foreground">{t("creator.generationProgress", { progress })}</span>
            </div>
          ) : (
            <div className="flex h-full items-center justify-center text-[11px] text-muted-foreground">
              {t("creator.waiting")}
            </div>
          )}

          {isRunning && (
            <div className="absolute bottom-0 left-0 right-0 h-1 bg-white/10">
              <div
                className="h-full bg-primary transition-all duration-500"
                style={{ width: `${Math.max(progress, 5)}%` }}
              />
            </div>
          )}
        </div>

        <div className="p-3">
          <div className="mb-1.5 flex items-start justify-between gap-2">
            <p className="line-clamp-1 flex-1 text-xs font-medium">
              {getCreationTitle(item.prompt)}
            </p>
            {statusBadge}
          </div>
          <div className="flex items-center justify-between text-[10px] text-muted-foreground">
            <span className="truncate">{item.model}</span>
            <span className="shrink-0">{formatCreationTime(item.createdAt, locale)}</span>
          </div>
        </div>
      </button>
    </div>
  );
}
