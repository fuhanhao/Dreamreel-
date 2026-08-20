"use client";

import Image from "next/image";
import { useT } from "@/i18n/locale-provider";
import type { LinkedNode, UpstreamContext } from "@/lib/canvas-graph";

interface UpstreamLinksProps {
  context: UpstreamContext;
  onApply?: () => void;
  showApply?: boolean;
}

export function UpstreamLinks({ context, onApply, showApply }: UpstreamLinksProps) {
  const t = useT();

  const typeLabels: Record<string, string> = {
    text: t("dramaforge.canvas.nodeText"),
    script: t("dramaforge.canvas.nodeScript"),
    image: t("dramaforge.canvas.nodeImage"),
    video: t("dramaforge.canvas.nodeVideo"),
    audio: t("dramaforge.canvas.nodeAudio"),
    compose: t("dramaforge.canvas.nodeCompose"),
  };

  const items: LinkedNode[] = [
    ...context.texts,
    ...context.scripts,
    ...context.images,
    ...context.videos,
  ];

  if (items.length === 0) {
    return (
      <div className="rounded-lg border border-dashed border-slate-300 bg-slate-50/70 px-3 py-2">
        <p className="text-xs text-slate-500">{t("dramaforge.canvas.noUpstreamLinks")}</p>
      </div>
    );
  }

  return (
    <div className="space-y-2">
      <div className="flex items-center justify-between">
        <span className="text-xs font-medium text-slate-600">{t("dramaforge.canvas.upstreamCount", { count: items.length })}</span>
        {showApply && onApply && (
          <button
            type="button"
            onClick={onApply}
            className="text-xs font-medium text-[#17131f] underline-offset-2 hover:underline"
          >
              {t("dramaforge.canvas.applyUpstream")}
          </button>
        )}
      </div>
      <ul className="space-y-1.5">
        {items.map((item) => {
          const hasOutput = Boolean(item.data.outputUrl);
          const config = item.data.config ?? {};
          const preview = item.data.outputUrl ?? String(config.imageUrl ?? "");
          return (
            <li
              key={item.id}
              className="flex items-start gap-2 rounded-lg border border-slate-200 bg-white/75 px-2.5 py-2"
            >
              {preview && item.nodeType === "image" ? (
                <Image
                  src={preview}
                  alt=""
                  width={40}
                  height={40}
                  className="h-10 w-10 shrink-0 rounded border border-slate-200 object-cover"
                  unoptimized
                />
              ) : (
                <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded bg-slate-100 text-xs text-slate-500">
                  {typeLabels[item.nodeType]?.[0] ?? "?"}
                </div>
              )}
              <div className="min-w-0 flex-1">
                <div className="flex items-center gap-1.5">
                  <span className="truncate text-xs font-medium text-slate-800">{item.label}</span>
                  <span className="shrink-0 rounded bg-slate-100 px-1.5 py-0.5 text-[10px] text-slate-500">
                    {typeLabels[item.nodeType]}
                  </span>
                  {hasOutput && (
                    <span className="shrink-0 text-[10px] text-[#7c3aed]">{t("dramaforge.canvas.hasOutput")}</span>
                  )}
                </div>
                {Boolean(config.prompt || config.content) && (
                  <p className="mt-0.5 line-clamp-2 text-[10px] text-slate-500">
                    {String(config.prompt ?? config.content)}
                  </p>
                )}
              </div>
            </li>
          );
        })}
      </ul>
    </div>
  );
}
