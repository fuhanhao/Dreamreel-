/** 画布节点类型 */
export type CanvasNodeType =
  | "text"
  | "script"
  | "image"
  | "video"
  | "audio"
  | "compose";

/** 节点运行状态 */
export type NodeStatus = "idle" | "queued" | "running" | "success" | "failed";

/** 项目类型（与后端枚举对应） */
export type ProjectType =
  | "SHORT_DRAMA"
  | "COMIC_DRAMA"
  | "AD"
  | "CUSTOM";

export const PROJECT_TYPE_LABELS: Record<ProjectType, string> = {
  SHORT_DRAMA: "竖屏短剧",
  COMIC_DRAMA: "漫剧",
  AD: "广告片",
  CUSTOM: "自定义",
};

export interface Project {
  id: string;
  name: string;
  type: ProjectType;
  description?: string | null;
  canvasData?: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface CanvasData {
  nodes: unknown[];
  edges: unknown[];
}

export interface CreateProjectInput {
  name: string;
  type: ProjectType;
  description?: string;
}

export interface CanvasNodeData {
  label: string;
  nodeType: CanvasNodeType;
  status: NodeStatus;
  config?: Record<string, unknown>;
  outputUrl?: string;
}

export interface ApiResponse<T> {
  success: boolean;
  data: T;
  message?: string;
  timestamp: string;
}

export interface HealthData {
  status: string;
  service: string;
  version: string;
}

/** 支持的视频模型（TokenFree 动态列表） */
export type VideoModelId = string;

export type GenerationStatus = "QUEUED" | "IN_PROGRESS" | "COMPLETED" | "FAILED";

export interface VideoModelInfo {
  id: string;
  provider: string;
}

export interface VideoGenerationJob {
  id: string;
  projectId?: string | null;
  nodeId?: string | null;
  providerTaskId: string;
  model: string;
  prompt: string;
  status: GenerationStatus;
  progress?: number | null;
  outputUrl?: string | null;
  errorMessage?: string | null;
  createdAt: string;
  updatedAt: string;
}

export type AspectRatio = "16:9" | "9:16" | "1:1" | "4:3" | "3:4";
export type MediaQuality = "480p" | "720p" | "1080p";
export type VideoSubMode = "text-to-video" | "image-to-video" | "video-to-video";
export type ImageSubMode = "text-to-image" | "image-to-image";

export interface CreateImageGenerationInput {
  projectId?: string;
  nodeId?: string;
  model: string;
  prompt: string;
  ratio?: AspectRatio;
  quality?: MediaQuality;
  mode?: ImageSubMode;
  imageUrl?: string;
  strength?: number;
}

export interface ImageGenerationJob {
  id: string;
  projectId?: string | null;
  nodeId?: string | null;
  model: string;
  prompt: string;
  status: GenerationStatus;
  outputUrl?: string | null;
  errorMessage?: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface UploadResult {
  id: string;
  url: string;
  contentType: string;
  originalFilename: string;
}

export interface ImageModelInfo {
  id: string;
  provider: string;
}

export interface CreateVideoGenerationInput {
  projectId?: string;
  nodeId?: string;
  model: string;
  prompt: string;
  seconds?: number;
  ratio?: AspectRatio;
  quality?: MediaQuality;
  mode?: VideoSubMode;
  imageUrl?: string;
  videoUrl?: string;
  imageUrls?: string[];
  audioUrls?: string[];
}

export interface TextModelInfo {
  id: string;
  provider: string;
}

export interface CreateTextGenerationInput {
  projectId?: string;
  nodeId?: string;
  model: string;
  prompt: string;
  nodeType: "text" | "script" | "prompt";
  context?: string;
}

export interface TextGenerationJob {
  id: string;
  projectId?: string | null;
  nodeId?: string | null;
  model: string;
  prompt: string;
  nodeType: "text" | "script" | "prompt";
  status: GenerationStatus;
  outputText?: string | null;
  errorMessage?: string | null;
  createdAt: string;
  updatedAt: string;
}

/** @deprecated 使用 VideoModelId */
export type VideoModel =
  | "kling-3.0"
  | "seedance-2.0"
  | "wanxiang-2.6"
  | "runway-gen4";

export interface VideoGenParams {
  model: VideoModelId;
  prompt: string;
  imageUrl?: string;
  duration?: number;
  ratio?: "16:9" | "9:16" | "1:1";
}

export type UserRole = "USER" | "ADMIN";
export type UserStatus = "ACTIVE" | "DISABLED";

export interface User {
  id: string;
  email: string;
  displayName: string;
  role: UserRole;
  status: UserStatus;
  hasTokenfreeApiKey: boolean;
  hasArkApiKey?: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface AuthData {
  token: string;
  user: User;
}

export interface LoginInput {
  email: string;
  password: string;
}

export interface RegisterInput {
  email: string;
  password: string;
  displayName: string;
}

export type GenerationMediaType = "TEXT" | "IMAGE" | "VIDEO";

export interface GenerationJobRecord {
  id: string;
  userId?: string | null;
  projectId?: string | null;
  nodeId?: string | null;
  providerTaskId: string;
  model: string;
  mediaType: GenerationMediaType;
  prompt: string;
  status: GenerationStatus;
  progress?: number | null;
  outputUrl?: string | null;
  outputText?: string | null;
  errorMessage?: string | null;
  generationMode?: string | null;
  referenceImageUrl?: string | null;
  referenceImageUrls?: string[] | null;
  referenceVideoUrl?: string | null;
  ratio?: string | null;
  strength?: number | null;
  createdAt: string;
  updatedAt: string;
}

export interface PageResult<T> {
  items: T[];
  total: number;
  page: number;
  size: number;
}

export interface AdminStats {
  totalUsers: number;
  activeUsers: number;
  totalProjects: number;
  totalGenerations: number;
  completedGenerations: number;
  failedGenerations: number;
}

/** 当前用户「今日创作数据」（按 Asia/Shanghai 自然日） */
export interface CreationStats {
  projectCount: number;
  projectDeltaPercent: number;
  renderHours: number;
  renderDeltaPercent: number;
  videoCount: number;
  videoDeltaPercent: number;
  credits: number;
  creditsDeltaPercent: number;
}

export * from "./dramaforge";
