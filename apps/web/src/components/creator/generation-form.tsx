"use client";

import { useRef, useState } from "react";
import type {
  AspectRatio,
  ImageSubMode,
  MediaQuality,
  VideoSubMode,
} from "@dreamreel/shared-types";
import { uploadMedia } from "@/lib/api";
import { DfSelect } from "@/components/ui/df-select";

type GenerationMode = "video" | "image" | "prompt";

const PROMPT_MAX = 2000;

const VIDEO_SUB_MODES: { id: VideoSubMode; label: string }[] = [
  { id: "text-to-video", label: "文生视频" },
  { id: "image-to-video", label: "图生视频" },
  { id: "video-to-video", label: "视频生视频" },
];

const IMAGE_SUB_MODES: { id: ImageSubMode; label: string }[] = [
  { id: "text-to-image", label: "文生图" },
  { id: "image-to-image", label: "图生图" },
];

const RATIO_OPTIONS: { id: AspectRatio; label: string }[] = [
  { id: "16:9", label: "16:9" },
  { id: "9:16", label: "9:16" },
  { id: "1:1", label: "1:1" },
  { id: "4:3", label: "4:3" },
  { id: "3:4", label: "3:4" },
];

const DURATION_OPTIONS = [2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15];

const MODE_OPTIONS: { value: GenerationMode; label: string }[] = [
  { value: "video", label: "视频" },
  { value: "image", label: "图片" },
  { value: "prompt", label: "提示词" },
];

export interface GenerationFormValues {
  mode: GenerationMode;
  videoSubMode: VideoSubMode;
  imageSubMode: ImageSubMode;
  prompt: string;
  model: string;
  ratio: AspectRatio;
  quality: MediaQuality;
  seconds: number;
  strength: number;
  referenceUrl: string;
}

interface GenerationFormProps {
  values: GenerationFormValues;
  models: string[];
  loading: boolean;
  loadingModels: boolean;
  error: string | null;
  onChange: (patch: Partial<GenerationFormValues>) => void;
  onGenerate: () => void;
  /** 暗色产品壳内使用，默认 true 以匹配 DramaForge 设计稿 */
  dark?: boolean;
}

function WandIcon({ className }: { className?: string }) {
  return (
    <svg className={className} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8">
      <path strokeLinecap="round" strokeLinejoin="round" d="M15 4l5 5M9.5 9.5L4 15v5h5l5.5-5.5M14 6l4 4M9.5 14.5L6 18" />
      <path strokeLinecap="round" strokeLinejoin="round" d="M3 21l3-3" />
    </svg>
  );
}

function SparkleIcon({ className }: { className?: string }) {
  return (
    <svg className={className} viewBox="0 0 24 24" fill="currentColor">
      <path d="M12 2l1.2 4.8L18 8l-4.8 1.2L12 14l-1.2-4.8L6 8l4.8-1.2L12 2z" />
    </svg>
  );
}

export function GenerationForm({
  values,
  models,
  loading,
  loadingModels,
  error,
  onChange,
  onGenerate,
  dark = true,
}: GenerationFormProps) {
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [uploading, setUploading] = useState(false);
  const [uploadError, setUploadError] = useState<string | null>(null);
  const [showAdvanced, setShowAdvanced] = useState(false);

  const needsImageReference =
    values.mode === "image"
      ? values.imageSubMode === "image-to-image"
      : values.videoSubMode === "image-to-video";
  const needsVideoReference = values.mode === "video" && values.videoSubMode === "video-to-video";

  const panelTitle =
    values.mode === "video"
      ? VIDEO_SUB_MODES.find((item) => item.id === values.videoSubMode)?.label ?? "视频生成"
      : values.mode === "image"
        ? IMAGE_SUB_MODES.find((item) => item.id === values.imageSubMode)?.label ?? "图片生成"
        : "提示词优化";

  async function handleUpload(file: File) {
    setUploading(true);
    setUploadError(null);
    try {
      const res = await uploadMedia(file);
      onChange({ referenceUrl: res.data.url });
    } catch (e) {
      setUploadError(e instanceof Error ? e.message : "上传失败");
    } finally {
      setUploading(false);
    }
  }

  const chip = (active: boolean) =>
    dark
      ? active
        ? "bg-[var(--df-accent)] text-white shadow-[0_0_16px_rgba(124,77,255,0.35)]"
        : "bg-white/5 text-[var(--df-text-3)] hover:bg-white/10 hover:text-[var(--df-text)]"
      : active
        ? "bg-brand text-white shadow-sm"
        : "bg-zinc-50 text-zinc-600 hover:bg-brand-light hover:text-brand";

  return (
    <section
      className={
        dark
          ? "rounded-2xl border border-[var(--df-hairline)] bg-[rgba(22,22,37,0.92)] p-5 sm:p-6"
          : "rounded-2xl border border-zinc-100 bg-white p-5 shadow-[0_2px_16px_rgba(0,0,0,0.06)] sm:p-6"
      }
    >
      <div className="mb-5 flex items-center justify-between">
        <div className="flex items-center gap-2">
          <WandIcon className={`h-5 w-5 ${dark ? "text-[var(--df-accent-2)]" : "text-brand"}`} />
          <h2 className={`text-base font-semibold ${dark ? "text-[var(--df-text)]" : "text-zinc-800"}`}>
            {panelTitle}
          </h2>
        </div>
        <div
          className={`flex items-center gap-1.5 text-xs ${
            dark ? "text-[var(--df-text-3)]" : "text-zinc-500"
          }`}
        >
          <span>内容类型</span>
          <DfSelect
            size="sm"
            searchable={false}
            variant={dark ? "dark" : "light"}
            className="min-w-[100px]"
            value={values.mode}
            onChange={(mode) => onChange({ mode: mode as GenerationMode })}
            options={MODE_OPTIONS.map((m) => ({ value: m.value, label: m.label }))}
          />
        </div>
      </div>

      {values.mode === "video" && (
        <div className="mb-4 flex flex-wrap gap-2">
          {VIDEO_SUB_MODES.map((item) => (
            <button
              key={item.id}
              type="button"
              onClick={() => onChange({ videoSubMode: item.id, referenceUrl: "" })}
              className={`rounded-lg px-3 py-1.5 text-xs font-medium transition-all ${chip(
                values.videoSubMode === item.id,
              )}`}
            >
              {item.label}
            </button>
          ))}
        </div>
      )}

      {values.mode === "image" && (
        <div className="mb-4 flex flex-wrap gap-2">
          {IMAGE_SUB_MODES.map((item) => (
            <button
              key={item.id}
              type="button"
              onClick={() => onChange({ imageSubMode: item.id, referenceUrl: "" })}
              className={`rounded-lg px-3 py-1.5 text-xs font-medium transition-all ${chip(
                values.imageSubMode === item.id,
              )}`}
            >
              {item.label}
            </button>
          ))}
        </div>
      )}

      <div className="mb-4">
        <DfSelect
          searchable
          variant={dark ? "dark" : "light"}
          className="w-full"
          value={values.model}
          onChange={(model) => onChange({ model })}
          disabled={loadingModels || models.length === 0}
          placeholder={
            loadingModels ? "加载模型中…" : models.length === 0 ? "暂无可用模型" : "选择模型"
          }
          options={models.map((m) => ({ value: m, label: m }))}
        />
      </div>

      {(needsImageReference || needsVideoReference) && (
        <div className="mb-4">
          <input
            ref={fileInputRef}
            type="file"
            accept={needsVideoReference ? "video/mp4,video/webm,video/quicktime" : "image/*"}
            className="hidden"
            onChange={(e) => {
              const file = e.target.files?.[0];
              if (file) void handleUpload(file);
              e.target.value = "";
            }}
          />
          <div
            role="button"
            tabIndex={0}
            onClick={() => fileInputRef.current?.click()}
            onKeyDown={(e) => e.key === "Enter" && fileInputRef.current?.click()}
            className={
              dark
                ? "flex min-h-[100px] cursor-pointer flex-col items-center justify-center rounded-xl border border-dashed border-[var(--df-hairline-strong)] bg-black/30 px-4 py-5 text-center transition hover:border-[var(--df-accent-soft)]"
                : "flex min-h-[100px] cursor-pointer flex-col items-center justify-center rounded-xl border border-dashed border-zinc-200 bg-zinc-50 px-4 py-5 text-center transition-colors hover:border-brand hover:bg-brand-light/30"
            }
          >
            {values.referenceUrl ? (
              needsVideoReference ? (
                <video
                  src={values.referenceUrl}
                  controls
                  className="max-h-36 w-full rounded-lg object-contain"
                  onClick={(e) => e.stopPropagation()}
                />
              ) : (
                // eslint-disable-next-line @next/next/no-img-element
                <img
                  src={values.referenceUrl}
                  alt="参考图"
                  className="max-h-36 w-full rounded-lg object-contain"
                />
              )
            ) : (
              <>
                <svg
                  className={`h-8 w-8 ${dark ? "text-[var(--df-text-4)]" : "text-zinc-300"}`}
                  fill="none"
                  viewBox="0 0 24 24"
                  stroke="currentColor"
                >
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M12 4v16m8-8H4" />
                </svg>
                <span className={`mt-2 text-xs ${dark ? "text-[var(--df-text-4)]" : "text-zinc-400"}`}>
                  {uploading ? "上传中..." : `点击上传${needsVideoReference ? "参考视频" : "参考图片"}`}
                </span>
              </>
            )}
          </div>
          {values.referenceUrl && (
            <button
              type="button"
              onClick={() => onChange({ referenceUrl: "" })}
              className={`mt-1.5 text-xs ${dark ? "text-[var(--df-text-4)] hover:text-[var(--df-danger)]" : "text-zinc-400 hover:text-red-500"}`}
            >
              清除
            </button>
          )}
          {uploadError && (
            <p className={`mt-1.5 text-xs ${dark ? "text-[var(--df-danger)]" : "text-red-500"}`}>
              {uploadError}
            </p>
          )}
        </div>
      )}

      <div className="relative mb-4">
        <textarea
          value={values.prompt}
          onChange={(e) => onChange({ prompt: e.target.value.slice(0, PROMPT_MAX) })}
          rows={5}
          placeholder={
            values.mode === "video"
              ? "描述你想生成的视频，例如：阳光花园里有只猫在玩耍，自然光，清新氛围……"
              : values.mode === "image"
                ? "描述你想生成的图片，例如：赛博朋克风格的城市夜景，霓虹灯光……"
                : "输入简短描述，AI 将为你生成专业的中文提示词……"
          }
          className={
            dark
              ? "df-input w-full resize-none px-4 py-3.5 text-sm leading-relaxed placeholder:text-[var(--df-text-4)]"
              : "w-full resize-none rounded-xl border border-zinc-200 bg-zinc-50 px-4 py-3.5 text-sm leading-relaxed text-zinc-800 outline-none transition-colors placeholder:text-zinc-400 focus:border-brand focus:bg-white"
          }
        />
        <span
          className={`absolute bottom-3 right-3 text-[11px] ${
            dark ? "text-[var(--df-text-4)]" : "text-zinc-400"
          }`}
        >
          {values.prompt.length} / {PROMPT_MAX}
        </span>
      </div>

      {values.mode !== "prompt" && (
        <div className="mb-4">
          <button
            type="button"
            onClick={() => setShowAdvanced(!showAdvanced)}
            className={`flex items-center gap-1 text-xs ${
              dark
                ? "text-[var(--df-text-3)] hover:text-[var(--df-accent-2)]"
                : "text-zinc-500 hover:text-brand"
            }`}
          >
            <svg
              className={`h-3.5 w-3.5 transition-transform ${showAdvanced ? "rotate-180" : ""}`}
              fill="none"
              viewBox="0 0 24 24"
              stroke="currentColor"
            >
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 9l-7 7-7-7" />
            </svg>
            {showAdvanced ? "收起参数" : "展开参数（比例 / 时长 / 清晰度）"}
          </button>

          {showAdvanced && (
            <div
              className={
                dark
                  ? "mt-3 space-y-4 rounded-xl border border-[var(--df-hairline)] bg-black/25 p-4"
                  : "mt-3 space-y-4 rounded-xl border border-zinc-100 bg-zinc-50/50 p-4"
              }
            >
              <div>
                <span
                  className={`mb-2 block text-xs font-medium ${
                    dark ? "text-[var(--df-text-3)]" : "text-zinc-500"
                  }`}
                >
                  画布比例
                </span>
                <div className="flex flex-wrap gap-2">
                  {RATIO_OPTIONS.map((item) => (
                    <button
                      key={item.id}
                      type="button"
                      onClick={() => onChange({ ratio: item.id })}
                      className={`rounded-lg px-3 py-1.5 text-xs font-medium transition-all ${chip(
                        values.ratio === item.id,
                      )}`}
                    >
                      {item.label}
                    </button>
                  ))}
                </div>
              </div>

              {values.mode === "video" && (
                <div>
                  <span
                    className={`mb-2 block text-xs font-medium ${
                      dark ? "text-[var(--df-text-3)]" : "text-zinc-500"
                    }`}
                  >
                    视频时长
                  </span>
                  <div className="flex flex-wrap gap-1.5">
                    {DURATION_OPTIONS.map((item) => (
                      <button
                        key={item}
                        type="button"
                        onClick={() => onChange({ seconds: item })}
                        className={`rounded-lg px-2.5 py-1 text-xs font-medium transition-all ${chip(
                          values.seconds === item,
                        )}`}
                      >
                        {item}s
                      </button>
                    ))}
                  </div>
                </div>
              )}

              <div>
                <span
                  className={`mb-2 block text-xs font-medium ${
                    dark ? "text-[var(--df-text-3)]" : "text-zinc-500"
                  }`}
                >
                  清晰度
                </span>
                <div className="flex gap-2">
                  {(["480p", "720p", "1080p"] as MediaQuality[]).map((item) => (
                    <button
                      key={item}
                      type="button"
                      onClick={() => onChange({ quality: item })}
                      className={`rounded-lg px-4 py-1.5 text-xs font-medium uppercase transition-all ${chip(
                        values.quality === item,
                      )}`}
                    >
                      {item}
                    </button>
                  ))}
                </div>
              </div>

              {values.mode === "image" && values.imageSubMode === "image-to-image" && (
                <div>
                  <div className="mb-2 flex items-center justify-between">
                    <span
                      className={`text-xs font-medium ${
                        dark ? "text-[var(--df-text-3)]" : "text-zinc-500"
                      }`}
                    >
                      参考强度
                    </span>
                    <span className={`text-xs ${dark ? "text-[var(--df-accent-2)]" : "text-brand"}`}>
                      {values.strength.toFixed(2)}
                    </span>
                  </div>
                  <input
                    type="range"
                    min={0}
                    max={1}
                    step={0.05}
                    value={values.strength}
                    onChange={(e) => onChange({ strength: Number(e.target.value) })}
                    className="w-full accent-[var(--df-accent)]"
                  />
                </div>
              )}
            </div>
          )}
        </div>
      )}

      <button
        type="button"
        onClick={onGenerate}
        disabled={loading || !values.prompt.trim()}
        className={
          dark
            ? "df-btn-accent mt-2 flex w-full items-center justify-center gap-2 py-3.5 text-sm disabled:cursor-not-allowed disabled:opacity-40"
            : "mt-2 flex w-full items-center justify-center gap-2 rounded-xl bg-[#00c091] py-3.5 text-sm font-semibold text-white shadow-sm transition-colors hover:bg-[#00a87d] disabled:cursor-not-allowed disabled:bg-zinc-300 disabled:opacity-100"
        }
      >
        {loading ? (
          <>
            <svg className="h-4 w-4 animate-spin" viewBox="0 0 24 24" fill="none">
              <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
              <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
            </svg>
            生成中...
          </>
        ) : (
          <>
            <SparkleIcon className="h-4 w-4 shrink-0" />
            <span>开始{panelTitle}</span>
          </>
        )}
      </button>

      {error && (
        <p
          className={
            dark
              ? "mt-3 rounded-xl bg-[rgba(248,113,113,0.12)] px-3 py-2 text-xs text-[var(--df-danger)]"
              : "mt-3 rounded-xl bg-red-50 px-3 py-2 text-xs text-red-600"
          }
        >
          {error}
        </p>
      )}
    </section>
  );
}
