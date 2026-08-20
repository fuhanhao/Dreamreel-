/** DramaForge 内容模式：说书 / 剧集 / 广告 */
export type DramaForgeContentMode = "narration" | "drama" | "ad";

/** DramaForge 视频生成模式 */
export type DramaForgeGenerationMode =
  | "storyboard_to_video"
  | "image_to_video"
  | "grid_to_video"
  | "reference_to_video";

/** 5 步流水线阶段 */
export type DramaForgePipelineStage =
  | "story_input"
  | "script_locked"
  | "assets_locked"
  | "video_done"
  | "composed";

export const DRAMA_FORGE_PIPELINE_STAGES: {
  id: DramaForgePipelineStage;
  label: string;
  description: string;
  step: number;
}[] = [
  { id: "story_input", step: 1, label: "① 配置", description: "项目参数与原文（先配置再分集）" },
  { id: "script_locked", step: 2, label: "② 定剧本", description: "按集：正文→剧本→镜头并确认" },
  { id: "assets_locked", step: 3, label: "③ 建资产", description: "从剧本提取角色/场景/道具并定妆" },
  { id: "video_done", step: 4, label: "④ 出成片", description: "按集生成镜头视频" },
  { id: "composed", step: 5, label: "⑤ AI 剪辑", description: "时间轴编排与导出" },
];

export type DramaForgeWizardStep = DramaForgePipelineStage;

export function wizardStepIndex(stage: DramaForgePipelineStage): number {
  return DRAMA_FORGE_PIPELINE_STAGES.findIndex((s) => s.id === stage);
}

export function canAdvanceToStep(
  target: DramaForgeWizardStep,
  current: DramaForgePipelineStage,
): boolean {
  const targetIdx = wizardStepIndex(target);
  const currentIdx = wizardStepIndex(current);
  if (targetIdx < 0 || currentIdx < 0) return false;
  return targetIdx <= currentIdx + 1;
}

export type DramaForgeAssetType = "character" | "scene" | "prop";

export const DRAMA_FORGE_ASSET_TYPE_LABELS: Record<DramaForgeAssetType, string> = {
  character: "角色",
  scene: "场景",
  prop: "道具/线索",
};

export type DramaForgeShotStatus = "pending" | "storyboard_done" | "video_done" | "failed";

/** DramaForge 画面比例 */
export type DramaForgeAspectRatio = "9:16" | "16:9" | "1:1" | "4:3" | "3:4";

export const DRAMA_FORGE_ASPECT_RATIOS: { value: DramaForgeAspectRatio; label: string }[] = [
  { value: "9:16", label: "竖屏 9:16（抖音）" },
  { value: "16:9", label: "横屏 16:9（B站）" },
  { value: "3:4", label: "竖屏 3:4" },
  { value: "4:3", label: "横屏 4:3" },
  { value: "1:1", label: "方形 1:1" },
];

export const DRAMA_FORGE_EXPORT_PRESETS = [
  { id: "douyin_9_16", label: "抖音 9:16 1080p", aspectRatio: "9:16" as const, quality: "1080p" },
  { id: "bilibili_16_9", label: "B站 16:9 1080p", aspectRatio: "16:9" as const, quality: "1080p" },
] as const;

export function defaultDramaForgeAspectRatio(contentMode: DramaForgeContentMode): DramaForgeAspectRatio {
  return contentMode === "ad" ? "16:9" : "9:16";
}

export interface DramaForgeConfig {
  projectId: string;
  contentMode: DramaForgeContentMode;
  generationMode: DramaForgeGenerationMode;
  aspectRatio?: DramaForgeAspectRatio | null;
  imageBackend?: string | null;
  videoBackend?: string | null;
  textBackend?: string | null;
  imageQuality?: string | null;
  videoQuality?: string | null;
  stylePrompt?: string | null;
  sourceText?: string | null;
  projectSummary?: string | null;
  worldview?: string | null;
  colorGradePreset?: string | null;
  mixDialogueAudioInCompose?: boolean;
  bgmUrl?: string | null;
  bgmVolume?: number | null;
  lipSyncEnabled?: boolean;
  lipSyncEndpoint?: string | null;
  preferModelMultiShot?: boolean;
  assetsLockedAt?: string | null;
  storyboardLockedAt?: string | null;
  updatedAt: string;
}

export interface DramaForgeAsset {
  id: string;
  projectId: string;
  type: DramaForgeAssetType;
  name: string;
  description?: string | null;
  designPrompt?: string | null;
  referenceImageUrl?: string | null;
  voiceLabel?: string | null;
  voiceSampleUrl?: string | null;
  voiceSpeakerId?: string | null;
  identityLockStrength?: number | null;
  loraRef?: string | null;
  sortOrder: number;
  createdAt: string;
  updatedAt: string;
}

export interface DramaForgeEpisode {
  id: string;
  projectId: string;
  episodeNumber: number;
  title: string;
  scriptJson?: string | null;
  shotCount: number;
  scriptLockedAt?: string | null;
  storyboardLockedAt?: string | null;
  timelineJson?: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface DramaForgeTimelineClip {
  shotId: string;
  order: number;
  trimIn?: number;
  trimOut?: number;
  transition?: "cut" | "fade";
}

export interface DramaForgeTimeline {
  clips: DramaForgeTimelineClip[];
  exportPreset?: "douyin_9_16" | "bilibili_16_9";
}

export interface DramaForgePlanEpisodeOutline {
  episodeNumber: number;
  title: string;
  summary: string;
}

export interface DramaForgePlanEpisodesResult {
  plannedCount: number;
  episodes: DramaForgePlanEpisodeOutline[];
}

export interface DramaForgeAssetDesignCandidates {
  assetId: string;
  candidates: DramaForgeAssetVersion[];
}

export interface DramaForgeMediaBinding {
  index: number;
  tag: string;
  label: string;
  url?: string | null;
}

export interface DramaForgeShot {
  id: string;
  episodeId: string;
  shotNumber: number;
  description: string;
  videoPrompt?: string | null;
  /** 分镜首帧专用提示词（静态关键帧，与 videoPrompt 分离） */
  storyboardPrompt?: string | null;
  dialogue?: string | null;
  cameraNote?: string | null;
  characterRefs: string[];
  sceneRef?: string | null;
  propRefs?: string[];
  imageBindings?: DramaForgeMediaBinding[];
  audioBindings?: DramaForgeMediaBinding[];
  storyboardUrl?: string | null;
  videoJobId?: string | null;
  videoUrl?: string | null;
  durationSeconds?: number | null;
  forceCharacterBinding?: boolean | null;
  referenceVideoMode?: string | null;
  referenceVideoUrl?: string | null;
  firstFrameUrl?: string | null;
  lastFrameUrl?: string | null;
  dialogueAudioUrl?: string | null;
  qaStatus?: string | null;
  modelMultiShot?: boolean | null;
  multiShotTemplate?: string | null;
  status: DramaForgeShotStatus;
  /** 失败原因（视频/分镜等） */
  errorMessage?: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface DramaForgePipelineOverview {
  projectId: string;
  stage: DramaForgePipelineStage;
  progress: number;
  contentMode: DramaForgeContentMode;
  generationMode: DramaForgeGenerationMode;
  assetCounts: Record<DramaForgeAssetType, number>;
  episodeCount: number;
  shotCount: number;
  storyboardDoneCount: number;
  videoDoneCount: number;
  nextActions: string[];
  consistency?: DramaForgeConsistencyReport | null;
  assetsLockedAt?: string | null;
  storyboardLockedAt?: string | null;
  scriptLocked: boolean;
  storyboardLocked: boolean;
}

export interface DramaForgeConsistencyReport {
  assetsMissingDesignImage: number;
  charactersMissingVoiceSample: number;
  shotsMissingBindings: number;
  shotsReadyForVideo: number;
  shotsPendingVideo: number;
  warnings: string[];
}

export interface UpdateDramaForgeConfigInput {
  contentMode?: DramaForgeContentMode;
  generationMode?: DramaForgeGenerationMode;
  aspectRatio?: DramaForgeAspectRatio | null;
  imageBackend?: string;
  videoBackend?: string;
  textBackend?: string;
  stylePrompt?: string;
  sourceText?: string;
  projectSummary?: string;
  worldview?: string;
  imageQuality?: string;
  videoQuality?: string;
  colorGradePreset?: string;
  mixDialogueAudioInCompose?: boolean;
  bgmUrl?: string;
  bgmVolume?: number;
  lipSyncEnabled?: boolean;
  lipSyncEndpoint?: string;
  preferModelMultiShot?: boolean;
}

export interface CreateDramaForgeAssetInput {
  type: DramaForgeAssetType;
  name: string;
  description?: string;
  designPrompt?: string;
  referenceImageUrl?: string;
  voiceLabel?: string;
  voiceSampleUrl?: string;
  sortOrder?: number;
}

export interface UpdateDramaForgeAssetInput {
  name?: string;
  description?: string;
  designPrompt?: string;
  referenceImageUrl?: string;
  voiceLabel?: string;
  voiceSampleUrl?: string;
  voiceSpeakerId?: string;
  identityLockStrength?: number;
  loraRef?: string;
  sortOrder?: number;
}

export interface CreateDramaForgeEpisodeInput {
  title: string;
  episodeNumber?: number;
  scriptJson?: string;
}

export interface UpdateDramaForgeEpisodeInput {
  title?: string;
  scriptJson?: string;
  timelineJson?: string;
}

export interface CreateDramaForgeShotInput {
  description: string;
  dialogue?: string;
  cameraNote?: string;
  characterRefs?: string[];
  shotNumber?: number;
  durationSeconds?: number;
}

export interface UpdateDramaForgeShotInput {
  description?: string;
  dialogue?: string;
  cameraNote?: string;
  characterRefs?: string[];
  sceneRef?: string;
  propRefs?: string[];
  storyboardUrl?: string;
  status?: DramaForgeShotStatus;
  durationSeconds?: number;
  forceCharacterBinding?: boolean;
  referenceVideoMode?: string;
  referenceVideoUrl?: string;
  firstFrameUrl?: string;
  lastFrameUrl?: string;
  storyboardPrompt?: string;
  dialogueAudioUrl?: string;
  qaStatus?: string;
  modelMultiShot?: boolean;
  multiShotTemplate?: string;
}

export type DramaForgeJobType =
  | "extract_assets"
  | "generate_script"
  | "asset_design"
  | "asset_design_single"
  | "storyboard"
  | "shot_storyboard"
  | "shot_video"
  | "grid_storyboard"
  | "video"
  | "sync_videos"
  | "compose"
  | "export_project"
  | "export_jianying"
  | "workflow_run";

export type DramaForgeJobStatus = "queued" | "running" | "completed" | "failed" | "cancelled";

export interface DramaForgeJob {
  id: string;
  projectId: string;
  jobType: DramaForgeJobType;
  status: DramaForgeJobStatus;
  episodeId?: string | null;
  /** 关联目标（如 shotId / assetId） */
  targetId?: string | null;
  errorMessage?: string | null;
  progressCurrent?: number;
  progressTotal?: number;
  progressMessage?: string | null;
  queuePosition?: number | null;
  createdAt: string;
  updatedAt: string;
}

export interface DramaForgeJobProgressEvent {
  jobId: string;
  type: string;
  status: string;
  current: number;
  total: number;
  message: string;
  episodeId?: string;
  /** 关联目标（如 shotId） */
  targetId?: string;
}

export interface DramaForgeComposition {
  id: string;
  projectId: string;
  episodeId?: string | null;
  outputUrl?: string | null;
  status: string;
  errorMessage?: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface DramaForgeShotVersion {
  id: string;
  shotId: string;
  versionNo: number;
  storyboardUrl?: string | null;
  videoJobId?: string | null;
  videoUrl?: string | null;
  active: boolean;
  createdAt: string;
}

export interface DramaForgeAssetVersion {
  id: string;
  assetId: string;
  versionNo: number;
  referenceImageUrl?: string | null;
  designPrompt?: string | null;
  active: boolean;
  createdAt: string;
}

export interface DramaForgeExportResult {
  type: string;
  downloadUrl: string;
  message: string;
}

export interface DramaForgeAgentMessage {
  role: "user" | "assistant";
  content: string;
  actions?: DramaForgeAgentAction[];
}

export interface DramaForgeAgentAction {
  tool: string;
  status: string;
  detail?: string | null;
}

export interface DramaForgeAgentChatInput {
  message: string;
  selectedEpisodeId?: string | null;
  history?: { role: string; content: string }[];
}

export interface DramaForgeAgentChatResult {
  reply: string;
  actions: DramaForgeAgentAction[];
}

export type DramaForgePromptKind = "style" | "asset_design" | "shot";

export interface OptimizeDramaForgePromptInput {
  kind: DramaForgePromptKind;
  draft?: string;
  assetId?: string;
  episodeId?: string;
  shotId?: string;
  assetType?: DramaForgeAssetType;
  assetName?: string;
  assetDescription?: string;
}

export interface OptimizeDramaForgePromptResult {
  optimizedText: string;
}
