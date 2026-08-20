import type { GenerationJobRecord, ImageSubMode, VideoSubMode } from "@dreamreel/shared-types";
import type { Locale } from "@/i18n/translate";

export type CreationMode = "video" | "image" | "prompt";

export type CreationGenerationMode =
  | ImageSubMode
  | VideoSubMode
  | "reference-to-video"
  | "text"
  | "script"
  | "prompt";

export interface CreationItem {
  id: string;
  mode: CreationMode;
  prompt: string;
  model: string;
  status: "QUEUED" | "IN_PROGRESS" | "COMPLETED" | "FAILED";
  progress?: number | null;
  outputUrl?: string | null;
  outputText?: string | null;
  errorMessage?: string | null;
  generationMode?: CreationGenerationMode | null;
  referenceImageUrl?: string | null;
  referenceImageUrls?: string[] | null;
  referenceVideoUrl?: string | null;
  ratio?: string | null;
  strength?: number | null;
  createdAt: string;
}

export function mapCreationRecord(item: GenerationJobRecord): CreationItem {
  const mode: CreationMode =
    item.mediaType === "VIDEO" ? "video" : item.mediaType === "IMAGE" ? "image" : "prompt";
  const referenceImageUrls = normalizeReferenceImageUrls(item);
  return {
    id: item.id,
    mode,
    prompt: item.prompt,
    model: item.model,
    status: item.status,
    progress: item.progress,
    outputUrl: item.outputUrl,
    outputText: item.outputText,
    errorMessage: item.errorMessage,
    generationMode: (item.generationMode as CreationGenerationMode | null | undefined) ?? null,
    referenceImageUrl: referenceImageUrls[0] ?? item.referenceImageUrl ?? null,
    referenceImageUrls: referenceImageUrls.length > 0 ? referenceImageUrls : null,
    referenceVideoUrl: item.referenceVideoUrl,
    ratio: item.ratio,
    strength: item.strength,
    createdAt: item.createdAt,
  };
}

function normalizeReferenceImageUrls(item: GenerationJobRecord): string[] {
  const raw = item.referenceImageUrls as unknown;
  let urls: string[] = [];
  if (Array.isArray(raw)) {
    urls = raw.filter((u): u is string => typeof u === "string" && u.trim().length > 0);
  } else if (typeof raw === "string" && raw.trim()) {
    try {
      const parsed = JSON.parse(raw) as unknown;
      if (Array.isArray(parsed)) {
        urls = parsed.filter((u): u is string => typeof u === "string" && u.trim().length > 0);
      }
    } catch {
      // ignore
    }
  }
  if (urls.length === 0 && item.referenceImageUrl?.trim()) {
    urls = [item.referenceImageUrl.trim()];
  }
  return [...new Set(urls.map((u) => u.trim()))];
}

export function formatCreationTime(iso: string, locale: Locale = "zh") {
  try {
    const d = new Date(iso);
    const now = new Date();
    const diffMin = Math.floor((now.getTime() - d.getTime()) / 60000);
    if (diffMin < 1) return locale === "zh" ? "刚刚" : "Just now";
    if (diffMin < 60) return locale === "zh" ? `${diffMin} 分钟前` : `${diffMin} min ago`;
    const diffHour = Math.floor(diffMin / 60);
    if (diffHour < 24) return locale === "zh" ? `${diffHour} 小时前` : `${diffHour} hr ago`;
    return d.toLocaleDateString(locale === "zh" ? "zh-CN" : "en-US", {
      year: "numeric",
      month: "numeric",
      day: "numeric",
      hour: "2-digit",
      minute: "2-digit",
    });
  } catch {
    return "";
  }
}

export function getCreationTypeLabel(item: CreationItem, locale: Locale = "zh") {
  if (item.mode === "prompt") return locale === "zh" ? "提示词" : "Prompt";
  if (item.mode === "image") {
    return item.generationMode === "image-to-image"
      ? locale === "zh" ? "图生图" : "Image to image"
      : locale === "zh" ? "文生图" : "Text to image";
  }
  if (item.mode === "video") {
    if (item.generationMode === "image-to-video") return locale === "zh" ? "图生视频" : "Image to video";
    if (item.generationMode === "video-to-video") return locale === "zh" ? "视频生视频" : "Video to video";
    if (item.generationMode === "reference-to-video") return locale === "zh" ? "资产参考生视频" : "Asset reference to video";
    return locale === "zh" ? "文生视频" : "Text to video";
  }
  return locale === "zh" ? "创作" : "Creation";
}

export function getCreationTitle(prompt: string) {
  const first = prompt.split(/[\n。！？]/)[0]?.trim() ?? prompt;
  return first.length > 20 ? first.slice(0, 20) + "…" : first;
}

export async function downloadCreationFile(url: string, filename: string) {
  try {
    const res = await fetch(url);
    if (!res.ok) throw new Error("download failed");
    const blob = await res.blob();
    const objectUrl = URL.createObjectURL(blob);
    const anchor = document.createElement("a");
    anchor.href = objectUrl;
    anchor.download = filename;
    document.body.appendChild(anchor);
    anchor.click();
    anchor.remove();
    URL.revokeObjectURL(objectUrl);
  } catch {
    window.open(url, "_blank", "noopener,noreferrer");
  }
}

export function getDownloadFilename(item: CreationItem) {
  const base = item.id.slice(0, 8);
  if (item.mode === "video") return `dreamreel-${base}.mp4`;
  if (item.mode === "image") {
    const ext = item.outputUrl?.includes(".png") ? "png" : item.outputUrl?.includes(".webp") ? "webp" : "jpg";
    return `dreamreel-${base}.${ext}`;
  }
  return `dreamreel-${base}.txt`;
}
