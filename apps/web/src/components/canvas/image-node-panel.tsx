"use client";

import { useEffect, useState } from "react";
import type { CanvasNodeData } from "@dreamreel/shared-types";
import type { UpstreamContext } from "@/lib/canvas-graph";
import { createImageGeneration, fetchImageModels } from "@/lib/api";
import { DfSelect } from "@/components/ui/df-select";
import { DfScrollArea } from "@/components/ui/df-scroll-area";
import { UpstreamLinks } from "./upstream-links";
import { useT } from "@/i18n/locale-provider";

const RECOMMENDED_IMAGE_MODELS = [
  "nano-banana-2",
  "grok-imagine/text-to-image",
];

interface ImageNodePanelProps {
  projectId: string;
  nodeId: string;
  nodeData: CanvasNodeData;
  upstream: UpstreamContext;
  onUpdate: (patch: Partial<CanvasNodeData>) => void;
}

export function ImageNodePanel({
  projectId,
  nodeId,
  nodeData,
  upstream,
  onUpdate,
}: ImageNodePanelProps) {
  const t = useT();
  const config = (nodeData.config ?? {}) as Record<string, unknown>;
  const [models, setModels] = useState<string[]>(RECOMMENDED_IMAGE_MODELS);
  const [model, setModel] = useState(String(config.model ?? RECOMMENDED_IMAGE_MODELS[0]));
  const [prompt, setPrompt] = useState(String(config.prompt ?? ""));
  const [ratio, setRatio] = useState<"16:9" | "9:16" | "1:1">(
    (config.ratio as "16:9" | "9:16" | "1:1") ?? "9:16",
  );
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  const imageUrl = String(nodeData.outputUrl ?? config.imageUrl ?? "");

  useEffect(() => {
    void fetchImageModels()
      .then((res) => {
        const ids = res.data.map((m) => m.id);
        if (ids.length > 0) {
          const recommended = RECOMMENDED_IMAGE_MODELS.filter((id) => ids.includes(id));
          setModels(recommended.length > 0 ? recommended : ids.slice(0, 15));
        }
      })
      .catch(() => setModels(RECOMMENDED_IMAGE_MODELS));
  }, []);

  function applyUpstream() {
    const nextPrompt = upstream.mergedPrompt || prompt;
    setPrompt(nextPrompt);
    onUpdate({ config: { ...config, prompt: nextPrompt, model, ratio } });
  }

  async function handleGenerate() {
    const finalPrompt = prompt.trim() || upstream.mergedPrompt.trim();
    if (!finalPrompt) {
      setError(t("dramaforge.canvas.imagePromptRequired"));
      return;
    }

    setLoading(true);
    setError("");
    onUpdate({ status: "running", config: { ...config, prompt: finalPrompt, model, ratio } });

    try {
      const res = await createImageGeneration({
        projectId,
        nodeId,
        model,
        prompt: finalPrompt,
        ratio,
      });

      const job = res.data;
      if (job.status === "COMPLETED" && job.outputUrl) {
        onUpdate({
          status: "success",
          outputUrl: job.outputUrl,
          config: { ...config, prompt: finalPrompt, model, ratio, imageUrl: job.outputUrl, jobId: job.id },
        });
      } else {
        onUpdate({ status: "failed" });
        setError(job.errorMessage ?? t("dramaforge.canvas.imageGenFailed"));
      }
    } catch (err) {
      onUpdate({ status: "failed" });
      setError(err instanceof Error ? err.message : t("dramaforge.canvas.imageGenFailed"));
    } finally {
      setLoading(false);
    }
  }

  return (
    <DfScrollArea className="w-80 shrink-0 border-l border-white/70 bg-white/78 p-4 shadow-[-8px_0_28px_rgba(15,55,95,.08)] backdrop-blur-xl">
      <h2 className="text-sm font-semibold text-slate-900">{t("dramaforge.canvas.nodeImage")}</h2>
      <p className="mt-1 text-xs text-slate-500">{t("dramaforge.canvas.imageAutoPassHint")}</p>

      <div className="mt-4 space-y-3">
        <UpstreamLinks
          context={upstream}
          onApply={applyUpstream}
          showApply={!!upstream.mergedPrompt}
        />

        <label className="block">
          <span className="text-xs text-slate-600">{t("dramaforge.canvas.imageModel")}</span>
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
          <span className="text-xs text-slate-600">{t("dramaforge.canvas.imagePrompt")}</span>
          <textarea
            value={prompt}
            onChange={(e) => {
              setPrompt(e.target.value);
              onUpdate({ config: { ...config, prompt: e.target.value, model, ratio } });
            }}
            rows={4}
            placeholder={t("dramaforge.canvas.imagePromptPlaceholder")}
            className="mt-1 w-full resize-none rounded-lg border border-slate-200 bg-white px-3 py-2 text-xs text-slate-800 outline-none focus:border-[#7c3aed] focus:ring-2 focus:ring-[#7c3aed]/15"
          />
        </label>

        <label className="block">
          <span className="text-xs text-slate-600">{t("dramaforge.canvas.aspectRatio")}</span>
          <DfSelect
            size="sm"
            searchable={false}
            className="mt-1 w-full"
            variant="light"
            value={ratio}
            onChange={(v) => setRatio(v as "16:9" | "9:16" | "1:1")}
            options={[
              { value: "9:16", label: t("dramaforge.canvas.ratio9_16") },
              { value: "16:9", label: t("dramaforge.canvas.ratio16_9") },
              { value: "1:1", label: t("dramaforge.canvas.ratio1_1") },
            ]}
          />
        </label>

        <button
          type="button"
          onClick={handleGenerate}
          disabled={loading}
          className="w-full rounded-lg bg-gradient-to-r from-fuchsia-600 to-pink-500 py-2 text-sm font-medium text-white shadow-sm hover:opacity-90 disabled:opacity-60"
        >
          {loading ? t("dramaforge.canvas.generating") : t("dramaforge.canvas.generateImage")}
        </button>

        {error && <p className="text-xs text-rose-700">{error}</p>}

        {imageUrl && (
          <div>
            <p className="mb-1 text-xs text-[#7c3aed]">{t("dramaforge.canvas.imagePreviewHint")}</p>
            {/* eslint-disable-next-line @next/next/no-img-element */}
            <img
              src={imageUrl}
              alt={t("dramaforge.canvas.imagePreviewAlt")}
              className="max-h-72 w-full rounded-lg border border-pink-200 bg-slate-100 object-contain"
            />
          </div>
        )}
      </div>
    </DfScrollArea>
  );
}
