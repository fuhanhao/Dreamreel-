/** 解析 API 基址：优先读容器运行时配置 runtime-env.js，其次本地 .env */
export function getApiBase(): string {
  if (typeof window !== "undefined") {
    const runtime = window.__RUNTIME_CONFIG__?.API_BASE_URL;
    if (runtime !== undefined) {
      return runtime.replace(/\/$/, "");
    }
  }

  const configured = process.env.NEXT_PUBLIC_API_URL?.replace(/\/$/, "");
  return configured ?? "http://localhost:7051";
}

/** 将相对 /api/... 媒体地址补成可播放的绝对 URL（img/audio/video src） */
export function resolveMediaUrl(url: string | null | undefined): string {
  if (!url) return "";
  const trimmed = url.trim();
  if (!trimmed) return "";
  if (/^(https?:|blob:|data:)/i.test(trimmed)) return trimmed;
  if (trimmed.startsWith("//")) return `https:${trimmed}`;
  const base = getApiBase();
  if (trimmed.startsWith("/")) return `${base}${trimmed}`;
  return `${base}/${trimmed}`;
}
