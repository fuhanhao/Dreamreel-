"use client";

import Image from "next/image";
import { useEffect, useState } from "react";
import type { CanvasNodeData, GenerationStatus } from "@dreamreel/shared-types";
import {
  createVideoGeneration,
  fetchVideoGeneration,
  fetchVideoModels,
} from "@/lib/api";
import type { UpstreamContext } from "@/lib/canvas-graph";
import { pickModelsForMode } from "@/lib/canvas-graph";
import { DfSelect } from "@/components/ui/df-select";
import { DfNumberStepper } from "@/components/ui/df-number-stepper";
import { DfScrollArea } from "@/components/ui/df-scroll-area";
import { UpstreamLinks } from "./upstream-links";
import { useT } from "@/i18n/locale-provider";

interface VideoNodePanelProps {
  projectId: string;
  nodeId: string;
  nodeData: CanvasNodeData;
  upstream: UpstreamContext;
  onUpdate: (patch: Partial<CanvasNodeData>) => void;
}

function mapJobStatus(status: GenerationStatus): CanvasNodeData["status"] {
  switch (status) {
    case "QUEUED":
      return "queued";
    case "IN_PROGRESS":
      return "running";
    case "COMPLETED":
      return "success";
    case "FAILED":
      return "failed";
    default:
      return "idle";
  }
}

export function VideoNodePanel({
  projectId,
  nodeId,
  nodeData,
  upstream,
  onUpdate,
}: VideoNodePanelProps) {
  const t = useT();
  const config = (nodeData.config ?? {}) as Record<string, unknown>;
  const [allModels, setAllModels] = useState<string[]>([]);
  const [models, setModels] = useState<string[]>([]);
  const [mode, setMode] = useState<"text-to-video" | "image-to-video">(
    (config.mode as "text-to-video" | "image-to-video") ?? upstream.videoMode,
  );
  const [model, setModel] = useState(String(config.model ?? ""));
  const [prompt, setPrompt] = useState(String(config.prompt ?? ""));
  const [seconds, setSeconds] = useState(Number(config.seconds ?? 5));
  const [ratio, setRatio] = useState<"16:9" | "9:16" | "1:1">(
    (config.ratio as "16:9" | "9:16" | "1:1") ?? "9:16",
  );
  const [useUpstreamImage, setUseUpstreamImage] = useState(
    config.useUpstreamImage !== false,
  );
  const [loading, setLoading] = useState(false);
  const [polling, setPolling] = useState(false);
  const [error, setError] = useState("");

  const effectiveImageUrl =
    useUpstreamImage && upstream.referenceImageUrl
      ? upstream.referenceImageUrl
      : String(config.imageUrl ?? "");

  useEffect(() => {
    void fetchVideoModels()
      .then((res) => {
        const ids = res.data.map((m) => m.id);
        setAllModels(ids);
      })
      .catch(() => setAllModels([]));
  }, []);

  useEffect(() => {
    const nextMode =
      useUpstreamImage && upstream.referenceImageUrl
        ? "image-to-video"
        : (config.mode as "text-to-video" | "image-to-video") ?? upstream.videoMode;
    const filtered = pickModelsForMode(nextMode, allModels);
    const timer = window.setTimeout(() => {
      setMode(nextMode);
      setModels(filtered.length > 0 ? filtered : allModels.slice(0, 15));
    }, 0);
    return () => window.clearTimeout(timer);
  }, [upstream.videoMode, upstream.referenceImageUrl, useUpstreamImage, allModels, config.mode]);

  useEffect(() => {
    if (models.length > 0 && !models.includes(model)) {
      const timer = window.setTimeout(() => setModel(models[0]), 0);
      return () => window.clearTimeout(timer);
    }
  }, [models, model]);

  useEffect(() => {
    const jobId = config.jobId as string | undefined;
    if (!jobId || nodeData.status === "success" || nodeData.status === "failed") {
      return;
    }

    let cancelled = false;
    const timer = window.setTimeout(() => setPolling(true), 0);

    const poll = async () => {
      try {
        const res = await fetchVideoGeneration(jobId);
        if (cancelled) return;

        const job = res.data;
        onUpdate({
          status: mapJobStatus(job.status),
          config: { ...config, model, prompt, seconds, ratio, mode, jobId, useUpstreamImage },
          outputUrl: job.outputUrl ?? undefined,
        });

        if (job.status === "COMPLETED" || job.status === "FAILED") {
          setPolling(false);
          if (job.status === "FAILED") {
            setError(job.errorMessage ?? t("dramaforge.canvas.videoGenFailed"));
          }
          return;
        }

        setTimeout(poll, 5000);
      } catch (err) {
        if (!cancelled) {
          setPolling(false);
          setError(err instanceof Error ? err.message : t("dramaforge.canvas.pollFailed"));
        }
      }
    };

    void poll();

    return () => {
      cancelled = true;
      window.clearTimeout(timer);
      setPolling(false);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [config.jobId, nodeData.status]);

  function applyUpstream() {
    if (upstream.mergedPrompt) {
      setPrompt(upstream.mergedPrompt);
    }
    if (upstream.referenceImageUrl) {
      setUseUpstreamImage(true);
      setMode("image-to-video");
    }
    onUpdate({
      config: {
        ...config,
        prompt: upstream.mergedPrompt || prompt,
        mode: upstream.referenceImageUrl ? "image-to-video" : mode,
        useUpstreamImage: true,
      },
    });
  }

  async function handleGenerate() {
    const finalPrompt = prompt.trim() || upstream.mergedPrompt.trim();
    if (!finalPrompt) {
      setError(t("dramaforge.canvas.videoPromptRequired"));
      return;
    }

    if (mode === "image-to-video" && !effectiveImageUrl) {
      setError(t("dramaforge.canvas.imageToVideoRequiresImage"));
      return;
    }

    setLoading(true);
    setError("");
    onUpdate({
      status: "queued",
      config: {
        ...config,
        model,
        prompt: finalPrompt,
        seconds,
        ratio,
        mode,
        useUpstreamImage,
        imageUrl: effectiveImageUrl || undefined,
      },
    });

    try {
      const res = await createVideoGeneration({
        projectId,
        nodeId,
        model,
        prompt: finalPrompt,
        seconds,
        ratio,
        imageUrl: mode === "image-to-video" ? effectiveImageUrl : undefined,
      });

      const job = res.data;
      onUpdate({
        status: mapJobStatus(job.status),
        config: {
          ...config,
          model,
          prompt: finalPrompt,
          seconds,
          ratio,
          mode,
          useUpstreamImage,
          imageUrl: effectiveImageUrl || undefined,
          jobId: job.id,
        },
        outputUrl: job.outputUrl ?? undefined,
      });
    } catch (err) {
      onUpdate({ status: "failed" });
      setError(err instanceof Error ? err.message : t("dramaforge.canvas.submitFailed"));
    } finally {
      setLoading(false);
    }
  }

  return (
    <DfScrollArea className="w-80 shrink-0 border-l border-white/70 bg-white/78 p-4 shadow-[-8px_0_28px_rgba(15,55,95,.08)] backdrop-blur-xl">
      <div className="flex items-center justify-between">
        <h2 className="text-sm font-semibold text-slate-900">{t("dramaforge.canvas.nodeVideo")}</h2>
        <span
          className={`rounded-full px-2 py-0.5 text-[10px] ${
            mode === "image-to-video"
              ? "bg-pink-100 text-pink-800"
              : "bg-amber-100 text-amber-800"
          }`}
        >
          {mode === "image-to-video" ? t("dramaforge.canvas.imageToVideo") : t("dramaforge.canvas.textToVideo")}
        </span>
      </div>
      <p className="mt-1 text-xs text-slate-500">{t("dramaforge.canvas.videoAutoReadHint")}</p>

      <div className="mt-4 space-y-3">
        <UpstreamLinks
          context={upstream}
          onApply={applyUpstream}
          showApply={!!(upstream.mergedPrompt || upstream.referenceImageUrl)}
        />

        {upstream.referenceImageUrl && (
          <label className="flex items-center gap-2 rounded-lg border border-slate-200 bg-white/75 px-3 py-2">
            <input
              type="checkbox"
              checked={useUpstreamImage}
              onChange={(e) => setUseUpstreamImage(e.target.checked)}
              className="rounded"
            />
            <span className="text-xs text-slate-700">{t("dramaforge.canvas.useUpstreamImage")}</span>
          </label>
        )}

        {effectiveImageUrl && mode === "image-to-video" && (
          <div>
            <p className="mb-1 text-xs text-slate-600">{t("dramaforge.canvas.referenceImage")}</p>
            <Image
              src={effectiveImageUrl}
              alt={t("dramaforge.canvas.referenceAlt")}
              width={640}
              height={360}
              className="w-full rounded-lg border border-pink-500/30 object-cover max-h-32"
              unoptimized
            />
          </div>
        )}

        <label className="block">
          <span className="text-xs text-slate-600">{t("dramaforge.canvas.model")}</span>
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
          <span className="text-xs text-slate-600">{t("dramaforge.canvas.shotPrompt")}</span>
          <textarea
            value={prompt}
            onChange={(e) => setPrompt(e.target.value)}
            rows={4}
            placeholder={
              upstream.mergedPrompt
                ? t("dramaforge.canvas.shotPromptHasUpstream")
                : t("dramaforge.canvas.shotPromptPlaceholder")
            }
            className="mt-1 w-full resize-none rounded-lg border border-slate-200 bg-white px-3 py-2 text-xs text-slate-800 outline-none focus:border-[#7c3aed] focus:ring-2 focus:ring-[#7c3aed]/15"
          />
        </label>

        <div className="grid grid-cols-2 gap-2">
          <label className="block">
            <span className="text-xs text-slate-600">{t("dramaforge.canvas.durationSeconds")}</span>
            <DfNumberStepper
              size="sm"
              variant="light"
              className="mt-1 w-full"
              min={4}
              max={15}
              step={1}
              value={seconds}
              onChange={setSeconds}
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
                { value: "9:16", label: t("dramaforge.canvas.videoRatio9_16") },
                { value: "16:9", label: t("dramaforge.canvas.videoRatio16_9") },
                { value: "1:1", label: t("dramaforge.canvas.ratio1_1") },
              ]}
            />
          </label>
        </div>

        <button
          type="button"
          onClick={handleGenerate}
          disabled={loading || polling}
          className="w-full rounded-lg bg-gradient-to-r from-amber-500 to-orange-500 py-2 text-sm font-medium text-white shadow-sm hover:opacity-90 disabled:opacity-60"
        >
          {loading ? t("dramaforge.canvas.submitting") : polling ? t("dramaforge.canvas.generating") : mode === "image-to-video" ? t("dramaforge.canvas.imageToVideo") : t("dramaforge.canvas.textToVideo")}
        </button>

        {error && <p className="text-xs text-rose-700">{error}</p>}

        {nodeData.outputUrl && (
          <div className="mt-2">
            <p className="mb-2 text-xs text-[#7c3aed]">{t("dramaforge.canvas.videoPreview")}</p>
            <video
              src={nodeData.outputUrl}
              controls
              playsInline
              className="max-h-72 w-full rounded-lg border border-amber-200 bg-slate-100"
            />
          </div>
        )}
      </div>
    </DfScrollArea>
  );
}
