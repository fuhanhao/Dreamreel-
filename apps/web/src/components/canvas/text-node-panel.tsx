"use client";

import { useEffect, useMemo, useState } from "react";
import type { CanvasNodeData, CanvasNodeType } from "@dreamreel/shared-types";
import type { UpstreamContext } from "@/lib/canvas-graph";
import { createTextGeneration, fetchTextModels } from "@/lib/api";
import { DfSelect } from "@/components/ui/df-select";
import { DfScrollArea } from "@/components/ui/df-scroll-area";
import { UpstreamLinks } from "./upstream-links";
import { useT } from "@/i18n/locale-provider";

const RECOMMENDED_TEXT_MODELS = [
  "gpt-4o-mini",
  "gpt-4o",
  "deepseek-chat",
  "qwen-plus",
  "claude-3-5-sonnet",
];

interface TextNodePanelProps {
  projectId: string;
  nodeId: string;
  nodeType: Extract<CanvasNodeType, "text" | "script">;
  nodeData: CanvasNodeData;
  upstream: UpstreamContext;
  onUpdate: (patch: Partial<CanvasNodeData>) => void;
  title: string;
  placeholder: string;
}

export function TextNodePanel({
  projectId,
  nodeId,
  nodeType,
  nodeData,
  upstream,
  onUpdate,
  title,
  placeholder,
}: TextNodePanelProps) {
  const t = useT();

  const GENERATE_HINTS: Record<"text" | "script", { label: string; hint: string; defaultPrompt: string }> = useMemo(() => ({
    text: {
      label: t("dramaforge.canvas.genScriptLabel"),
      hint: t("dramaforge.canvas.genScriptHint"),
      defaultPrompt: t("dramaforge.canvas.genScriptDefaultPrompt"),
    },
    script: {
      label: t("dramaforge.canvas.genStoryboardLabel"),
      hint: t("dramaforge.canvas.genStoryboardHint"),
      defaultPrompt: t("dramaforge.canvas.genStoryboardDefaultPrompt"),
    },
  }), [t]);

  const config = (nodeData.config ?? {}) as Record<string, unknown>;
  const content = String(config.content ?? config.prompt ?? "");
  const [models, setModels] = useState<string[]>(RECOMMENDED_TEXT_MODELS);
  const [model, setModel] = useState(String(config.model ?? RECOMMENDED_TEXT_MODELS[0]));
  const [genPrompt, setGenPrompt] = useState(String(config.genPrompt ?? ""));
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  const meta = GENERATE_HINTS[nodeType];

  useEffect(() => {
    void fetchTextModels()
      .then((res) => {
        const ids = res.data.map((m) => m.id);
        if (ids.length > 0) {
          const recommended = RECOMMENDED_TEXT_MODELS.filter((id) => ids.includes(id));
          setModels(recommended.length > 0 ? recommended : ids.slice(0, 15));
        }
      })
      .catch(() => setModels(RECOMMENDED_TEXT_MODELS));
  }, []);

  function applyUpstream() {
    if (upstream.mergedPrompt) {
      onUpdate({ config: { ...config, content: upstream.mergedPrompt } });
    }
  }

  async function handleGenerate() {
    const finalPrompt = genPrompt.trim() || meta.defaultPrompt;
    if (!finalPrompt) {
      setError(t("dramaforge.canvas.genPromptRequired"));
      return;
    }

    setLoading(true);
    setError("");
    onUpdate({ status: "running", config: { ...config, genPrompt: finalPrompt, model } });

    try {
      const res = await createTextGeneration({
        projectId,
        nodeId,
        model,
        prompt: finalPrompt,
        nodeType,
        context: upstream.mergedPrompt || undefined,
      });

      const job = res.data;
      if (job.status === "COMPLETED" && job.outputText) {
        onUpdate({
          status: "success",
          config: { ...config, content: job.outputText, genPrompt: finalPrompt, model, jobId: job.id },
        });
      } else {
        onUpdate({ status: "failed" });
        setError(job.errorMessage ?? t("dramaforge.canvas.textGenFailed"));
      }
    } catch (err) {
      onUpdate({ status: "failed" });
      setError(err instanceof Error ? err.message : t("dramaforge.canvas.textGenFailed"));
    } finally {
      setLoading(false);
    }
  }

  return (
    <DfScrollArea className="w-80 shrink-0 border-l border-white/70 bg-white/78 p-4 shadow-[-8px_0_28px_rgba(15,55,95,.08)] backdrop-blur-xl">
      <h2 className="text-sm font-semibold text-slate-900">{title}</h2>
      <p className="mt-1 text-xs text-slate-500">{t("dramaforge.canvas.textEditHint")}</p>

      <div className="mt-4 space-y-3">
        <UpstreamLinks context={upstream} onApply={applyUpstream} showApply={!!upstream.mergedPrompt} />

        <div className="space-y-3 rounded-xl border border-[#e5e7eb] bg-[#f8f7fc] p-3">
          <div>
            <p className="text-xs font-medium text-[#17131f]">{meta.label}</p>
            <p className="mt-0.5 text-[10px] text-slate-500">{meta.hint}</p>
          </div>

          <label className="block">
            <span className="text-xs text-slate-600">{t("dramaforge.canvas.llmModel")}</span>
            <DfSelect
              size="sm"
              searchable
              className="mt-1 w-full"
              variant="light"
              value={model}
              onChange={setModel}
              options={models.map((id) => ({ value: id, label: id }))}
            />
          </label>

          <label className="block">
            <span className="text-xs text-slate-600">{t("dramaforge.canvas.genPrompt")}</span>
            <textarea
              value={genPrompt}
              onChange={(e) => setGenPrompt(e.target.value)}
              rows={3}
              placeholder={meta.defaultPrompt}
              className="mt-1 w-full resize-none rounded-lg border border-slate-200 bg-white px-3 py-2 text-xs text-slate-800 outline-none focus:border-[#7c3aed] focus:ring-2 focus:ring-[#7c3aed]/15"
            />
          </label>

          <button
            type="button"
            onClick={handleGenerate}
            disabled={loading}
            className="w-full rounded-lg bg-[#7c3aed] py-2 text-sm font-semibold text-[#17131f] hover:bg-[#6d28d9] disabled:opacity-60"
          >
            {loading ? t("dramaforge.canvas.aiGenerating") : meta.label}
          </button>
        </div>

        {error && <p className="text-xs text-rose-700">{error}</p>}

        <label className="block">
          <span className="text-xs text-slate-600">{t("dramaforge.canvas.content")}</span>
          <textarea
            value={content}
            onChange={(e) =>
              onUpdate({ config: { ...config, content: e.target.value }, status: "idle" })
            }
            rows={10}
            placeholder={placeholder}
            className="mt-1 w-full resize-none rounded-lg border border-slate-200 bg-white px-3 py-2 text-xs text-slate-800 outline-none focus:border-[#7c3aed] focus:ring-2 focus:ring-[#7c3aed]/15"
          />
        </label>
      </div>
    </DfScrollArea>
  );
}
