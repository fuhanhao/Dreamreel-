"use client";

import type { DramaForgePipelineStage } from "@dreamreel/shared-types";
import { DRAMA_FORGE_PIPELINE_STAGES, canAdvanceToStep } from "@dreamreel/shared-types";
import { useT } from "@/i18n/locale-provider";

const STAGE_LABEL_KEYS: Record<string, string> = {
  story_input: "dramaforge.workspace.stageStoryInput",
  script_locked: "dramaforge.workspace.stageScriptLocked",
  assets_locked: "dramaforge.workspace.stageAssetsLocked",
  video_done: "dramaforge.workspace.stageVideoDone",
  composed: "dramaforge.workspace.stageComposed",
};

const STAGE_DESC_KEYS: Record<string, string> = {
  story_input: "dramaforge.workspace.stageStoryInputDesc",
  script_locked: "dramaforge.workspace.stageScriptLockedDesc",
  assets_locked: "dramaforge.workspace.stageAssetsLockedDesc",
  video_done: "dramaforge.workspace.stageVideoDoneDesc",
  composed: "dramaforge.workspace.stageComposedDesc",
};

export interface DramaForgeWizardProps {
  currentStage: DramaForgePipelineStage;
  activeStep: DramaForgePipelineStage;
  onStepChange: (step: DramaForgePipelineStage) => void;
  onPrev?: () => void;
  onNext?: () => void;
  canGoNext?: boolean;
  className?: string;
}

export function DramaForgeWizard({
  currentStage,
  activeStep,
  onStepChange,
  onPrev,
  onNext,
  canGoNext = false,
  className = "",
}: DramaForgeWizardProps) {
  const t = useT();
  const activeIdx = DRAMA_FORGE_PIPELINE_STAGES.findIndex((s) => s.id === activeStep);
  const currentIdx = DRAMA_FORGE_PIPELINE_STAGES.findIndex((s) => s.id === currentStage);

  return (
    <div className={`space-y-3 ${className}`}>
      <div className="flex flex-wrap items-center gap-1">
        {DRAMA_FORGE_PIPELINE_STAGES.map((stage, index) => {
          const done = index < currentIdx;
          const current = index === currentIdx;
          const selected = stage.id === activeStep;
          // Allow one step ahead of server stage OR currently viewed step
          // (e.g. view is 出成片 while overview is still storyboard_locked → ⑥ AI 剪辑 clickable)
          const reachable =
            canAdvanceToStep(stage.id, currentStage)
            || canAdvanceToStep(stage.id, activeStep)
            || index <= currentIdx;
          return (
            <button
              key={stage.id}
              type="button"
              disabled={!reachable}
              onClick={() => onStepChange(stage.id)}
              style={
                selected
                  ? { borderColor: "rgba(124, 58, 237, 0.5)", backgroundColor: "#f4ffd6" }
                  : done
                    ? { borderColor: "#e3ffa3", backgroundColor: "#f8ffe0" }
                    : undefined
              }
              className={`flex min-w-0 flex-1 items-center gap-2 rounded-xl border px-2 py-2 text-left transition sm:min-w-[120px] sm:flex-none ${
                selected
                  ? ""
                  : done
                    ? ""
                    : current
                      ? "border-[var(--ar-hairline)] bg-white/55"
                      : "border-[var(--ar-hairline)] opacity-50"
              }`}
            >
              <span
                style={
                  done
                    ? { backgroundColor: "#7c3aed", color: "#17131f" }
                    : selected
                      ? { backgroundColor: "#7c3aed", color: "#17131f" }
                      : undefined
                }
                className={`flex h-6 w-6 shrink-0 items-center justify-center rounded-full text-[10px] font-bold ${
                  done
                    ? ""
                    : selected
                      ? ""
                      : "bg-slate-200/75 text-[var(--ar-text-3)]"
                }`}
              >
                {done ? "✓" : stage.step}
              </span>
              <span className="min-w-0 hidden sm:block">
                <span className="block truncate text-xs font-medium text-[var(--ar-text)]">{t(STAGE_LABEL_KEYS[stage.id])}</span>
                <span className="block truncate text-[9px] text-[var(--ar-text-4)]">{t(STAGE_DESC_KEYS[stage.id])}</span>
              </span>
            </button>
          );
        })}
      </div>

      {(onPrev || onNext) && (
        <div className="flex justify-between gap-2">
          {onPrev ? (
            <button
              type="button"
              onClick={onPrev}
              disabled={activeIdx <= 0}
              className="rounded-lg border border-[var(--ar-hairline)] px-3 py-1.5 text-xs text-[var(--ar-text-3)] disabled:opacity-40"
            >
              {t("dramaforge.wizard.previous")}
            </button>
          ) : (
            <span />
          )}
          {onNext && (
            <button
              type="button"
              onClick={onNext}
              disabled={!canGoNext || activeIdx >= DRAMA_FORGE_PIPELINE_STAGES.length - 1}
              className="rounded-lg bg-[#7c3aed] px-3 py-1.5 text-xs font-semibold text-[#17131f] disabled:opacity-40"
            >
              {t("dramaforge.wizard.next")}
            </button>
          )}
        </div>
      )}
    </div>
  );
}

export function stageContentView(
  step: DramaForgePipelineStage,
): "source" | "script" | "assets" | "storyboard" | "video" | "post" {
  switch (step) {
    case "story_input":
      return "source";
    case "script_locked":
      return "script";
    case "assets_locked":
      return "assets";
    case "video_done":
      return "video";
    case "composed":
      return "post";
    default:
      return "source";
  }
}
