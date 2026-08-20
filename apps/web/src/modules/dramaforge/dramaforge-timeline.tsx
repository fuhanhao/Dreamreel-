"use client";

import { useCallback, useMemo } from "react";
import type { DramaForgeShot, DramaForgeTimeline, DramaForgeTimelineClip } from "@dreamreel/shared-types";
import { DRAMA_FORGE_EXPORT_PRESETS } from "@dreamreel/shared-types";
import { useT } from "@/i18n/locale-provider";

export interface DramaForgeTimelineEditorProps {
  shots: DramaForgeShot[];
  timelineJson?: string | null;
  onChange: (timeline: DramaForgeTimeline) => void;
  disabled?: boolean;
}

function parseTimeline(json?: string | null): DramaForgeTimeline {
  if (!json) {
    return { clips: [], exportPreset: "douyin_9_16" };
  }
  try {
    return JSON.parse(json) as DramaForgeTimeline;
  } catch {
    return { clips: [], exportPreset: "douyin_9_16" };
  }
}

export function DramaForgeTimelineEditor({
  shots,
  timelineJson,
  onChange,
  disabled,
}: DramaForgeTimelineEditorProps) {
  const t = useT();
  const timeline = useMemo(() => parseTimeline(timelineJson), [timelineJson]);

  const orderedClips = useMemo(() => {
    if (timeline.clips.length > 0) {
      return [...timeline.clips].sort((a, b) => a.order - b.order);
    }
    return shots.map((s, i) => ({ shotId: s.id, order: i + 1, transition: "cut" as const }));
  }, [timeline.clips, shots]);

  const updateClips = useCallback(
    (clips: DramaForgeTimelineClip[]) => {
      onChange({ ...timeline, clips });
    },
    [onChange, timeline],
  );

  const moveClip = (index: number, direction: -1 | 1) => {
    const next = [...orderedClips];
    const target = index + direction;
    if (target < 0 || target >= next.length) return;
    [next[index], next[target]] = [next[target], next[index]];
    updateClips(next.map((c, i) => ({ ...c, order: i + 1 })));
  };

  const shotMap = useMemo(() => new Map(shots.map((s) => [s.id, s])), [shots]);

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap gap-2">
        <label className="text-xs text-[var(--ar-text-3)]">{t("dramaforge.timeline.exportPresetLabel")}</label>
        <select
          disabled={disabled}
          value={timeline.exportPreset ?? "douyin_9_16"}
          onChange={(e) =>
            onChange({
              ...timeline,
              exportPreset: e.target.value as DramaForgeTimeline["exportPreset"],
            })
          }
          className="dramaforge-input rounded-lg px-2 py-1 text-xs"
        >
          {DRAMA_FORGE_EXPORT_PRESETS.map((p) => (
            <option key={p.id} value={p.id}>
              {p.id === "douyin_9_16" ? t("dramaforge.workspace.aspectRatio9_16") : t("dramaforge.workspace.aspectRatio16_9")}
            </option>
          ))}
        </select>
      </div>

      <div className="space-y-2">
        {orderedClips.map((clip, index) => {
          const shot = shotMap.get(clip.shotId);
          return (
            <div
              key={clip.shotId}
              className="flex items-center gap-2 rounded-xl border border-[var(--ar-hairline)] bg-white/[0.03] p-2"
            >
              <span className="w-6 text-center text-xs text-[var(--ar-text-4)]">{index + 1}</span>
              <div className="min-w-0 flex-1">
                <p className="truncate text-xs font-medium text-[var(--ar-text)]">
                  {t("dramaforge.timeline.shotLabel", {
                    shotNumber: (shot?.shotNumber ?? "?") as string | number,
                    description: (shot?.description?.slice(0, 40) ?? clip.shotId) as string,
                  })}
                </p>
                {shot?.dialogue && (
                  <p className="truncate text-[10px] text-[var(--ar-text-4)]">{shot.dialogue}</p>
                )}
              </div>
              <select
                disabled={disabled}
                value={clip.transition ?? "cut"}
                onChange={(e) => {
                  const next = orderedClips.map((c, i) =>
                    i === index ? { ...c, transition: e.target.value as "cut" | "fade" } : c,
                  );
                  updateClips(next);
                }}
                className="dramaforge-input rounded px-1 py-0.5 text-[10px]"
              >
                <option value="cut">{t("dramaforge.timeline.transitionCut")}</option>
                <option value="fade">{t("dramaforge.timeline.transitionFade")}</option>
              </select>
              <button
                type="button"
                disabled={disabled || index === 0}
                onClick={() => moveClip(index, -1)}
                className="text-xs text-[var(--ar-accent-2)] disabled:opacity-30"
              >
                ↑
              </button>
              <button
                type="button"
                disabled={disabled || index === orderedClips.length - 1}
                onClick={() => moveClip(index, 1)}
                className="text-xs text-[var(--ar-accent-2)] disabled:opacity-30"
              >
                ↓
              </button>
            </div>
          );
        })}
        {orderedClips.length === 0 && (
          <p className="text-sm text-[var(--ar-text-3)]">{t("dramaforge.timeline.noClips")}</p>
        )}
      </div>
    </div>
  );
}
