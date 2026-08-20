import type {
  AdminStats,
  ApiResponse,
  AuthData,
  CanvasData,
  CreateImageGenerationInput,
  CreateProjectInput,
  CreateTextGenerationInput,
  CreateVideoGenerationInput,
  CreationStats,
  GenerationJobRecord,
  GenerationMediaType,
  ImageGenerationJob,
  ImageModelInfo,
  LoginInput,
  PageResult,
  Project,
  RegisterInput,
  TextGenerationJob,
  TextModelInfo,
  User,
  VideoGenerationJob,
  VideoModelInfo,
  UploadResult,
} from "@dreamreel/shared-types";
import { getAuthToken, clearAuthSession } from "./auth";
import { getArkApiKey, getTokenfreeApiKey, resolveApiKeyHeader } from "./api-key";
import { getApiBase } from "./api-base";
import { extractApiErrorMessage, ApiError } from "./api-error";
const API_KEY_HEADER = "X-Tokenfree-Api-Key";
const ARK_API_KEY_HEADER = "X-Ark-Api-Key";

type RequestOptions = RequestInit & {
  apiKey?: string | null;
  arkApiKey?: string | null;
  auth?: boolean;
};

/** True when 401 means our JWT/session failed — not an upstream provider key error. */
function isSessionUnauthorizedMessage(message: string): boolean {
  if (!message) return true;
  if (message === "请先登录" || message === "未授权" || message.includes("登录已过期")) {
    return true;
  }
  // Provider errors that historically leaked as HTTP 401
  const lower = message.toLowerCase();
  if (
    lower.includes("invalid token") ||
    lower.includes("api key") ||
    message.includes("第三方") ||
    message.includes("TokenFree") ||
    message.includes("火山方舟")
  ) {
    return false;
  }
  return true;
}

function buildHeaders(init?: RequestOptions): HeadersInit {
  const headers: Record<string, string> = {
    "Content-Type": "application/json",
    ...(init?.headers as Record<string, string> | undefined),
  };

  const token = getAuthToken();
  if (token && init?.auth !== false) {
    headers.Authorization = `Bearer ${token}`;
  }

  const apiKey = resolveApiKeyHeader(init?.apiKey ?? getTokenfreeApiKey());
  if (apiKey) {
    headers[API_KEY_HEADER] = apiKey;
  }

  const arkApiKey = resolveApiKeyHeader(init?.arkApiKey ?? getArkApiKey());
  if (arkApiKey) {
    headers[ARK_API_KEY_HEADER] = arkApiKey;
  }

  return headers;
}

export async function request<T>(path: string, init?: RequestOptions): Promise<ApiResponse<T>> {
  const { apiKey: _apiKey, arkApiKey: _arkApiKey, auth: _auth, ...fetchInit } = init ?? {};
  const res = await fetch(`${getApiBase()}${path}`, {
    ...fetchInit,
    headers: buildHeaders(init),
  });

  if (res.status === 401 && init?.auth !== false) {
    const body = await res.json().catch(() => null);
    const message = typeof body?.message === "string" ? body.message.trim() : "";
    // Only wipe the session for our auth gateway; provider key errors must not log the user out.
    if (isSessionUnauthorizedMessage(message)) {
      clearAuthSession();
      if (typeof window !== "undefined") {
        window.dispatchEvent(new Event("auth:unauthorized"));
      }
      throw new Error(message || "登录已过期，请重新登录");
    }
    throw new ApiError(extractApiErrorMessage(body, res.status), res.status);
  }

  if (!res.ok && res.status !== 204) {
    const body = await res.json().catch(() => null);
    throw new ApiError(extractApiErrorMessage(body, res.status), res.status);
  }

  if (res.status === 204) {
    return { success: true, data: undefined as T, timestamp: new Date().toISOString() };
  }

  return res.json();
}

export async function login(input: LoginInput) {
  return request<AuthData>("/api/v1/auth/login", {
    method: "POST",
    body: JSON.stringify(input),
    auth: false,
  });
}

export async function register(input: RegisterInput) {
  return request<AuthData>("/api/v1/auth/register", {
    method: "POST",
    body: JSON.stringify(input),
    auth: false,
  });
}

export async function fetchMe() {
  return request<User>("/api/v1/auth/me", { cache: "no-store" });
}

export async function fetchCreationStats() {
  return request<CreationStats>("/api/v1/auth/me/creation-stats", { cache: "no-store" });
}

export async function updateTokenfreeKey(apiKey: string) {
  return request<User>("/api/v1/auth/me/tokenfree-key", {
    method: "PUT",
    body: JSON.stringify({ apiKey }),
  });
}

export async function updateArkKey(apiKey: string) {
  return request<User>("/api/v1/auth/me/ark-key", {
    method: "PUT",
    body: JSON.stringify({ apiKey }),
  });
}

export async function fetchHealth() {
  return request<{ status: string; service: string; version: string }>("/api/v1/health", {
    cache: "no-store",
    auth: false,
  });
}

export async function fetchProjects() {
  return request<Project[]>("/api/v1/projects", { cache: "no-store" });
}

export async function fetchProject(id: string, opts?: { summary?: boolean }) {
  const qs = opts?.summary ? "?summary=true" : "";
  return request<Project>(`/api/v1/projects/${id}${qs}`, { cache: "no-store" });
}

export async function createProject(input: CreateProjectInput) {
  return request<Project>("/api/v1/projects", {
    method: "POST",
    body: JSON.stringify(input),
  });
}

export async function deleteProject(id: string) {
  return request<void>(`/api/v1/projects/${id}`, { method: "DELETE" });
}

export async function saveProjectCanvas(id: string, canvas: CanvasData) {
  return request<Project>(`/api/v1/projects/${id}/canvas`, {
    method: "PATCH",
    body: JSON.stringify({ canvasData: JSON.stringify(canvas) }),
  });
}

export function parseCanvasData(raw?: string | null): CanvasData {
  if (!raw) {
    return { nodes: [], edges: [] };
  }
  try {
    const parsed = JSON.parse(raw) as CanvasData;
    return {
      nodes: Array.isArray(parsed.nodes) ? parsed.nodes : [],
      edges: Array.isArray(parsed.edges) ? parsed.edges : [],
    };
  } catch {
    return { nodes: [], edges: [] };
  }
}

export async function fetchGenerations(page = 0, size = 20, mediaType?: GenerationMediaType) {
  const params = new URLSearchParams({ page: String(page), size: String(size) });
  if (mediaType) params.set("mediaType", mediaType);
  return request<PageResult<GenerationJobRecord>>(`/api/v1/generations?${params}`, {
    cache: "no-store",
  });
}

export async function fetchGeneration(id: string) {
  return request<GenerationJobRecord>(`/api/v1/generations/${id}`, {
    cache: "no-store",
  });
}

export async function deleteGeneration(id: string) {
  return request<void>(`/api/v1/generations/${id}`, { method: "DELETE" });
}

export async function fetchVideoModels(apiKey?: string | null) {
  return request<VideoModelInfo[]>("/api/v1/video/models", {
    cache: "no-store",
    arkApiKey: apiKey,
  });
}

export async function createVideoGeneration(input: CreateVideoGenerationInput, apiKey?: string | null) {
  return request<VideoGenerationJob>("/api/v1/video/generations", {
    method: "POST",
    body: JSON.stringify(input),
    arkApiKey: apiKey,
  });
}

export async function fetchVideoGeneration(id: string, apiKey?: string | null) {
  return request<VideoGenerationJob>(`/api/v1/video/generations/${id}`, {
    cache: "no-store",
    arkApiKey: apiKey,
  });
}

export async function uploadMedia(file: File) {
  const formData = new FormData();
  formData.append("file", file);

  const headers: Record<string, string> = {};
  const token = getAuthToken();
  if (token) {
    headers.Authorization = `Bearer ${token}`;
  }

  const res = await fetch(`${getApiBase()}/api/v1/uploads`, {
    method: "POST",
    headers,
    body: formData,
  });

  if (!res.ok) {
    if (res.status === 401) {
      const body = await res.json().catch(() => null);
      const message = typeof body?.message === "string" ? body.message.trim() : "";
      if (isSessionUnauthorizedMessage(message)) {
        clearAuthSession();
        if (typeof window !== "undefined") {
          window.dispatchEvent(new Event("auth:unauthorized"));
        }
        throw new Error(message || "登录已过期，请重新登录");
      }
      throw new Error(extractApiErrorMessage(body, res.status));
    }
    const body = await res.json().catch(() => null);
    throw new Error(body?.message ?? `上传失败: ${res.status}`);
  }

  return res.json() as Promise<ApiResponse<UploadResult>>;
}

export async function fetchImageModels(apiKey?: string | null) {
  return request<ImageModelInfo[]>("/api/v1/image/models", {
    cache: "no-store",
    apiKey,
  });
}

export async function createImageGeneration(input: CreateImageGenerationInput, apiKey?: string | null) {
  return request<ImageGenerationJob>("/api/v1/image/generations", {
    method: "POST",
    body: JSON.stringify(input),
    apiKey,
  });
}

export async function fetchTextModels(apiKey?: string | null) {
  return request<TextModelInfo[]>("/api/v1/text/models", {
    cache: "no-store",
    apiKey,
  });
}

export async function createTextGeneration(input: CreateTextGenerationInput, apiKey?: string | null) {
  return request<TextGenerationJob>("/api/v1/text/generations", {
    method: "POST",
    body: JSON.stringify(input),
    apiKey,
  });
}

export async function fetchAdminStats() {
  return request<AdminStats>("/api/v1/admin/stats", { cache: "no-store" });
}

export async function fetchAdminUsers(page = 0, size = 20, keyword?: string) {
  const params = new URLSearchParams({ page: String(page), size: String(size) });
  if (keyword) params.set("keyword", keyword);
  return request<PageResult<User>>(`/api/v1/admin/users?${params}`, { cache: "no-store" });
}

export async function fetchAdminProjects(page = 0, size = 20) {
  const params = new URLSearchParams({ page: String(page), size: String(size) });
  return request<PageResult<Project>>(`/api/v1/admin/projects?${params}`, { cache: "no-store" });
}

export async function fetchAdminGenerations(page = 0, size = 20, mediaType?: GenerationMediaType) {
  const params = new URLSearchParams({ page: String(page), size: String(size) });
  if (mediaType) params.set("mediaType", mediaType);
  return request<PageResult<GenerationJobRecord>>(`/api/v1/admin/generations?${params}`, {
    cache: "no-store",
  });
}

export async function updateAdminUser(id: string, body: { status?: "ACTIVE" | "DISABLED"; displayName?: string }) {
  return request<User>(`/api/v1/admin/users/${id}`, {
    method: "PATCH",
    body: JSON.stringify(body),
  });
}
