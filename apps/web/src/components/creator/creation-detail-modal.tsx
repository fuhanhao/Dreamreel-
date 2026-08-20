"use client";

import { useEffect, useRef, useState } from "react";
import { fetchGeneration } from "@/lib/api";
import type { CreationItem } from "./creation-types";
import {
  downloadCreationFile,
  formatCreationTime,
  getCreationTypeLabel,
  getDownloadFilename,
  mapCreationRecord,
} from "./creation-types";
import { useLocale, useT } from "@/i18n/locale-provider";

interface CreationDetailModalProps {
  item: CreationItem | null;
  onClose: () => void;
  onUpdated?: (item: CreationItem) => void;
  onDelete?: (item: CreationItem) => Promise<void> | void;
  /** 在「全部创作」弹窗内嵌打开时，避免双层遮罩与 z-index 冲突 */
  stacked?: boolean;
}

function isTerminal(status: CreationItem["status"]) {
  return status === "COMPLETED" || status === "FAILED";
}

export function CreationDetailModal({
  item,
  onClose,
  onUpdated,
  onDelete,
  stacked = false,
}: CreationDetailModalProps) {
  const { locale } = useLocale();
  const t = useT();
  const [detail, setDetail] = useState<CreationItem | null>(null);
  const [prevItemId, setPrevItemId] = useState<string | undefined>(item?.id);
  const [loading, setLoading] = useState(false);
  const onUpdatedRef = useRef(onUpdated);

  useEffect(() => {
    onUpdatedRef.current = onUpdated;
  }, [onUpdated]);

  if (prevItemId !== item?.id) {
    setPrevItemId(item?.id);
    setDetail(item);
  }

  useEffect(() => {
    if (!item) return;
    let cancelled = false;
    let pollTimer: ReturnType<typeof setTimeout> | null = null;

    async function syncOnce(showSpinner: boolean) {
      if (!item) return null;
      if (showSpinner) setLoading(true);
      try {
        const res = await fetchGeneration(item.id);
        if (cancelled) return null;
        const fresh = mapCreationRecord(res.data);
        setDetail(fresh);
        onUpdatedRef.current?.(fresh);
        return fresh;
      } catch {
        if (!cancelled) setDetail(item);
        return null;
      } finally {
        if (!cancelled && showSpinner) setLoading(false);
      }
    }

    void (async () => {
      const fresh = await syncOnce(true);
      if (cancelled || !fresh || isTerminal(fresh.status)) return;

      const tick = async () => {
        if (cancelled) return;
        const next = await syncOnce(false);
        if (cancelled) return;
        if (next && !isTerminal(next.status)) {
          pollTimer = setTimeout(tick, 5000);
        }
      };
      pollTimer = setTimeout(tick, 5000);
    })();

    return () => {
      cancelled = true;
      if (pollTimer) clearTimeout(pollTimer);
    };
    // 只按记录 ID 同步；onUpdated 走 ref，避免父组件重渲染触发请求风暴
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [item?.id]);

  useEffect(() => {
    if (!item?.id) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") onClose();
    };
    document.addEventListener("keydown", onKey);
    if (!stacked) {
      document.body.style.overflow = "hidden";
    }
    return () => {
      document.removeEventListener("keydown", onKey);
      if (!stacked) {
        document.body.style.overflow = "";
      }
    };
  }, [item?.id, onClose, stacked]);

  if (!item || !detail) return null;

  const current = detail;

  async function handleDownload() {
    if (current.mode === "prompt" && current.outputText) {
      const blob = new Blob([current.outputText], { type: "text/plain;charset=utf-8" });
      const url = URL.createObjectURL(blob);
      const anchor = document.createElement("a");
      anchor.href = url;
      anchor.download = getDownloadFilename(current);
      anchor.click();
      URL.revokeObjectURL(url);
      return;
    }
    if (current.outputUrl) {
      await downloadCreationFile(current.outputUrl, getDownloadFilename(current));
    }
  }

  async function handleCopyText() {
    if (current.outputText) {
      await navigator.clipboard.writeText(current.outputText);
    }
  }

  const isCompleted = current.status === "COMPLETED";
  const isFailed = current.status === "FAILED";
  const isRunning = current.status === "IN_PROGRESS" || current.status === "QUEUED";
  const canDownload =
    isCompleted &&
    ((current.mode !== "prompt" && current.outputUrl) || (current.mode === "prompt" && current.outputText));

  const mediaSrc = current.outputUrl
    ? `${current.outputUrl}${current.outputUrl.includes("?") ? "&" : "?"}v=${current.id}`
    : null;
  const referenceImageUrls = (current.referenceImageUrls?.length
    ? current.referenceImageUrls
    : current.referenceImageUrl
      ? [current.referenceImageUrl]
      : []
  ).map((url) => `${url}${url.includes("?") ? "&" : "?"}ref=${current.id}`);
  const referenceImageSrc = referenceImageUrls[0] ?? null;
  const referenceVideoSrc = current.referenceVideoUrl
    ? `${current.referenceVideoUrl}${current.referenceVideoUrl.includes("?") ? "&" : "?"}ref=${current.id}`
    : null;
  const isImageToImage = current.mode === "image" && current.generationMode === "image-to-image";
  const isImageToVideo = current.mode === "video" && (
    current.generationMode === "image-to-video"
    || current.generationMode === "reference-to-video"
  );
  const isVideoToVideo = current.mode === "video" && current.generationMode === "video-to-video";

  return (
    <div
      className={`fixed inset-0 flex items-center justify-center p-4 ${stacked ? "z-[60]" : "z-50"}`}
      role="dialog"
      aria-modal="true"
    >
      <button
        type="button"
        className={`absolute inset-0 backdrop-blur-sm ${stacked ? "bg-black/40" : "bg-black/65"}`}
        onClick={onClose}
        aria-label={t("common.close")}
      />
      <div className="relative z-10 flex max-h-[90vh] w-full max-w-4xl flex-col overflow-hidden rounded-2xl border border-[var(--df-hairline)] bg-[var(--df-surface)] text-[var(--df-text)] shadow-2xl">
        <div className="flex items-center justify-between border-b border-[var(--df-hairline)] bg-[var(--df-surface-2)] px-5 py-4">
          <div>
            <p className="text-sm font-semibold text-[var(--df-text)]">{getCreationTypeLabel(current, locale)}</p>
            <p className="mt-0.5 text-xs text-[var(--df-text-4)]">{formatCreationTime(current.createdAt, locale)}</p>
          </div>
          <button
            type="button"
            onClick={onClose}
            aria-label={t("common.close")}
            className="rounded-lg p-2 text-[var(--df-text-4)] hover:bg-white/5 hover:text-[var(--df-text-2)]"
          >
            <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
        </div>

        <div className="flex-1 overflow-y-auto bg-[var(--df-surface)] p-5">
          <div className="mb-4 flex flex-wrap gap-2 text-xs text-[var(--df-text-3)]">
            <span className="rounded-full bg-[var(--df-surface-3)] px-2.5 py-1">{current.model}</span>
            {current.ratio && <span className="rounded-full bg-[var(--df-surface-3)] px-2.5 py-1">{t("creator.ratioLabel", { value: current.ratio })}</span>}
            {current.strength != null && (
              <span className="rounded-full bg-[var(--df-surface-3)] px-2.5 py-1">{t("creator.referenceStrength", { value: Math.round(current.strength * 100) })}</span>
            )}
            <span className="rounded-full bg-[var(--df-surface-3)] px-2.5 py-1">
              {isCompleted ? t("creator.statusCompleted") : isFailed ? t("creator.statusFailed") : t("creator.statusGenerating")}
            </span>
          </div>

          {loading && (
            <div className="mb-4 flex items-center gap-2 text-xs text-[var(--df-text-4)]">
              <div className="h-4 w-4 animate-spin rounded-full border-2 border-[var(--df-teal)]/20 border-t-[var(--df-teal)]" />
              {t("creator.loadingDetails")}
            </div>
          )}

          {isFailed && (
            <div
              className="mb-4 rounded-xl border p-4 text-sm"
              style={{ borderColor: "#fecdd3", backgroundColor: "#fff1f2", color: "#9f1239" }}
            >
              {current.errorMessage ?? t("creator.failureRetry")}
            </div>
          )}

          {/* 图生视频 / 资产参考 / 视频生视频：优先展示输入媒体（含生成中） */}
          {current.mode === "video" && (isImageToVideo || isVideoToVideo || referenceImageUrls.length > 0 || !!referenceVideoSrc) && (
            <div className="mb-4 grid gap-3 sm:grid-cols-2">
              <div>
                <p className="mb-2 text-xs font-medium text-[var(--df-text-3)]">
                  {isVideoToVideo || (!!referenceVideoSrc && referenceImageUrls.length === 0)
                    ? t("creator.inputVideo")
                    : referenceImageUrls.length > 1
                      ? t("creator.inputImages", { count: referenceImageUrls.length })
                      : t("creator.inputImage")}
                </p>
                <div className="overflow-hidden rounded-xl bg-[var(--df-surface-2)] p-2">
                  {referenceImageUrls.length > 0 ? (
                    <div className={`grid gap-2 ${referenceImageUrls.length > 1 ? "grid-cols-2" : "grid-cols-1"}`}>
                      {referenceImageUrls.map((src, index) => (
                        // eslint-disable-next-line @next/next/no-img-element
                        <img
                          key={`${src}-${index}`}
                          src={src}
                          alt={t("creator.inputImageAlt", { index: index + 1 })}
                          className={`w-full rounded-lg object-contain bg-[var(--df-bg)] ${referenceImageUrls.length > 1 ? "max-h-[22vh]" : "max-h-[40vh]"}`}
                          title={`Image ${index + 1}`}
                        />
                      ))}
                    </div>
                  ) : referenceVideoSrc ? (
                    <video src={referenceVideoSrc} controls className="max-h-[40vh] w-full" playsInline />
                  ) : (
                    <div className="flex h-40 items-center justify-center text-xs text-[var(--df-text-4)]">
                      {t("creator.missingInputMedia")}
                    </div>
                  )}
                </div>
              </div>
              <div>
                <p className="mb-2 text-xs font-medium text-[var(--df-text-3)]">
                  {isRunning ? t("creator.generationProgressLabel") : t("creator.generatedVideo")}
                </p>
                {isRunning ? (
                  <div className="flex h-full min-h-[10rem] flex-col items-center justify-center gap-3 rounded-xl bg-[var(--df-surface-2)] py-8">
                    <div className="h-10 w-10 animate-spin rounded-full border-2 border-[var(--df-teal)]/20 border-t-[var(--df-teal)]" />
                    <p className="text-sm text-[var(--df-text-3)]">{t("creator.generationProgress", { progress: current.progress ?? 0 })}</p>
                  </div>
                ) : mediaSrc ? (
                  <div className="overflow-hidden rounded-xl bg-black">
                    <video src={mediaSrc} controls className="max-h-[40vh] w-full" playsInline />
                  </div>
                ) : (
                  <div className="flex h-40 items-center justify-center rounded-xl bg-[var(--df-surface-2)] text-xs text-[var(--df-text-4)]">
                    {t("creator.noGenerationResult")}
                  </div>
                )}
              </div>
            </div>
          )}

          {/* 图生图：始终展示输入图（含生成中） */}
          {isImageToImage && (
            <div className="mb-4 grid gap-3 sm:grid-cols-2">
              <div>
                <p className="mb-2 text-xs font-medium text-[var(--df-text-3)]">{t("creator.inputImage")}</p>
                <div className="overflow-hidden rounded-xl bg-[var(--df-surface-2)]">
                  {referenceImageSrc ? (
                    // eslint-disable-next-line @next/next/no-img-element
                    <img src={referenceImageSrc} alt={t("creator.inputImage")} className="mx-auto max-h-[40vh] w-full object-contain" />
                  ) : (
                    <div className="flex h-40 items-center justify-center text-xs text-[var(--df-text-4)]">{t("creator.missingInputImage")}</div>
                  )}
                </div>
              </div>
              <div>
                <p className="mb-2 text-xs font-medium text-[var(--df-text-3)]">
                  {isRunning ? t("creator.generationProgressLabel") : t("creator.generationResult")}
                </p>
                {isRunning ? (
                  <div className="flex h-full min-h-[10rem] flex-col items-center justify-center gap-3 rounded-xl bg-[var(--df-surface-2)] py-8">
                    <div className="h-10 w-10 animate-spin rounded-full border-2 border-[var(--df-teal)]/20 border-t-[var(--df-teal)]" />
                    <p className="text-sm text-[var(--df-text-3)]">{t("creator.generationProgress", { progress: current.progress ?? 0 })}</p>
                  </div>
                ) : mediaSrc ? (
                  <div className="overflow-hidden rounded-xl bg-[var(--df-surface-2)]">
                    {/* eslint-disable-next-line @next/next/no-img-element */}
                    <img src={mediaSrc} alt={current.prompt} className="mx-auto max-h-[40vh] w-full object-contain" />
                  </div>
                ) : (
                  <div className="flex h-40 items-center justify-center rounded-xl bg-[var(--df-surface-2)] text-xs text-[var(--df-text-4)]">
                    {t("creator.noGenerationResult")}
                  </div>
                )}
              </div>
            </div>
          )}

          {isRunning && !isImageToImage && !(current.mode === "video" && (isImageToVideo || isVideoToVideo || !!referenceImageSrc || !!referenceVideoSrc)) && (
            <div className="mb-4 flex flex-col items-center justify-center gap-3 rounded-xl bg-[var(--df-surface-2)] py-12">
              <div className="h-10 w-10 animate-spin rounded-full border-2 border-[var(--df-teal)]/20 border-t-[var(--df-teal)]" />
              <p className="text-sm text-[var(--df-text-3)]">{t("creator.generationProgress", { progress: current.progress ?? 0 })}</p>
            </div>
          )}

          <div className="mb-4 rounded-xl border border-[var(--df-hairline)] bg-[var(--df-surface-2)] p-3">
            <p className="mb-1 text-xs font-medium text-[var(--df-text-3)]">{t("creator.promptLabel")}</p>
            <pre className="whitespace-pre-wrap break-words font-sans text-sm leading-relaxed text-[var(--df-text-2)]">
              {current.prompt}
            </pre>
          </div>

          {current.mode === "video" && mediaSrc && !isRunning && !isImageToVideo && !isVideoToVideo && !referenceImageSrc && !referenceVideoSrc && (
            <div className="overflow-hidden rounded-xl bg-black">
              <video src={mediaSrc} controls autoPlay className="max-h-[50vh] w-full" playsInline />
            </div>
          )}

          {current.mode === "image" && mediaSrc && !isRunning && !isImageToImage && (
            <div className="overflow-hidden rounded-xl bg-[var(--df-surface-2)]">
              {/* eslint-disable-next-line @next/next/no-img-element */}
              <img src={mediaSrc} alt={current.prompt} className="mx-auto max-h-[50vh] w-full object-contain" />
            </div>
          )}

          {current.mode === "prompt" && current.outputText && (
            <div className="rounded-xl border border-[var(--df-hairline)] bg-[var(--df-surface-2)] p-4">
              <p className="mb-2 text-xs font-medium text-[var(--df-text-3)]">{t("creator.optimizedResult")}</p>
              <pre className="whitespace-pre-wrap break-words font-sans text-sm leading-relaxed text-[var(--df-text-2)]">
                {current.outputText}
              </pre>
            </div>
          )}
        </div>

        <div className="flex gap-2 border-t border-[var(--df-hairline)] bg-[var(--df-surface-2)] px-5 py-4">
          {onDelete && (
            <button
              type="button"
              onClick={() => onDelete(current)}
              className="rounded-xl border border-[var(--df-danger)]/40 px-4 py-2.5 text-sm text-[var(--df-danger)] hover:bg-[var(--df-danger)]/10"
            >
              {t("common.delete")}
            </button>
          )}
          <div className="flex flex-1 justify-end gap-2">
            {current.mode === "prompt" && current.outputText && (
              <button
                type="button"
                onClick={handleCopyText}
                className="rounded-xl border border-[var(--df-hairline)] px-4 py-2.5 text-sm text-[var(--df-text-2)] hover:bg-white/5"
              >
                {t("creator.copyText")}
              </button>
            )}
            {canDownload && (
              <button
                type="button"
                onClick={handleDownload}
                className="rounded-xl bg-[var(--df-accent)] px-4 py-2.5 text-sm font-medium text-[#17131f] hover:brightness-110"
              >
                {t("creator.download")}
              </button>
            )}
            {current.outputUrl && current.mode !== "prompt" && (
              <a
                href={current.outputUrl}
                target="_blank"
                rel="noopener noreferrer"
                className="rounded-xl border border-[var(--df-hairline)] px-4 py-2.5 text-sm text-[var(--df-text-2)] hover:bg-white/5"
              >
                {t("creator.openNewWindow")}
              </a>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
