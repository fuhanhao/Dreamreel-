"use client";

import Image from "next/image";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useT } from "@/i18n/locale-provider";
import Link from "next/link";
import type {
  DramaForgeAsset,
  DramaForgeAssetType,
  DramaForgeAssetVersion,
  DramaForgeAspectRatio,
  DramaForgeComposition,
  DramaForgeConfig,
  DramaForgeContentMode,
  DramaForgeEpisode,
  DramaForgeExportResult,
  DramaForgeJob,
  DramaForgeJobProgressEvent,
  DramaForgePipelineOverview,
  DramaForgePipelineStage,
  DramaForgePromptKind,
  DramaForgeShot,
  DramaForgeShotVersion,
  MediaQuality,
} from "@dreamreel/shared-types";
import { DfExpandableText } from "@/components/ui/df-expandable-text";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import {
  DRAMA_FORGE_ASPECT_RATIOS,
  DRAMA_FORGE_ASSET_TYPE_LABELS,
  DRAMA_FORGE_PIPELINE_STAGES,
  canAdvanceToStep,
  defaultDramaForgeAspectRatio,
} from "@dreamreel/shared-types";
import { DramaForgeWizard, stageContentView } from "./dramaforge-wizard";
import { DramaForgeTimelineEditor } from "./dramaforge-timeline";
import { DramaForgeScriptEditor } from "./dramaforge-script-editor";
import {
  findActiveShotJob,
  hasActiveShotJob,
  resolveShotFailureReason,
  shotVisualStatus,
} from "./shot-status";

const DRAMA_FORGE_IMAGE_BACKENDS = [
  { value: "doubao-seedream-5-0-260128", label: "豆包 Seedream 5.0 Lite" },
  { value: "doubao-seedream-5-0-pro-260628", label: "豆包 Seedream 5 Pro" },
] as const;

const DRAMA_FORGE_VIDEO_BACKENDS = [
  { value: "doubao-seedance-2-5-260628", label: "Seedance 2.5（方舟）" },
  { value: "doubao-seedance-2-0-260128", label: "Seedance 2.0（方舟）" },
  { value: "doubao-seedance-2-0-fast-260128", label: "Seedance 2.0 Fast" },
  { value: "bytedance/seedance-2.5", label: "Seedance 2.5（别名）" },
  { value: "bytedance/seedance-2", label: "Seedance 2（别名）" },
  { value: "bytedance/seedance-2-fast", label: "Seedance 2 Fast（别名）" },
] as const;

const DRAMA_FORGE_QUALITIES: MediaQuality[] = ["480p", "720p", "1080p"];

import {
  composeDramaForgeEpisode,
  createDramaForgeAsset,
  createDramaForgeEpisode,
  createDramaForgeShot,
  deleteDramaForgeAsset,
  deleteDramaForgeEpisode,
  exportDramaForgeJianying,
  exportDramaForgeProject,
  extractDramaForgeAssets,
  fetchDramaForgeAssets,
  fetchDramaForgeCompositions,
  fetchDramaForgeConfig,
  fetchDramaForgeEpisodes,
  fetchDramaForgeJobs,
  fetchDramaForgeOverview,
  fetchDramaForgeShotVersions,
  fetchDramaForgeShot,
  fetchDramaForgeShots,
  cancelDramaForgeJob,
  clearFinishedDramaForgeJobs,
  retryDramaForgeJob,
  activateDramaForgeShotVersion,
  generateDramaForgeAssetDesigns,
  generateDramaForgeAssetDesignCandidates,
  regenerateDramaForgeAssetDesign,
  generateDramaForgeCharacterVoice,
  fetchDramaForgeAssetVersions,
  activateDramaForgeAssetVersion,
  lockDramaForgeAssets,
  lockDramaForgeScript,
  planDramaForgeEpisodes,
  structureDramaForgeEpisodeScript,
  structureDramaForgeEpisodeShots,
  fetchDramaForgeComposeReadiness,
  generateDramaForgeEpisodeDialogueAudio,
  generateDramaForgeShotDialogueAudio,
  generateDramaForgeVideos,
  regenerateDramaForgeShotVideo,
  promoteDramaForgeShotAssets,
  promotePreviousDramaForgeShotAssets,
  optimizeDramaForgeAssetDesignPrompts,
  optimizeDramaForgePrompt,
  parseDramaForgeShotsFromScript,
  runDramaForgeWorkflow,
  subscribeDramaForgeEvents,
  syncDramaForgeVideos,
  updateDramaForgeConfig,
  updateDramaForgeEpisode,
  updateDramaForgeAsset,
  updateDramaForgeShot,
} from "./api";
import { uploadMedia } from "@/lib/api";
import { resolveMediaUrl } from "@/lib/api-base";
import { extractJobFailureMessage, getErrorMessage } from "@/lib/api-error";
import { resolveTokenfreeApiKey } from "@/lib/api-key";
import { useAuth } from "@/components/auth/auth-provider";
import "./dramaforge-theme.css";
import { DramaForgeAgentPanel } from "./dramaforge-agent-panel";
import { DfSelect } from "@/components/ui/df-select";
import { DfScrollArea } from "@/components/ui/df-scroll-area";
import { DfNumberStepper } from "@/components/ui/df-number-stepper";
import { useConfirmDialog } from "@/hooks/use-confirm-dialog";
import {
  DramaForgeBadge,
  DramaForgeJobProgressBar,
  DramaForgePanel,
  DramaForgePrimaryButton,
  DramaForgeSecondaryButton,
  DramaForgeStat,
  GeneratingText,
} from "./dramaforge-ui";

interface DramaForgeWorkspaceProps {
  projectId: string;
  projectName: string;
  onOpenCanvas?: () => void;
}

type WizardStep = DramaForgePipelineStage;
type RightPanelId = "agent" | "tasks" | "preview";
type ShotListSubTab = "list" | "batch" | "history" | "versions";

interface ShotPreviewState {
  shotId: string;
  shotNumber: number;
  videoUrl: string;
  description?: string | null;
}

interface FramePreviewState {
  url: string;
  label: string;
}

function formatShotDuration(seconds?: number | null) {
  const total = Math.max(0, Math.round(seconds ?? 5));
  const m = Math.floor(total / 60);
  const s = total % 60;
  return `${String(m).padStart(2, "0")}:${String(s).padStart(2, "0")}`;
}

function pipelineStageLabel(stage: DramaForgePipelineStage, t: (key: string, params?: Record<string, string | number>) => string): string {
  const labels: Record<DramaForgePipelineStage, string> = {
    story_input: t("dramaforge.workspace.stageStoryInput"),
    script_locked: t("dramaforge.workspace.stageScriptLocked"),
    assets_locked: t("dramaforge.workspace.stageAssetsLocked"),
    video_done: t("dramaforge.workspace.stageVideoDone"),
    composed: t("dramaforge.workspace.stageComposed"),
  };
  return labels[stage] ?? stage;
}

function pipelineStageDescription(stage: DramaForgePipelineStage, t: (key: string, params?: Record<string, string | number>) => string): string {
  const descriptions: Record<DramaForgePipelineStage, string> = {
    story_input: t("dramaforge.workspace.stageStoryInputDesc"),
    script_locked: t("dramaforge.workspace.stageScriptLockedDesc"),
    assets_locked: t("dramaforge.workspace.stageAssetsLockedDesc"),
    video_done: t("dramaforge.workspace.stageVideoDoneDesc"),
    composed: t("dramaforge.workspace.stageComposedDesc"),
  };
  return descriptions[stage] ?? stage;
}

function aspectRatioLabel(value: DramaForgeAspectRatio, t: (key: string, params?: Record<string, string | number>) => string): string {
  const labels: Record<DramaForgeAspectRatio, string> = {
    "9:16": t("dramaforge.workspace.aspectRatio9_16"),
    "16:9": t("dramaforge.workspace.aspectRatio16_9"),
    "3:4": t("dramaforge.workspace.aspectRatio3_4"),
    "4:3": t("dramaforge.workspace.aspectRatio4_3"),
    "1:1": t("dramaforge.workspace.aspectRatio1_1"),
  };
  return labels[value] ?? value;
}

function backendLabel(value: string, t: (key: string, params?: Record<string, string | number>) => string): string {
  const labels: Record<string, string> = {
    "doubao-seedream-5-0-260128": t("dramaforge.workspace.backendImageSeedream50Lite"),
    "doubao-seedream-5-0-pro-260628": t("dramaforge.workspace.backendImageSeedream5Pro"),
    "doubao-seedance-2-0-260128": t("dramaforge.workspace.backendVideoSeedance20Ark"),
    "doubao-seedance-2-0-fast-260128": t("dramaforge.workspace.backendVideoSeedance20Fast"),
    "doubao-seedance-2-5-260628": t("dramaforge.workspace.backendVideoSeedance25"),
    "bytedance/seedance-2": t("dramaforge.workspace.backendVideoSeedance2Alias"),
    "bytedance/seedance-2-fast": t("dramaforge.workspace.backendVideoSeedance2FastAlias"),
    "bytedance/seedance-2.5": t("dramaforge.workspace.backendVideoSeedance25Alias"),
  };
  return labels[value] ?? value;
}

function jobTypeLabel(type: string, t: (key: string, params?: Record<string, string | number>) => string) {
  const labels: Record<string, string> = {
    extract_assets: t("dramaforge.workspace.jobExtractAssets"),
    generate_script: t("dramaforge.workspace.jobGenerateScript"),
    asset_design: t("dramaforge.workspace.jobAssetDesign"),
    asset_design_single: t("dramaforge.workspace.jobAssetDesignSingle"),
    storyboard: t("dramaforge.workspace.jobStoryboard"),
    shot_storyboard: t("dramaforge.workspace.jobShotStoryboard"),
    shot_video: t("dramaforge.workspace.jobShotVideo"),
    grid_storyboard: t("dramaforge.workspace.jobGridStoryboard"),
    video: t("dramaforge.workspace.jobVideo"),
    sync_videos: t("dramaforge.workspace.jobSyncVideos"),
    compose: t("dramaforge.workspace.jobCompose"),
    export_project: t("dramaforge.workspace.jobExportProject"),
    export_jianying: t("dramaforge.workspace.jobExportJianying"),
    workflow_run: t("dramaforge.workspace.jobWorkflowRun"),
  };
  return labels[type] ?? type.replaceAll("_", " ");
}

function jobStatusLabel(status: DramaForgeJob["status"], t: (key: string, params?: Record<string, string | number>) => string) {
  const labels: Record<DramaForgeJob["status"], string> = {
    queued: t("dramaforge.workspace.statusQueued"),
    running: t("dramaforge.workspace.statusRunning"),
    completed: t("dramaforge.workspace.statusCompleted"),
    failed: t("dramaforge.workspace.statusFailed"),
    cancelled: t("dramaforge.workspace.statusCancelled"),
  };
  return labels[status] ?? status;
}

function sortJobsForDisplay(jobs: DramaForgeJob[]) {
  const priority: Record<DramaForgeJob["status"], number> = {
    running: 0,
    queued: 1,
    failed: 2,
    cancelled: 3,
    completed: 4,
  };
  return [...jobs].sort((a, b) => {
    const byStatus = priority[a.status] - priority[b.status];
    if (byStatus !== 0) return byStatus;
    return new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime();
  });
}

function effectiveAspectRatio(config: DramaForgeConfig | null): DramaForgeAspectRatio {
  if (config?.aspectRatio) return config.aspectRatio;
  return defaultDramaForgeAspectRatio(config?.contentMode ?? "drama");
}

export function DramaForgeWorkspace({
  projectId,
  projectName,
  onOpenCanvas,
}: DramaForgeWorkspaceProps) {
  const t = useT();
  const [wizardStep, setWizardStep] = useState<WizardStep>("story_input");
  const [rightPanel, setRightPanel] = useState<RightPanelId>("agent");
  const [shotPreview, setShotPreview] = useState<ShotPreviewState | null>(null);
  const [framePreview, setFramePreview] = useState<FramePreviewState | null>(null);
  const [shotListSubTab, setShotListSubTab] = useState<ShotListSubTab>("list");
  const [shotSearch, setShotSearch] = useState("");
  const [shotSceneFilter, setShotSceneFilter] = useState("");
  const [shotStatusFilter, setShotStatusFilter] = useState<"all" | "done" | "run" | "wait">("all");
  const [pendingShotsOnly, setPendingShotsOnly] = useState(false);
  const [overview, setOverview] = useState<DramaForgePipelineOverview | null>(null);
  const [config, setConfig] = useState<DramaForgeConfig | null>(null);
  const [assets, setAssets] = useState<DramaForgeAsset[]>([]);
  const [episodes, setEpisodes] = useState<DramaForgeEpisode[]>([]);
  const [selectedEpisodeId, setSelectedEpisodeId] = useState<string | null>(null);
  const [shots, setShots] = useState<DramaForgeShot[]>([]);
  const [shotsLoading, setShotsLoading] = useState(true);
  const [jobs, setJobs] = useState<DramaForgeJob[]>([]);
  const [compositions, setCompositions] = useState<DramaForgeComposition[]>([]);
  const [exportUrl, setExportUrl] = useState<string | null>(null);
  const [expandedShotId, setExpandedShotId] = useState<string | null>(null);
  const [expandedAssetId, setExpandedAssetId] = useState<string | null>(null);
  const [shotVersions, setShotVersions] = useState<DramaForgeShotVersion[]>([]);
  const [assetVersions, setAssetVersions] = useState<DramaForgeAssetVersion[]>([]);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [sourceText, setSourceText] = useState("");
  const [stylePrompt, setStylePrompt] = useState("");
  const [projectSummary, setProjectSummary] = useState("");
  const [worldview, setWorldview] = useState("");
  const [colorGradePreset, setColorGradePreset] = useState("none");
  const [mixDialogueAudioInCompose, setMixDialogueAudioInCompose] = useState(true);
  const [bgmUrl, setBgmUrl] = useState("");
  const [bgmVolume, setBgmVolume] = useState(0.18);
  const [lipSyncEnabled, setLipSyncEnabled] = useState(false);
  const [lipSyncEndpoint, setLipSyncEndpoint] = useState("");
  const [preferModelMultiShot, setPreferModelMultiShot] = useState(true);
  const [composeReadiness, setComposeReadiness] = useState<{
    totalShots: number;
    videoDoneShots: number;
    shotsWithDialogue: number;
    missingDialogueAudio: number;
    lipSyncEnabled: boolean;
    lipSyncEndpointConfigured: boolean;
    mixDialogueAudio: boolean;
    warnings: string[];
    blockers: string[];
  } | null>(null);
  const [uploadingBgm, setUploadingBgm] = useState(false);
  const [editingAssetId, setEditingAssetId] = useState<string | null>(null);
  const [editingShotId, setEditingShotId] = useState<string | null>(null);
  const [episodeTitle, setEpisodeTitle] = useState(() => t("dramaforge.workspace.episodeTitle", { n: 1 }));
  const [episodeScript, setEpisodeScript] = useState("");
  const episodeScriptDirtyRef = useRef(false);
  const [shotDescription, setShotDescription] = useState("");
  const [assetName, setAssetName] = useState("");
  const [assetType, setAssetType] = useState<DramaForgeAssetType>("character");
  const [assetFilter, setAssetFilter] = useState<DramaForgeAssetType | null>(null);
  const [assetDescription, setAssetDescription] = useState("");
  const [assetDesignPrompt, setAssetDesignPrompt] = useState("");
  const [optimizingKind, setOptimizingKind] = useState<string | null>(null);
  const [activeJobProgress, setActiveJobProgress] = useState<DramaForgeJobProgressEvent | null>(null);
  const [pendingShotIds, setPendingShotIds] = useState<Record<string, true>>({});
  const selectedEpisodeIdRef = useRef<string | null>(null);
  const refreshDebounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const { user } = useAuth();
  const apiKey = resolveTokenfreeApiKey(user);
  const { confirm, alert, ConfirmDialog } = useConfirmDialog("dark");

  const episodeSceneRefs = useMemo(
    () => Array.from(
      new Set(shots.map((s) => s.sceneRef?.trim()).filter((s): s is string => Boolean(s))),
    ),
    [shots],
  );
  const videoDoneInEpisodeCount = useMemo(
    () => shots.filter((shot) => shot.status === "video_done").length,
    [shots],
  );

  const filteredShots = useMemo(() => {
    const q = shotSearch.trim().toLowerCase();
    const listMode = "video";
    return shots.filter((shot) => {
      if (pendingShotsOnly) {
        if (shot.status === "video_done") {
          return false;
        }
      }
      if (shotSceneFilter && (shot.sceneRef?.trim() ?? "") !== shotSceneFilter) return false;
      if (shotStatusFilter !== "all") {
        const visual = shotVisualStatus(shot, listMode, jobs).key;
        if (shotStatusFilter === "done" && visual !== "done") return false;
        if (shotStatusFilter === "run" && visual !== "run") return false;
        if (shotStatusFilter === "wait" && visual !== "wait" && visual !== "fail") return false;
      }
      if (!q) return true;
      return (
        String(shot.shotNumber).includes(q) ||
        (shot.description ?? "").toLowerCase().includes(q) ||
        (shot.videoPrompt ?? "").toLowerCase().includes(q) ||
        (shot.sceneRef ?? "").toLowerCase().includes(q) ||
        shot.id.toLowerCase().includes(q)
      );
    });
  }, [shots, shotSearch, pendingShotsOnly, shotSceneFilter, shotStatusFilter, jobs]);

  const refreshAll = useCallback(async (opts?: { silent?: boolean }) => {
    if (!opts?.silent) {
      setLoading(true);
    }
    try {
      // 首屏先拉核心数据；jobs 历史可能仍偏大，放到第二波，避免挡住流水线 UI。
      const [overviewRes, configRes, assetsRes, episodesRes, compositionsRes] =
        await Promise.all([
        fetchDramaForgeOverview(projectId),
        fetchDramaForgeConfig(projectId),
        fetchDramaForgeAssets(projectId),
        fetchDramaForgeEpisodes(projectId),
        fetchDramaForgeCompositions(projectId),
      ]);
      setOverview(overviewRes.data);
      setConfig(configRes.data);
      setAssets(assetsRes.data);
      setEpisodes(episodesRes.data);
      setCompositions(compositionsRes.data);
      setSourceText(configRes.data.sourceText ?? "");
      setStylePrompt(configRes.data.stylePrompt ?? "");
      setProjectSummary(configRes.data.projectSummary ?? "");
      setWorldview(configRes.data.worldview ?? "");
      setColorGradePreset(configRes.data.colorGradePreset ?? "none");
      setMixDialogueAudioInCompose(configRes.data.mixDialogueAudioInCompose ?? true);
      setBgmUrl(configRes.data.bgmUrl ?? "");
      setBgmVolume(configRes.data.bgmVolume ?? 0.18);
      setLipSyncEnabled(configRes.data.lipSyncEnabled ?? false);
      setLipSyncEndpoint(configRes.data.lipSyncEndpoint ?? "");
      setPreferModelMultiShot(configRes.data.preferModelMultiShot ?? true);
      const firstEpisode = episodesRes.data[0]?.id ?? null;
      setSelectedEpisodeId((prev) => prev ?? firstEpisode);
      if (!opts?.silent) {
        setLoading(false);
      }

      void fetchDramaForgeJobs(projectId, 50)
        .then((jobsRes) => {
          setJobs(jobsRes.data);
        })
        .catch(() => {
          /* jobs 失败不阻断工作台 */
        });
    } catch (e) {
      setError(getErrorMessage(e, t("dramaforge.workspace.errorLoadModule")));
      if (!opts?.silent) {
        setLoading(false);
      }
    }
  }, [projectId, t]);

  const refreshShots = useCallback(async (episodeId: string) => {
    const res = await fetchDramaForgeShots(projectId, episodeId);
    setShots(res.data);
  }, [projectId]);

  const loadShotsForEpisode = useCallback(async (episodeId: string) => {
    setShotsLoading(true);
    setShots([]);
    try {
      await refreshShots(episodeId);
    } finally {
      setShotsLoading(false);
    }
  }, [refreshShots]);

  const refreshShot = useCallback(async (episodeId: string, shotId: string) => {
    try {
      const res = await fetchDramaForgeShot(projectId, episodeId, shotId);
      setShots((prev) => {
        const idx = prev.findIndex((s) => s.id === shotId);
        if (idx < 0) return prev;
        const next = prev.slice();
        next[idx] = res.data;
        return next;
      });
    } catch (e) {
      // 旧 API 未注册 GET /shots/{id} 时会 405：回退为列表取一条，仍只更新当前镜头 state
      const res = await fetchDramaForgeShots(projectId, episodeId);
      const updated = res.data.find((s) => s.id === shotId);
      if (!updated) throw e;
      setShots((prev) => {
        const idx = prev.findIndex((s) => s.id === shotId);
        if (idx < 0) return prev;
        const next = prev.slice();
        next[idx] = updated;
        return next;
      });
    }
  }, [projectId]);

  const upsertJob = useCallback((job: DramaForgeJob) => {
    setJobs((prev) => {
      const idx = prev.findIndex((j) => j.id === job.id);
      if (idx < 0) return [job, ...prev];
      const next = prev.slice();
      next[idx] = { ...next[idx], ...job };
      return next;
    });
  }, []);

  const refreshComposeReadiness = useCallback(async (episodeId: string) => {
    try {
      const res = await fetchDramaForgeComposeReadiness(projectId, episodeId);
      setComposeReadiness(res.data);
    } catch {
      setComposeReadiness(null);
    }
  }, [projectId]);

  const scheduleEventRefresh = useCallback((opts?: {
    shotId?: string | null;
    episodeId?: string | null;
    /** 只刷镜头/任务，不 refreshAll（避免 setEpisodes 触发列表闪烁） */
    light?: boolean;
    immediate?: boolean;
  }) => {
    if (refreshDebounceRef.current) {
      clearTimeout(refreshDebounceRef.current);
    }
    const run = () => {
      const episodeId = opts?.episodeId || selectedEpisodeIdRef.current;
      const shotId = opts?.shotId?.trim() || null;
      if (shotId && episodeId) {
        // 单镜任务：只刷新当前镜头 + 任务列表，避免整表闪烁
        void refreshShot(episodeId, shotId);
        void fetchDramaForgeJobs(projectId, 50)
          .then((jobsRes) => setJobs(jobsRes.data))
          .catch(() => undefined);
        return;
      }
      if (opts?.light) {
        void fetchDramaForgeJobs(projectId, 50)
          .then((jobsRes) => setJobs(jobsRes.data))
          .catch(() => undefined);
        if (episodeId) {
          void refreshShots(episodeId);
          void refreshComposeReadiness(episodeId);
        }
        return;
      }
      void refreshAll({ silent: true });
      if (episodeId) {
        void refreshShots(episodeId);
      }
    };
    // 任务结束立刻刷；进行中事件稍作合并，避免刷爆
    if (opts?.immediate) {
      run();
      return;
    }
    refreshDebounceRef.current = setTimeout(run, 800);
  }, [projectId, refreshAll, refreshComposeReadiness, refreshShot, refreshShots]);

  useEffect(() => {
    const timer = window.setTimeout(() => {
      void refreshAll();
    }, 0);
    return () => window.clearTimeout(timer);
  }, [refreshAll]);

  useEffect(() => {
    const unsubscribe = subscribeDramaForgeEvents(projectId, (event, data) => {
      if (event === "ping" || event === "connected") {
        return;
      }
      if (event === "job_progress" && data && typeof data === "object") {
        setActiveJobProgress(data as DramaForgeJobProgressEvent);
        return;
      }
      if (event === "pipeline_updated") {
        // 只更新流水线概览；单镜成片/分镜由 job_* + refreshShot 刷新，避免整表闪烁
        if (data && typeof data === "object") {
          setOverview(data as DramaForgePipelineOverview);
        } else {
          void refreshAll({ silent: true });
        }
        return;
      }
      if (event.startsWith("job_") || event === "composition_completed" || event === "jobs_cleared") {
        const terminal =
          event === "job_completed" || event === "job_failed" || event === "job_cancelled";
        if (terminal) {
          setActiveJobProgress(null);
        }
        if (event === "job_failed" && data && typeof data === "object") {
          const err = extractJobFailureMessage(data);
          // SSE 偶发丢字段时，用随后刷到的任务列表补全具体文案
          const jobId =
            typeof (data as { jobId?: unknown }).jobId === "string"
              ? (data as { jobId: string }).jobId
              : "";
          if ((!err || err === "任务失败，请查看任务列表详情") && jobId) {
            void fetchDramaForgeJobs(projectId, 50)
              .then((jobsRes) => {
                setJobs(jobsRes.data);
                const fromJob = jobsRes.data.find((j) => j.id === jobId)?.errorMessage?.trim();
                if (fromJob) {
                  setError(fromJob);
                }
              })
              .catch(() => undefined);
          }
          const privacyBlocked =
            /隐私|PrivacyInformation|real person|真人隐私/i.test(err) &&
            !/InputTextSensitive|文本敏感/i.test(err);
          const textSensitive = /InputTextSensitive|文本敏感|SensitiveContentDetected/i.test(err);
          if (privacyBlocked) {
            const hint = t("dramaforge.workspace.privacySafeRegenerateHint");
            void alert(`${err}\n\n${hint}`, t("dramaforge.workspace.privacyBlockedAlertTitle"), {
              variant: "danger",
            });
          } else if (textSensitive) {
            void alert(err, t("dramaforge.workspace.textSensitiveAlertTitle"), { variant: "danger" });
          } else {
            setError(err);
          }
        }
        if (event === "composition_completed") {
          void fetchDramaForgeCompositions(projectId)
            .then((res) => setCompositions(res.data))
            .catch(() => undefined);
        }
        const payload = data && typeof data === "object"
          ? (data as { type?: string; targetId?: string; episodeId?: string })
          : null;
        const jobType = (payload?.type ?? "").toLowerCase();
        const targetId = (payload?.targetId ?? "").trim();
        const episodeId = (payload?.episodeId ?? "").trim() || selectedEpisodeIdRef.current;
        const scopedShot =
          Boolean(targetId) &&
          (jobType === "shot_storyboard" || jobType === "shot_video");
        // 同步视频 / 批量成片：高频 SSE，只轻量刷镜头，避免 refreshAll → episodes 变引用 → 列表反复「加载中」
        // compose 完成后必须刷新 compositions 列表，不能走 light 路径
        if (terminal && jobType === "compose") {
          void fetchDramaForgeCompositions(projectId)
            .then((res) => setCompositions(res.data))
            .catch(() => undefined);
        }
        const lightEpisodeRefresh =
          jobType === "sync_videos"
          || jobType === "video"
          || jobType === "storyboard";
        scheduleEventRefresh(
          scopedShot
            ? {
                shotId: targetId,
                episodeId,
                immediate: terminal,
              }
            : lightEpisodeRefresh
              ? { episodeId, light: true, immediate: terminal }
              : { immediate: terminal },
        );
      }
      if (event === "export_completed" && data && typeof data === "object" && "downloadUrl" in data) {
        setExportUrl((data as DramaForgeExportResult).downloadUrl);
      }
    });
    return () => {
      unsubscribe();
      if (refreshDebounceRef.current) {
        clearTimeout(refreshDebounceRef.current);
        refreshDebounceRef.current = null;
      }
    };
    // 仅随 projectId 订阅，避免 refresh* 引用变化反复拆掉 SSE
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [projectId]);

  // 仅在切换分集时整表重载镜头；勿依赖 episodes（SSE/refreshAll 会不断换新数组引用，导致反复清空闪「加载中」）
  useEffect(() => {
    selectedEpisodeIdRef.current = selectedEpisodeId;
    if (selectedEpisodeId) {
      const timer = window.setTimeout(() => {
        void loadShotsForEpisode(selectedEpisodeId);
        void refreshComposeReadiness(selectedEpisodeId);
      }, 0);
      return () => window.clearTimeout(timer);
    }
    const timer = window.setTimeout(() => {
      setShots([]);
      setShotsLoading(false);
      setComposeReadiness(null);
    }, 0);
    episodeScriptDirtyRef.current = false;
    return () => window.clearTimeout(timer);
  }, [selectedEpisodeId, loadShotsForEpisode, refreshComposeReadiness]);

  useEffect(() => {
    if (!selectedEpisodeId) return;
    const episode = episodes.find((e) => e.id === selectedEpisodeId);
    if (!episode) return;
    const timer = window.setTimeout(() => {
      setEpisodeTitle(episode.title);
      if (!episodeScriptDirtyRef.current) {
        setEpisodeScript(episode.scriptJson ?? "");
      }
    }, 0);
    return () => window.clearTimeout(timer);
  }, [selectedEpisodeId, episodes]);

  async function runAction(action: () => Promise<void>) {
    setBusy(true);
    setError(null);
    try {
      await action();
      await refreshAll();
      const episodeId = selectedEpisodeIdRef.current;
      if (episodeId) {
        await refreshShots(episodeId);
        await refreshComposeReadiness(episodeId);
      } else {
        setShots([]);
        setComposeReadiness(null);
      }
    } catch (e) {
      // 操作失败只弹窗，避免与顶部红色横条重复提示
      await alert(getErrorMessage(e, t("dramaforge.workspace.operationFailed")), t("dramaforge.workspace.operationFailed"), { variant: "danger" });
    } finally {
      setBusy(false);
    }
  }

  async function runPromptOptimize(
    key: string,
    input: {
      kind: DramaForgePromptKind;
      draft?: string;
      assetId?: string;
      episodeId?: string;
      shotId?: string;
      assetType?: DramaForgeAssetType;
      assetName?: string;
      assetDescription?: string;
    },
    onResult: (text: string) => void,
  ) {
    if (!apiKey) {
      setError(t("dramaforge.workspace.configureTokenfreeApiKey"));
      return;
    }
    setOptimizingKind(key);
    setError(null);
    try {
      const res = await optimizeDramaForgePrompt(projectId, input, apiKey);
      onResult(res.data.optimizedText);
    } catch (e) {
      setError(e instanceof Error ? e.message : t("dramaforge.workspace.promptOptimizeFailed"));
    } finally {
      setOptimizingKind(null);
    }
  }

  async function handleSaveConfig() {
    await runAction(async () => {
      const res = await updateDramaForgeConfig(projectId, {
        sourceText,
        stylePrompt,
        projectSummary,
        worldview,
        contentMode: config?.contentMode ?? "drama",
        generationMode: "reference_to_video",
        aspectRatio: effectiveAspectRatio(config),
        imageBackend: config?.imageBackend ?? "doubao-seedream-5-0-260128",
        videoBackend: config?.videoBackend ?? "doubao-seedance-2-5-260628",
        imageQuality: config?.imageQuality ?? "720p",
        videoQuality: "480p",
        colorGradePreset,
        mixDialogueAudioInCompose,
        bgmUrl: bgmUrl || undefined,
        bgmVolume,
        lipSyncEnabled,
        lipSyncEndpoint: lipSyncEndpoint || undefined,
        preferModelMultiShot,
      });
      setConfig(res.data);
    });
  }

  async function handleSaveProjectMeta() {
    await runAction(async () => {
      const res = await updateDramaForgeConfig(projectId, {
        projectSummary,
        worldview,
        stylePrompt,
        contentMode: config?.contentMode ?? "drama",
        generationMode: "reference_to_video",
        aspectRatio: effectiveAspectRatio(config),
        imageBackend: config?.imageBackend ?? undefined,
        videoBackend: config?.videoBackend ?? undefined,
        imageQuality: config?.imageQuality ?? undefined,
        videoQuality: "480p",
      });
      setConfig(res.data);
    });
  }

  async function handleCreateAsset() {
    if (!assetName.trim()) return;
    await runAction(async () => {
      await createDramaForgeAsset(projectId, {
        type: assetType,
        name: assetName.trim(),
        description: assetDescription.trim() || undefined,
        designPrompt: assetDesignPrompt.trim() || undefined,
      });
      setAssetName("");
      setAssetDescription("");
      setAssetDesignPrompt("");
    });
  }

  async function handleCreateEpisode() {
    const nextNumber =
      episodes.reduce((max, ep) => Math.max(max, ep.episodeNumber), 0) + 1;
    const title = t("dramaforge.workspace.episodeTitle", { n: nextNumber });
    await runAction(async () => {
      const res = await createDramaForgeEpisode(projectId, {
        title,
        episodeNumber: nextNumber,
      });
      episodeScriptDirtyRef.current = false;
      selectedEpisodeIdRef.current = res.data.id;
      setSelectedEpisodeId(res.data.id);
      setEpisodeTitle(res.data.title);
      setEpisodeScript(res.data.scriptJson ?? "");
    });
  }

  async function handleSaveEpisodeScript() {
    if (!selectedEpisodeId) return;
    if (!episodeTitle.trim()) {
      setError(t("dramaforge.workspace.episodeTitleRequired"));
      return;
    }
    await runAction(async () => {
      await updateDramaForgeEpisode(projectId, selectedEpisodeId, {
        title: episodeTitle.trim(),
        scriptJson: episodeScript,
      });
      episodeScriptDirtyRef.current = false;
    });
  }

  async function handleDeleteEpisode(episodeId: string) {
    const episode = episodes.find((e) => e.id === episodeId);
    const ok = await confirm({
      title: t("dramaforge.workspace.deleteEpisode"),
      message: t("dramaforge.workspace.deleteEpisodeConfirm", { number: episode?.episodeNumber ?? "?", title: episode?.title ?? t("dramaforge.workspace.unnamed") }),
      confirmLabel: t("dramaforge.workspace.delete"),
      variant: "danger",
    });
    if (!ok) return;
    await runAction(async () => {
      await deleteDramaForgeEpisode(projectId, episodeId);
      if (selectedEpisodeIdRef.current === episodeId) {
        selectedEpisodeIdRef.current = null;
        episodeScriptDirtyRef.current = false;
        setSelectedEpisodeId(null);
        setEpisodeScript("");
        setEpisodeTitle(t("dramaforge.workspace.episodeTitle", { n: 1 }));
      }
    });
  }

  async function handleStructureEpisodeScript() {
    if (!selectedEpisodeId) return;
    if (!episodeScript.trim()) {
      setError(t("dramaforge.workspace.scriptBodyRequired"));
      return;
    }
    await runAction(async () => {
      await updateDramaForgeEpisode(projectId, selectedEpisodeId, {
        scriptJson: episodeScript,
      });
      episodeScriptDirtyRef.current = false;
      const res = await structureDramaForgeEpisodeScript(projectId, selectedEpisodeId, apiKey);
      setEpisodeScript(res.data.scriptJson ?? "");
      if (res.data.title) setEpisodeTitle(res.data.title);
    });
  }

  async function handleStructureEpisodeShots() {
    if (!selectedEpisodeId) return;
    const ok = await confirm({
      title: t("dramaforge.workspace.scriptToShotsTitle"),
      message: t("dramaforge.workspace.scriptToShotsConfirm"),
      confirmLabel: t("dramaforge.workspace.parseToShotStructure"),
    });
    if (!ok) return;
    await runAction(async () => {
      if (episodeScriptDirtyRef.current) {
        await updateDramaForgeEpisode(projectId, selectedEpisodeId, {
          scriptJson: episodeScript,
        });
        episodeScriptDirtyRef.current = false;
      }
      const res = await structureDramaForgeEpisodeShots(projectId, selectedEpisodeId, apiKey);
      setEpisodeScript(res.data.scriptJson ?? "");
    });
  }

  async function handleParseShotsFromEpisode() {
    if (!selectedEpisodeId) return;
    if (!episodeScript.trim()) {
      setError(t("dramaforge.workspace.scriptBodyRequired"));
      return;
    }
    const ok = await confirm({
      title: t("dramaforge.workspace.writeToShotLibrary"),
      message: t("dramaforge.workspace.writeToShotLibraryConfirm"),
      confirmLabel: t("dramaforge.workspace.writeToShotLibrary"),
      variant: "danger",
    });
    if (!ok) return;
    await runAction(async () => {
      await updateDramaForgeEpisode(projectId, selectedEpisodeId, {
        scriptJson: episodeScript,
      });
      episodeScriptDirtyRef.current = false;
      await parseDramaForgeShotsFromScript(projectId, selectedEpisodeId, apiKey);
    });
  }

  async function handleLockScript() {
    if (!selectedEpisodeId) return;
    await runAction(async () => {
      await lockDramaForgeScript(projectId, selectedEpisodeId);
      setWizardStep("assets_locked");
    });
  }

  async function handleLockAssets() {
    await runAction(async () => {
      await lockDramaForgeAssets(projectId);
      setWizardStep("video_done");
    });
  }

  async function handleBatchDialogueTts() {
    if (!selectedEpisodeId || !apiKey) return;
    if (!shots.some((s) => s.dialogue?.trim())) {
      setError(t("dramaforge.workspace.noDialogueForTts"));
      return;
    }
    await runAction(async () => {
      const res = await generateDramaForgeEpisodeDialogueAudio(
        projectId,
        selectedEpisodeId,
        apiKey,
      );
      const { attempted, succeeded, errors } = res.data;
      const summary = t("dramaforge.workspace.dialogueTtsSummary", { succeeded, attempted });
      if (errors.length > 0) {
        setError(`${summary}\n${errors.slice(0, 5).join("\n")}`);
      } else {
        await alert(summary, t("dramaforge.workspace.dialogueTtsTitle"), { variant: "success" });
      }
    });
  }

  async function handlePlanEpisodes() {
    await runAction(async () => {
      const res = await planDramaForgeEpisodes(projectId, apiKey);
      const list = await fetchDramaForgeEpisodes(projectId);
      const first = list.data[0];
      if (first) {
        setSelectedEpisodeId(first.id);
        setEpisodeTitle(first.title);
        setEpisodeScript(first.scriptJson ?? "");
        episodeScriptDirtyRef.current = false;
      }
      await alert(t("dramaforge.workspace.planEpisodesSuccess", { n: res.data.plannedCount }), t("dramaforge.workspace.multiEpisodePlanning"), {
        variant: "success",
      });
    });
  }

  async function handleGenerateAssetCandidates(assetId: string) {
    await runAction(async () => {
      await generateDramaForgeAssetDesignCandidates(projectId, assetId, apiKey);
      await loadAssetVersions(assetId);
    });
  }

  async function handleSaveTimeline(timeline: import("@dreamreel/shared-types").DramaForgeTimeline) {
    if (!selectedEpisodeId) return;
    await runAction(async () => {
      await updateDramaForgeEpisode(projectId, selectedEpisodeId, {
        timelineJson: JSON.stringify(timeline),
      });
    });
  }

  async function handleCreateShot() {
    if (!selectedEpisodeId || !shotDescription.trim()) return;
    await runAction(async () => {
      await createDramaForgeShot(projectId, selectedEpisodeId, {
        description: shotDescription.trim(),
      });
      setShotDescription("");
    });
  }

  async function handleImportSourceFile(file: File) {
    const text = await file.text();
    setSourceText(text);
    await runAction(async () => {
      const res = await updateDramaForgeConfig(projectId, {
        sourceText: text,
        stylePrompt,
        contentMode: config?.contentMode ?? "drama",
        generationMode: "reference_to_video",
        aspectRatio: effectiveAspectRatio(config),
      });
      setConfig(res.data);
    });
  }

  async function handleRunWorkflow() {
    const savedText = config?.sourceText?.trim() ?? "";
    const draftText = sourceText.trim();
    if (!savedText && !draftText) {
      setError(t("dramaforge.workspace.sourceTextRequired"));
      setWizardStep("story_input");
      return;
    }
    if (!savedText && draftText) {
      setError(t("dramaforge.workspace.scriptNotSaved"));
      setWizardStep("story_input");
      return;
    }
    await runAction(async () => {
      await runDramaForgeWorkflow(projectId, apiKey);
    });
  }

  async function loadShotVersions(shotId: string) {
    if (!selectedEpisodeId) return;
    const res = await fetchDramaForgeShotVersions(projectId, selectedEpisodeId, shotId);
    setShotVersions(res.data);
    setExpandedShotId(shotId);
  }

  async function loadAssetVersions(assetId: string) {
    const res = await fetchDramaForgeAssetVersions(projectId, assetId);
    setAssetVersions(res.data);
    setExpandedAssetId(assetId);
  }

  async function handleActivateAssetVersion(assetId: string, versionId: string) {
    await runAction(async () => {
      await activateDramaForgeAssetVersion(projectId, assetId, versionId);
      await refreshAll();
      await loadAssetVersions(assetId);
    });
  }

  async function handleActivateVersion(shotId: string, versionId: string) {
    if (!selectedEpisodeId) return;
    await runAction(async () => {
      await activateDramaForgeShotVersion(projectId, selectedEpisodeId, shotId, versionId);
      await refreshShots(selectedEpisodeId);
      await loadShotVersions(shotId);
    });
  }

  useEffect(() => {
    if (!overview?.stage) return;
    const timer = window.setTimeout(() => setWizardStep(overview.stage), 0);
    return () => window.clearTimeout(timer);
  }, [overview?.stage]);

  const contentView = stageContentView(wizardStep);
  const stageIndex = DRAMA_FORGE_PIPELINE_STAGES.findIndex((s) => s.id === overview?.stage);

  /** 左上角/摘要进度：出成片阶段按视频完成比例，避免阶段写死 85% */
  const displayProgress = useMemo(() => {
    if (!overview) return 0;
    const total = overview.shotCount ?? 0;
    const stage = overview.stage;
    if (stage === "composed") return 100;
    if (total > 0 && (stage === "video_done" || (overview.videoDoneCount ?? 0) > 0)) {
      const done = overview.videoDoneCount ?? 0;
      return Math.min(100, Math.round((done / total) * 100));
    }
    return overview.progress ?? 0;
  }, [overview]);

  const displayJobProgress: DramaForgeJobProgressEvent | null = activeJobProgress ?? (() => {
    const running = jobs.find((j) => j.status === "running") ?? jobs.find((j) => j.status === "queued");
    if (!running) return null;
    if ((running.progressTotal ?? 0) > 0) {
      return {
        jobId: running.id,
        type: running.jobType,
        status: running.status,
        current: running.progressCurrent ?? 0,
        total: running.progressTotal ?? 0,
        message: running.progressMessage ?? t("dramaforge.workspace.processingEllipsis"),
        episodeId: running.episodeId ?? undefined,
        targetId: running.targetId ?? undefined,
      };
    }
    return {
      jobId: running.id,
      type: running.jobType,
      status: running.status,
      current: 0,
      total: 0,
      message: running.progressMessage ?? t("dramaforge.workspace.jobTypeProcessing", { type: jobTypeLabel(running.jobType, t) }),
      episodeId: running.episodeId ?? undefined,
      targetId: running.targetId ?? undefined,
    };
  })();

  const navItems = DRAMA_FORGE_PIPELINE_STAGES.map((s) => ({
    id: s.id as WizardStep,
    label: pipelineStageLabel(s.id, t),
    hint: pipelineStageDescription(s.id, t),
  }));

  const resourceLibItems: {
    id: WizardStep;
    label: string;
    count: number;
    filter?: DramaForgeAssetType;
  }[] = [
    { id: "story_input", label: t("dramaforge.workspace.resourceLibConfig"), count: sourceText.trim() ? 1 : 0 },
    { id: "assets_locked", label: t("dramaforge.workspace.characterLibrary"), count: overview?.assetCounts.character ?? 0, filter: "character" },
    { id: "assets_locked", label: t("dramaforge.workspace.sceneLibrary"), count: overview?.assetCounts.scene ?? 0, filter: "scene" },
    { id: "assets_locked", label: t("dramaforge.workspace.propLibrary"), count: overview?.assetCounts.prop ?? 0, filter: "prop" },
  ];

  const filteredAssets = assetFilter ? assets.filter((a) => a.type === assetFilter) : assets;

  const assetListTitle = assetFilter
    ? t("dramaforge.workspace.assetCountTitle", { type: t(`dramaforge.assetType.${assetFilter}`), count: filteredAssets.length })
    : t("dramaforge.workspace.allAssetsTitle", { count: filteredAssets.length });

  function openResourceLib(item: (typeof resourceLibItems)[number]) {
    setWizardStep(item.id);
    if (item.filter) {
      setAssetFilter(item.filter);
      setAssetType(item.filter);
    }
  }

  const sidePanel = (
    <aside className="hidden w-[240px] shrink-0 flex-col border-r border-[var(--ar-hairline)] bg-white xl:flex">
      <div className="border-b border-[var(--ar-hairline)] p-4">
        <div className="mb-3 flex items-center justify-between gap-2">
          <span className="text-xs font-semibold text-[var(--ar-text)]">{t("dramaforge.workspace.sidebarTitle")}</span>
          <Link href="/projects" className="text-[10px] text-[var(--ar-text-4)] hover:text-[var(--ar-accent-2)]">
            {t("dramaforge.workspace.backToProjects")}
          </Link>
        </div>
        <h1 className="truncate text-sm font-semibold text-[var(--ar-text)]">{t("dramaforge.workspace.projectTitle", { name: projectName })}</h1>
        {overview && (
          <div className="mt-3">
            <div className="mb-1.5 flex items-center justify-between gap-2 text-[11px]">
              <span className="text-[var(--ar-text-3)]">{t("dramaforge.workspace.progress", { n: displayProgress })}</span>
              <span className="inline-block rounded-full bg-[var(--ar-accent-dim)] px-2 py-0.5 text-center text-[9px] font-semibold uppercase tracking-wide text-[var(--ar-accent-2)]">
                {pipelineStageLabel(overview.stage, t)}
              </span>
            </div>
            <div className="h-1.5 overflow-hidden rounded-full bg-slate-200/70">
              <div
                className="h-full rounded-full transition-all"
                style={{
                  width: `${displayProgress}%`,
                  background: "#7c3aed",
                }}
              />
            </div>
          </div>
        )}
      </div>

      <DfScrollArea className="flex-1 p-3">
        <p className="mb-2 px-1 text-[10px] font-medium tracking-wide text-[var(--ar-text-4)]">
          {t("dramaforge.workspace.tagoMovieSixSteps")}
        </p>
        <div className="space-y-0.5">
          {DRAMA_FORGE_PIPELINE_STAGES.map((stage, index) => {
            const active = stage.id === wizardStep;
            const done = index < stageIndex;
            return (
              <button
                key={stage.id}
                type="button"
                onClick={() => {
                  const serverStage = overview?.stage ?? "story_input";
                  if (
                    canAdvanceToStep(stage.id, serverStage)
                    || canAdvanceToStep(stage.id, wizardStep)
                  ) {
                    setWizardStep(stage.id);
                  }
                }}
                className={`df-pipeline-step ${active ? "active" : ""} ${done ? "done" : ""}`}
              >
                <span className="df-pipeline-dot">
                  {done ? "✓" : String(index + 1).padStart(2, "0")}
                </span>
                <span className="min-w-0">
                  <span
                    className={`block text-sm font-medium ${
                      active ? "text-[var(--ar-accent-2)]" : done ? "text-[var(--ar-text)]" : "text-[var(--ar-text-3)]"
                    }`}
                  >
                    {pipelineStageLabel(stage.id, t)}
                  </span>
                  <span className="mt-0.5 block text-[10px] leading-snug text-[var(--ar-text-4)]">
                    {pipelineStageDescription(stage.id, t)}
                  </span>
                </span>
              </button>
            );
          })}
        </div>

        <div className="mt-4 space-y-1 border-t border-[var(--ar-hairline)] pt-3">
          <p className="mb-1 px-1 text-[10px] font-medium text-[var(--ar-text-4)]">{t("dramaforge.workspace.resourceLibrary")}</p>
          {resourceLibItems.map((item) => {
            const active =
              item.id === "story_input"
                ? wizardStep === "story_input"
                : wizardStep === "assets_locked" && (item.filter ? assetFilter === item.filter : assetFilter === null);
            return (
              <button
                key={item.label}
                type="button"
                onClick={() => openResourceLib(item)}
                className={`flex w-full items-center justify-between rounded-lg px-2.5 py-1.5 text-left text-xs transition ${
                  active
                    ? "bg-[var(--ar-accent-dim)] text-[var(--ar-accent-2)]"
                    : "text-[var(--ar-text-3)] hover:bg-white/65"
                }`}
              >
                <span>{item.label}</span>
                <span className="text-[var(--ar-text-4)]">{item.count}</span>
              </button>
            );
          })}
        </div>

        {episodes.length > 0 && (
          <div className="mt-3 space-y-1 border-t border-[var(--ar-hairline)] pt-3">
            <p className="mb-1 px-1 text-[10px] font-medium text-[var(--ar-text-4)]">{t("dramaforge.workspace.episodes")}</p>
            {episodes.map((episode) => (
              <div
                key={episode.id}
                className={`group flex items-start gap-1 rounded-lg transition ${
                  selectedEpisodeId === episode.id
                    ? "bg-[var(--ar-accent-dim)] text-[var(--ar-accent-2)]"
                    : "text-[var(--ar-text-3)] hover:bg-white/65"
                }`}
              >
                <button
                  type="button"
                  onClick={() => {
                    setWizardStep("script_locked");
                    episodeScriptDirtyRef.current = false;
                    setSelectedEpisodeId(episode.id);
                    setEpisodeTitle(episode.title);
                    setEpisodeScript(episode.scriptJson ?? "");
                  }}
                  className="min-w-0 flex-1 flex flex-col px-2.5 py-1.5 text-left text-xs"
                >
                  <span className="truncate">
                    E{episode.episodeNumber} · {episode.title}
                  </span>
                  <span className="text-[var(--ar-text-4)]">{t("dramaforge.workspace.shotCount", { n: episode.shotCount })}</span>
                </button>
                <button
                  type="button"
                  title={t("dramaforge.workspace.deleteEpisode")}
                  disabled={busy}
                  onClick={(e) => {
                    e.stopPropagation();
                    void handleDeleteEpisode(episode.id);
                  }}
                  className="mr-1 mt-1 shrink-0 rounded px-1.5 py-0.5 text-[10px] text-[var(--ar-text-4)] opacity-60 hover:bg-rose-500/10 hover:text-[var(--ar-danger)] hover:opacity-100 disabled:opacity-30"
                >
                  {t("dramaforge.workspace.delete")}
                </button>
              </div>
            ))}
          </div>
        )}
      </DfScrollArea>

      <div className="border-t border-[var(--ar-hairline)] p-4">
        <p className="mb-2 text-[10px] font-medium text-[var(--ar-text-4)]">{t("dramaforge.workspace.projectInfo")}</p>
        <dl className="space-y-1.5 text-[11px] text-[var(--ar-text-3)]">
          <div className="flex justify-between gap-2">
            <dt className="text-[var(--ar-text-4)]">{t("dramaforge.workspace.projectType")}</dt>
            <dd>
              {config?.contentMode === "narration"
                ? t("dramaforge.workspace.contentModeNarration")
                : config?.contentMode === "ad"
                  ? t("dramaforge.workspace.contentModeAd")
                  : t("dramaforge.workspace.contentModeDrama")}
            </dd>
          </div>
          <div className="flex justify-between gap-2">
            <dt className="text-[var(--ar-text-4)]">{t("dramaforge.workspace.aspectRatio")}</dt>
            <dd>
              {config?.aspectRatio ?? "16:9"} / {config?.videoQuality ?? "480p"}
            </dd>
          </div>
          <div className="flex justify-between gap-2">
            <dt className="text-[var(--ar-text-4)]">{t("dramaforge.workspace.frameRate")}</dt>
            <dd>24 FPS</dd>
          </div>
          <div className="flex justify-between gap-2">
            <dt className="text-[var(--ar-text-4)]">{t("dramaforge.workspace.shotsInfo")}</dt>
            <dd>{overview?.shotCount ?? 0}</dd>
          </div>
          <div className="flex justify-between gap-2">
            <dt className="text-[var(--ar-text-4)]">{t("dramaforge.workspace.assetsInfo")}</dt>
            <dd>
              {(overview?.assetCounts.character ?? 0) +
                (overview?.assetCounts.scene ?? 0) +
                (overview?.assetCounts.prop ?? 0)}
            </dd>
          </div>
        </dl>
        {onOpenCanvas && (
          <button
            type="button"
            onClick={onOpenCanvas}
            className="mt-3 w-full rounded-lg border border-[var(--ar-hairline)] px-2 py-1.5 text-[11px] text-[var(--ar-text-4)] hover:border-[var(--ar-accent-soft)] hover:text-[var(--ar-accent-2)]"
          >
            {t("dramaforge.workspace.openWorkflowCanvas")}
          </button>
        )}
      </div>
    </aside>
  );

  const taskPanelContent = (
    <>
      <DramaForgePanel title={t("dramaforge.workspace.nextStep")}>
        <DramaForgePrimaryButton
          disabled={busy || !apiKey}
          className="mb-3 w-full"
          onClick={() => void handleRunWorkflow()}
        >
          {t("dramaforge.workspace.runPipelineOneClick")}
        </DramaForgePrimaryButton>
        <ul className="space-y-2 text-xs text-[var(--ar-text-2)]">
          {(overview?.nextActions ?? []).map((action) => (
            <li key={action} className="rounded-lg border border-[var(--ar-hairline)] px-3 py-2">
              {action}
            </li>
          ))}
        </ul>
      </DramaForgePanel>

      <DramaForgePanel title={t("dramaforge.workspace.projectStats")}>
        <div className="grid grid-cols-2 gap-2">
          <DramaForgeStat label={t("dramaforge.workspace.characterLibrary")} value={overview?.assetCounts.character ?? 0} />
          <DramaForgeStat label={t("dramaforge.workspace.sceneLibrary")} value={overview?.assetCounts.scene ?? 0} />
          <DramaForgeStat label={t("dramaforge.workspace.propLibrary")} value={overview?.assetCounts.prop ?? 0} />
          <DramaForgeStat label={t("dramaforge.workspace.shotsStat")} value={overview?.shotCount ?? 0} />
        </div>
      </DramaForgePanel>

      {overview?.consistency && (
        <DramaForgePanel title={t("dramaforge.workspace.consistencyCheck")}>
          <div className="grid grid-cols-2 gap-2 text-xs">
            <DramaForgeStat
              label={t("dramaforge.workspace.missingDesignImages")}
              value={overview.consistency.assetsMissingDesignImage}
            />
            <DramaForgeStat
              label={t("dramaforge.workspace.missingVoiceSamples")}
              value={overview.consistency.charactersMissingVoiceSample}
            />
            <DramaForgeStat
              label={t("dramaforge.workspace.unboundShots")}
              value={overview.consistency.shotsMissingBindings}
            />
            <DramaForgeStat
              label={t("dramaforge.workspace.readyForVideo")}
              value={overview.consistency.shotsReadyForVideo}
            />
          </div>
          {overview.consistency.warnings.length > 0 && (
            <ul className="mt-2 max-h-32 space-y-1 overflow-auto text-[11px] text-[var(--ar-text-3)]">
              {overview.consistency.warnings.map((w) => (
                <li key={w} className="rounded border border-[var(--ar-hairline)] px-2 py-1">
                  {w}
                </li>
              ))}
            </ul>
          )}
        </DramaForgePanel>
      )}

      {jobs.length > 0 && (
        <DramaForgePanel title={t("dramaforge.workspace.jobQueue")}>
          <div className="mb-2 flex items-center justify-between gap-2">
            <span className="text-[10px] text-[var(--ar-text-4)]">
              {jobs.filter((j) => j.status === "queued").length > 0
                ? t("dramaforge.workspace.queuedTasksCount", { n: jobs.filter((j) => j.status === "queued").length })
                : t("dramaforge.workspace.noQueuedTasks")}
            </span>
            {jobs.some((j) => j.status === "completed" || j.status === "failed" || j.status === "cancelled") && (
              <button
                type="button"
                className="text-[10px] text-[var(--ar-text-3)] underline hover:text-[var(--ar-accent-2)]"
                onClick={() => void runAction(async () => { await clearFinishedDramaForgeJobs(projectId); })}
              >
                {t("dramaforge.workspace.clearFinished")}
              </button>
            )}
          </div>
          <div className="max-h-64 space-y-1.5 overflow-auto text-xs text-[var(--ar-text-3)]">
            {sortJobsForDisplay(jobs).map((job) => (
              <div key={job.id} className="rounded-lg border border-[var(--ar-hairline)] px-2 py-1.5">
                <div className="flex items-start justify-between gap-2">
                  <div className="min-w-0">
                    <span className="text-[var(--ar-text-2)]">{jobTypeLabel(job.jobType, t)}</span>
                    <span className="mx-1">·</span>
                    <span>{jobStatusLabel(job.status, t)}</span>
                    {job.status === "queued" && job.queuePosition != null && job.queuePosition > 0 && (
                      <span className="ml-1 text-[var(--ar-text-4)]">#{job.queuePosition}</span>
                    )}
                  </div>
                  <div className="flex shrink-0 gap-1">
                    {job.status === "queued" && (
                      <button
                        type="button"
                        className="text-[10px] text-[var(--ar-danger)] hover:underline"
                        onClick={() => void runAction(async () => { await cancelDramaForgeJob(projectId, job.id); })}
                      >
                        {t("dramaforge.workspace.cancel")}
                      </button>
                    )}
                    {(job.status === "failed" || job.status === "cancelled") && (
                      <button
                        type="button"
                        className="text-[10px] text-[var(--ar-accent-2)] hover:underline"
                        onClick={() => void runAction(async () => { await retryDramaForgeJob(projectId, job.id); })}
                      >
                        {t("dramaforge.workspace.retry")}
                      </button>
                    )}
                  </div>
                </div>
                {(job.progressTotal ?? 0) > 0 && (
                  <div className="mt-1.5">
                    <div className="mb-1 flex justify-between text-[10px] text-[var(--ar-text-4)]">
                      <span>{job.progressMessage ?? t("dramaforge.workspace.processing")}</span>
                      <span className="num">{job.progressCurrent}/{job.progressTotal}</span>
                    </div>
                    <div className="h-1 overflow-hidden rounded-full bg-slate-200/75">
                      <div
                        className="h-full rounded-full bg-[var(--ar-accent-2)] transition-all"
                        style={{
                          width: `${Math.round(((job.progressCurrent ?? 0) / (job.progressTotal ?? 1)) * 100)}%`,
                        }}
                      />
                    </div>
                  </div>
                )}
                {job.status === "failed" ? (
                  <div className="mt-0.5 whitespace-pre-wrap text-[var(--ar-danger)]">
                    {job.errorMessage?.trim() || t("dramaforge.workspace.jobFailedNoDetail")}
                  </div>
                ) : job.errorMessage ? (
                  <div className="mt-0.5 whitespace-pre-wrap text-[var(--ar-danger)]">{job.errorMessage}</div>
                ) : null}
              </div>
            ))}
          </div>
        </DramaForgePanel>
      )}

      <div className="flex flex-wrap gap-2">
        <DramaForgeSecondaryButton disabled={busy} className="text-xs" onClick={() => void runAction(async () => { await exportDramaForgeProject(projectId); })}>
          {t("dramaforge.workspace.exportZip")}
        </DramaForgeSecondaryButton>
        <DramaForgeSecondaryButton
          disabled={busy || !selectedEpisodeId}
          className="text-xs"
          onClick={() => selectedEpisodeId && void runAction(async () => { await exportDramaForgeJianying(projectId, selectedEpisodeId); })}
        >
          {t("dramaforge.workspace.jianyingDraft")}
        </DramaForgeSecondaryButton>
      </div>
      {exportUrl && (
        <a href={exportUrl} target="_blank" rel="noreferrer" className="block text-xs text-[var(--ar-accent-2)] underline">
          {t("dramaforge.workspace.downloadLatestExport")}
        </a>
      )}
      {compositions.filter((c) => c.status === "completed" && c.outputUrl).map((c) => (
        <video key={c.id} src={resolveComposeOutputUrl(c.outputUrl)} controls className="w-full rounded-lg bg-black" />
      ))}
    </>
  );

  const rightPanelNode = (
    <aside className="hidden w-[320px] shrink-0 flex-col border-l border-[var(--ar-hairline)] bg-white lg:flex">
      <div className="flex items-center justify-between border-b border-[var(--ar-hairline)] px-4 py-3">
        <div>
          <div className="text-sm font-semibold text-[var(--ar-text)]">{t("dramaforge.workspace.aiDirectorAssistant")}</div>
          <div className="mt-0.5 flex items-center gap-1.5 text-[10px] text-[var(--ar-good)]">
            <span className="h-1.5 w-1.5 rounded-full bg-[var(--ar-good)]" />
            {t("dramaforge.workspace.online")}
          </div>
        </div>
      </div>
      <div className="flex border-b border-[var(--ar-hairline)]">
        {(
          [
            ["agent", t("dramaforge.workspace.agentTab")],
            ["tasks", t("dramaforge.workspace.tasksTab")],
            ["preview", t("dramaforge.workspace.previewTab")],
          ] as [RightPanelId, string][]
        ).map(([id, label]) => (
          <button
            key={id}
            type="button"
            onClick={() => setRightPanel(id)}
            className={`flex-1 px-3 py-2.5 text-xs font-medium transition ${
              rightPanel === id
                ? "border-b-2 border-[var(--ar-accent)] text-[var(--ar-accent-2)]"
                : "text-[var(--ar-text-3)] hover:text-[var(--ar-text)]"
            }`}
          >
            {label}
            {id === "preview" && shotPreview ? (
              <span className="ml-1 inline-block h-1.5 w-1.5 rounded-full bg-[var(--ar-accent)]" />
            ) : null}
          </button>
        ))}
      </div>
      <div className="flex min-h-0 flex-1 flex-col">
        {rightPanel === "preview" ? (
          <DfScrollArea className="flex-1 p-4">
            {shotPreview?.videoUrl ? (
              <div className="space-y-3">
                <div className="flex items-start justify-between gap-2">
                  <div>
                    <div className="text-sm font-semibold text-[var(--ar-text)]">
                      {t("dramaforge.workspace.shotNumber", { n: shotPreview.shotNumber })}
                    </div>
                    <DfExpandableText
                      text={shotPreview.description || t("dramaforge.workspace.shotVideoPreview")}
                      maxLines={3}
                      textClassName="mt-1 text-[11px] leading-relaxed text-[var(--ar-text-4)]"
                    />
                  </div>
                  <button
                    type="button"
                    className="shrink-0 text-[11px] text-[var(--ar-text-4)] hover:text-[var(--ar-accent-2)]"
                    onClick={() => setShotPreview(null)}
                  >
                    {t("dramaforge.workspace.close")}
                  </button>
                </div>
                <video
                  key={shotPreview.videoUrl}
                  src={shotPreview.videoUrl}
                  controls
                  autoPlay
                  playsInline
                  className="w-full rounded-xl border border-[var(--ar-hairline)] bg-black"
                />
                <a
                  href={shotPreview.videoUrl}
                  target="_blank"
                  rel="noreferrer"
                  className="inline-block text-[11px] text-[var(--ar-accent-2)] hover:underline"
                >
                  {t("dramaforge.workspace.openOriginalInNewWindow")}
                </a>
              </div>
            ) : (
              <div className="flex h-full min-h-[200px] flex-col items-center justify-center gap-2 text-center">
                <p className="text-sm text-[var(--ar-text-3)]">{t("dramaforge.workspace.noPreview")}</p>
                <p className="text-[11px] text-[var(--ar-text-4)]">
                  {t("dramaforge.workspace.previewHint")}
                </p>
              </div>
            )}
          </DfScrollArea>
        ) : rightPanel === "agent" ? (
          <DramaForgeAgentPanel
            projectId={projectId}
            selectedEpisodeId={selectedEpisodeId}
            apiKey={apiKey}
            disabled={busy}
            progress={displayProgress}
            shotCount={overview?.shotCount ?? 0}
            videoDoneCount={overview?.videoDoneCount ?? 0}
            onRunWorkflow={() => void handleRunWorkflow()}
            onBatchGenerate={() => {
              if (!selectedEpisodeId || !apiKey) return;
              void runAction(async () => {
                await generateDramaForgeVideos(projectId, selectedEpisodeId, apiKey);
                await syncDramaForgeVideos(projectId, selectedEpisodeId, apiKey);
              });
            }}
            onExport={() => void runAction(async () => { await exportDramaForgeProject(projectId); })}
            onExecuted={() => {
              void refreshAll().then(() => {
                if (selectedEpisodeId) void refreshShots(selectedEpisodeId);
              });
            }}
          />
        ) : (
          <DfScrollArea className="flex-1 space-y-4 p-4">{taskPanelContent}</DfScrollArea>
        )}
      </div>
    </aside>
  );

  return (
    <div className="dramaforge-theme flex h-full min-h-0">
      {sidePanel}

      <div className="flex min-w-0 flex-1 flex-col">
        <header className="flex flex-wrap items-center justify-between gap-3 border-b border-[var(--ar-hairline)] px-4 py-3 lg:hidden">
          <div>
            <p className="text-[10px] uppercase tracking-widest text-[var(--ar-text-4)]">DramaForge Workspace</p>
            <h1 className="text-base font-semibold">{projectName}</h1>
          </div>
          {overview && <DramaForgeBadge tone="accent">{displayProgress}%</DramaForgeBadge>}
        </header>

        <div className="flex gap-1 overflow-x-auto border-b border-[var(--ar-hairline)] px-3 py-2 xl:hidden">
          {navItems.map((item) => (
            <button
              key={item.id}
              type="button"
              onClick={() => {
                setWizardStep(item.id);
                if (item.id === "assets_locked") setAssetFilter(null);
              }}
              className={`shrink-0 rounded-full px-3 py-1.5 text-xs ${
                wizardStep === item.id
                  ? "bg-[var(--ar-accent-dim)] text-[var(--ar-accent-2)]"
                  : "text-[var(--ar-text-3)]"
              }`}
            >
              {item.label}
            </button>
          ))}
        </div>

        {error ? (
          <div className="mx-4 mt-3 flex items-start gap-2 rounded-lg border border-red-300/60 bg-red-50/90 px-3 py-2 text-sm text-red-600">
            <div className="min-w-0 flex-1 whitespace-pre-wrap break-words">{error}</div>
            <button
              type="button"
              className="shrink-0 rounded px-1.5 py-0.5 text-xs opacity-70 hover:bg-rose-500/15 hover:opacity-100"
              onClick={() => setError(null)}
              aria-label={t("dramaforge.workspace.closeError")}
            >
              {t("dramaforge.workspace.close")}
            </button>
          </div>
        ) : null}

        {displayJobProgress && (
          <div className="mx-4 mt-3">
            <DramaForgeJobProgressBar
              label={jobTypeLabel(displayJobProgress.type, t)}
              current={displayJobProgress.current}
              total={displayJobProgress.total}
              message={displayJobProgress.message}
            />
          </div>
        )}

        <div className="mx-4 mt-3">
          <DramaForgeWizard
            currentStage={overview?.stage ?? "story_input"}
            activeStep={wizardStep}
            onStepChange={setWizardStep}
          />
        </div>

        <div className="flex-1 overflow-auto p-4 df-scroll-area">
        {loading ? (
          <div className="text-sm text-[var(--ar-text-3)]">{t("dramaforge.workspace.loading")}</div>
        ) : contentView === "post" ? (
          <div className="mx-auto max-w-4xl space-y-4">
            <DramaForgePanel title={t("dramaforge.workspace.projectOverview")}>
              <textarea
                value={projectSummary}
                onChange={(e) => setProjectSummary(e.target.value)}
                rows={5}
                placeholder={t("dramaforge.workspace.projectSummaryPlaceholder")}
                className="dramaforge-input w-full rounded-xl px-3 py-2 text-sm"
              />
            </DramaForgePanel>
            <DramaForgePanel title={t("dramaforge.workspace.worldview")}>
              <textarea
                value={worldview}
                onChange={(e) => setWorldview(e.target.value)}
                rows={4}
                placeholder={t("dramaforge.workspace.worldviewPlaceholder")}
                className="dramaforge-input w-full rounded-xl px-3 py-2 text-sm"
              />
              <DramaForgePrimaryButton className="mt-3" disabled={busy} onClick={() => void handleSaveProjectMeta()}>
                {t("dramaforge.workspace.saveSummary")}
              </DramaForgePrimaryButton>
            </DramaForgePanel>
            <DramaForgePanel title={t("dramaforge.workspace.assetProgress")}>
              <div className="grid gap-3 sm:grid-cols-3">
                {([
                  [t("dramaforge.assetType.character"), overview?.assetCounts.character ?? 0],
                  [t("dramaforge.assetType.scene"), overview?.assetCounts.scene ?? 0],
                  [t("dramaforge.assetType.prop"), overview?.assetCounts.prop ?? 0],
                ] as [string, number][]).map(([label, count]) => (
                  <div key={label}>
                    <div className="mb-1 flex justify-between text-xs text-[var(--ar-text-3)]">
                      <span>{label}</span>
                      <span>{count}</span>
                    </div>
                    <div className="h-1.5 overflow-hidden rounded-full bg-slate-200/75">
                      <div
                        className="h-full rounded-full bg-[#7c3aed]"
                        style={{ width: `${Math.min(count * 10, 100)}%` }}
                      />
                    </div>
                  </div>
                ))}
              </div>
            </DramaForgePanel>
            {selectedEpisodeId && (
              <DramaForgePanel title={t("dramaforge.workspace.timelineLite")}>
                <div className="mb-3 flex flex-wrap gap-2">
                  {episodes.map((episode) => (
                    <button
                      key={episode.id}
                      type="button"
                      onClick={() => setSelectedEpisodeId(episode.id)}
                      className={`rounded-full border px-3 py-1.5 text-xs transition ${
                        selectedEpisodeId === episode.id
                          ? "border-[var(--ar-accent-soft)] bg-[var(--ar-accent-dim)] text-[var(--ar-accent-2)]"
                          : "border-[var(--ar-hairline)] text-[var(--ar-text-3)]"
                      }`}
                    >
                      E{episode.episodeNumber} · {episode.title}
                    </button>
                  ))}
                </div>
                <DramaForgeTimelineEditor
                  shots={shots}
                  timelineJson={episodes.find((e) => e.id === selectedEpisodeId)?.timelineJson}
                  disabled={busy}
                  onChange={(timeline) => void handleSaveTimeline(timeline)}
                />
                <div className="mt-3 flex flex-wrap gap-2">
                  <DramaForgePrimaryButton
                    disabled={busy || !selectedEpisodeId}
                    onClick={() => selectedEpisodeId && void runAction(async () => {
                      let readiness = composeReadiness;
                      if (!readiness) {
                        const res = await fetchDramaForgeComposeReadiness(
                          projectId,
                          selectedEpisodeId,
                        );
                        readiness = res.data;
                        setComposeReadiness(readiness);
                      }
                      const { blockers, warnings, videoDoneShots, totalShots } = readiness;
                      if (blockers.length > 0) {
                        throw new Error(blockers.join("\n"));
                      }
                      const lines = [
                        `${t("dramaforge.workspace.videoReadyCount", { done: videoDoneShots, total: totalShots })}`,
                        ...warnings,
                      ].filter(Boolean);
                      const ok = await confirm({
                        title: t("dramaforge.workspace.composeEpisode"),
                        message: t("dramaforge.workspace.composeEpisodeConfirm", { lines: lines.join("\n") }),
                        confirmLabel: t("dramaforge.workspace.startCompose"),
                        variant: "default",
                      });
                      if (!ok) return;
                      await composeDramaForgeEpisode(projectId, selectedEpisodeId);
                    })}
                  >
                    {t("dramaforge.workspace.composeEpisodeButton")}
                  </DramaForgePrimaryButton>
                </div>
              </DramaForgePanel>
            )}
            {(() => {
              const episodeCompositions = compositions
                .filter((c) => !selectedEpisodeId || c.episodeId === selectedEpisodeId)
                .slice()
                .sort(
                  (a, b) =>
                    new Date(b.updatedAt || b.createdAt).getTime() -
                    new Date(a.updatedAt || a.createdAt).getTime(),
                );
              const playable = episodeCompositions.filter(
                (c) => c.status === "completed" && c.outputUrl?.trim(),
              );
              const latest = playable[0] ?? null;
              const running = episodeCompositions.find((c) => c.status === "running");
              return (
                <DramaForgePanel title={t("dramaforge.workspace.episodeComposeResults")}>
                  {running ? (
                    <p className="mb-3 text-sm text-[var(--ar-accent-2)]">{t("dramaforge.workspace.composingInProgress")}</p>
                  ) : null}
                  {latest ? (
                    <div className="space-y-3">
                      <video
                        key={latest.id}
                        src={resolveComposeOutputUrl(latest.outputUrl)}
                        controls
                        className="w-full rounded-lg bg-black"
                      />
                      <div className="flex flex-wrap items-center gap-3 text-xs">
                        <a
                          href={resolveComposeOutputUrl(latest.outputUrl)}
                          target="_blank"
                          rel="noreferrer"
                          className="text-[var(--ar-accent-2)] underline"
                        >
                          {t("dramaforge.workspace.openOrDownload")}
                        </a>
                        <span className="text-[var(--ar-text-4)]">
                          {new Date(latest.updatedAt || latest.createdAt).toLocaleString()}
                        </span>
                      </div>
                      {latest.errorMessage?.trim() ? (
                        <p className="whitespace-pre-wrap text-xs text-[var(--ar-warn)]">
                          {latest.errorMessage}
                        </p>
                      ) : null}
                      {playable.length > 1 ? (
                        <details className="text-xs text-[var(--ar-text-3)]">
                          <summary className="cursor-pointer">{t("dramaforge.workspace.historyComposeCount", { n: playable.length - 1 })}</summary>
                          <ul className="mt-2 space-y-2">
                            {playable.slice(1).map((c) => (
                              <li key={c.id} className="rounded-lg border border-[var(--ar-hairline)] p-2">
                                <a
                                  href={resolveComposeOutputUrl(c.outputUrl)}
                                  target="_blank"
                                  rel="noreferrer"
                                  className="text-[var(--ar-accent-2)] underline"
                                >
                                  {new Date(c.updatedAt || c.createdAt).toLocaleString()}
                                </a>
                                {c.errorMessage?.trim() ? (
                                  <p className="mt-1 text-[var(--ar-warn)]">{c.errorMessage}</p>
                                ) : null}
                              </li>
                            ))}
                          </ul>
                        </details>
                      ) : null}
                    </div>
                  ) : (
                    <p className="text-sm text-[var(--ar-text-3)]">
                      {t("dramaforge.workspace.noComposeYet")}
                    </p>
                  )}
                </DramaForgePanel>
              );
            })()}
            {episodes.length > 0 && (
              <DramaForgePanel title={t("dramaforge.workspace.script")}>
                <p className="mb-3 text-xs text-[var(--ar-text-3)]">
                  {t("dramaforge.workspace.episodesGenerated", { n: episodes.length })}
                </p>
                <div className="space-y-2">
                  {episodes.map((episode) => (
                    <button
                      key={episode.id}
                      type="button"
                      onClick={() => {
                        setWizardStep("script_locked");
                        episodeScriptDirtyRef.current = false;
                        setSelectedEpisodeId(episode.id);
                        setEpisodeScript(episode.scriptJson ?? "");
                      }}
                      className="block w-full rounded-xl border border-[var(--ar-hairline)] px-3 py-2.5 text-left transition hover:border-[var(--ar-accent-soft)] hover:bg-[var(--ar-accent-dim)]"
                    >
                      <div className="flex items-center justify-between gap-2">
                        <span className="text-sm font-medium text-[var(--ar-text)]">
                          {t("dramaforge.workspace.episodeTitleWithName", { n: episode.episodeNumber, title: episode.title })}
                        </span>
                        <DramaForgeBadge tone="good">{t("dramaforge.workspace.shotCount", { n: episode.shotCount })}</DramaForgeBadge>
                      </div>
                      <p className="mt-1 line-clamp-2 text-xs text-[var(--ar-text-3)]">
                        {scriptPreview(episode.scriptJson, t)}
                      </p>
                    </button>
                  ))}
                </div>
              </DramaForgePanel>
            )}
            <DramaForgePanel title={t("dramaforge.workspace.pipelineOverview")}>
            {(!config?.sourceText?.trim() && overview?.stage === "story_input") && (
              <div className="mb-4 rounded-xl border border-[var(--ar-warn)]/35 bg-[var(--ar-warn)]/10 px-4 py-3 text-sm text-[var(--ar-warn)]">
                <p className="font-medium">{t("dramaforge.workspace.noScriptImported")}</p>
                <p className="mt-1 text-[var(--ar-text-2)]">
                  {t("dramaforge.workspace.noScriptImportedHint")}
                </p>
                <DramaForgePrimaryButton className="mt-3" onClick={() => setWizardStep("story_input")}>
                  {t("dramaforge.workspace.goToSourceConfig")}
                </DramaForgePrimaryButton>
              </div>
            )}
            <p className="mb-4 text-sm text-[var(--ar-text-2)]">
              {t("dramaforge.workspace.pipelineOverviewHint")}
            </p>
            <div className="grid gap-3 sm:grid-cols-2">
              <DramaForgeStat label={t("dramaforge.assetType.character")} value={overview?.assetCounts.character ?? 0} />
              <DramaForgeStat label={t("dramaforge.assetType.scene")} value={overview?.assetCounts.scene ?? 0} />
              <DramaForgeStat label={t("dramaforge.assetType.prop")} value={overview?.assetCounts.prop ?? 0} />
              <DramaForgeStat label={t("dramaforge.workspace.shotsStat")} value={overview?.shotCount ?? 0} />
            </div>
            <div className="mt-4 lg:hidden">
              <DramaForgePrimaryButton
                disabled={busy || !apiKey}
                className="w-full"
                onClick={() => void handleRunWorkflow()}
              >
                {t("dramaforge.workspace.runPipelineOneClick")}
              </DramaForgePrimaryButton>
            </div>
          </DramaForgePanel>
          </div>
        ) : contentView === "source" ? (
          <div className="mx-auto max-w-3xl space-y-4">
              <DramaForgePanel title={t("dramaforge.workspace.contentMode")}>
              <div className="flex flex-wrap gap-2">
                {(["drama", "narration", "ad"] as DramaForgeContentMode[]).map((mode) => (
                  <button
                    key={mode}
                    type="button"
                    onClick={() => setConfig((prev) => (prev ? { ...prev, contentMode: mode } : prev))}
                    className={`rounded-full border px-3 py-1.5 text-sm transition ${
                      config?.contentMode === mode
                        ? "border-[var(--ar-accent-soft)] bg-[var(--ar-accent-dim)] text-[var(--ar-accent-2)]"
                        : "border-[var(--ar-hairline)] text-[var(--ar-text-3)] hover:text-[var(--ar-text)]"
                    }`}
                  >
                    {mode === "narration"
                      ? t("dramaforge.workspace.contentModeNarration")
                      : mode === "ad"
                        ? t("dramaforge.workspace.contentModeAd")
                        : t("dramaforge.workspace.contentModeDrama")}
                  </button>
                ))}
              </div>
            </DramaForgePanel>
            <DramaForgePanel title={t("dramaforge.workspace.aspectRatioSetting")}>
              <p className="mb-3 text-xs text-[var(--ar-text-3)]">
                {t("dramaforge.workspace.aspectRatioHint")}
              </p>
              <div className="flex flex-wrap gap-2">
                {DRAMA_FORGE_ASPECT_RATIOS.map(({ value }) => (
                  <button
                    key={value}
                    type="button"
                    onClick={() =>
                      setConfig((prev) => (prev ? { ...prev, aspectRatio: value } : prev))
                    }
                    className={`rounded-full border px-3 py-1.5 text-sm transition ${
                      effectiveAspectRatio(config) === value
                        ? "border-[var(--ar-accent-soft)] bg-[var(--ar-accent-dim)] text-[var(--ar-accent-2)]"
                        : "border-[var(--ar-hairline)] text-[var(--ar-text-3)] hover:text-[var(--ar-text)]"
                    }`}
                  >
                    {aspectRatioLabel(value, t)}
                  </button>
                ))}
              </div>
            </DramaForgePanel>
            <DramaForgePanel title={t("dramaforge.workspace.videoGenerationMode")}>
              <p className="text-xs text-[var(--ar-text-3)]">
                {t("dramaforge.workspace.videoGenerationModeHint")}
              </p>
            </DramaForgePanel>
            <DramaForgePanel title={t("dramaforge.workspace.modelAndQuality")}>
              <p className="mb-3 text-xs text-[var(--ar-text-3)]">
                {t("dramaforge.workspace.modelAndQualityHint")}
              </p>
              <div className="grid gap-4 sm:grid-cols-2">
                <div>
                  <div className="mb-2 text-xs text-[var(--ar-text-4)]">{t("dramaforge.workspace.imageModel")}</div>
                  <div className="flex flex-wrap gap-2">
                    {DRAMA_FORGE_IMAGE_BACKENDS.map((item) => (
                      <button
                        key={item.value}
                        type="button"
                        onClick={() =>
                          setConfig((prev) => (prev ? { ...prev, imageBackend: item.value } : prev))
                        }
                        className={`rounded-full border px-3 py-1.5 text-sm transition ${
                          (config?.imageBackend ?? "doubao-seedream-5-0-260128") === item.value
                            ? "border-[var(--ar-accent-soft)] bg-[var(--ar-accent-dim)] text-[var(--ar-accent-2)]"
                            : "border-[var(--ar-hairline)] text-[var(--ar-text-3)] hover:text-[var(--ar-text)]"
                        }`}
                      >
                        {backendLabel(item.value, t)}
                      </button>
                    ))}
                  </div>
                </div>
                <div>
                  <div className="mb-2 text-xs text-[var(--ar-text-4)]">{t("dramaforge.workspace.videoModel")}</div>
                  <div className="flex flex-wrap gap-2">
                    {DRAMA_FORGE_VIDEO_BACKENDS.map((item) => (
                      <button
                        key={item.value}
                        type="button"
                        onClick={() =>
                          setConfig((prev) => (prev ? { ...prev, videoBackend: item.value } : prev))
                        }
                        className={`rounded-full border px-3 py-1.5 text-sm transition ${
                          (config?.videoBackend ?? "doubao-seedance-2-5-260628") === item.value
                            ? "border-[var(--ar-accent-soft)] bg-[var(--ar-accent-dim)] text-[var(--ar-accent-2)]"
                            : "border-[var(--ar-hairline)] text-[var(--ar-text-3)] hover:text-[var(--ar-text)]"
                        }`}
                      >
                        {backendLabel(item.value, t)}
                      </button>
                    ))}
                  </div>
                </div>
                <div>
                  <div className="mb-2 text-xs text-[var(--ar-text-4)]">{t("dramaforge.workspace.imageQuality")}</div>
                  <div className="flex flex-wrap gap-2">
                    {DRAMA_FORGE_QUALITIES.map((item) => (
                      <button
                        key={`img-${item}`}
                        type="button"
                        onClick={() =>
                          setConfig((prev) => (prev ? { ...prev, imageQuality: item } : prev))
                        }
                        className={`rounded-full border px-3 py-1.5 text-sm transition ${
                          (config?.imageQuality ?? "720p") === item
                            ? "border-[var(--ar-accent-soft)] bg-[var(--ar-accent-dim)] text-[var(--ar-accent-2)]"
                            : "border-[var(--ar-hairline)] text-[var(--ar-text-3)] hover:text-[var(--ar-text)]"
                        }`}
                      >
                        {item}
                      </button>
                    ))}
                  </div>
                </div>
                <div>
                  <div className="mb-2 text-xs text-[var(--ar-text-4)]">{t("dramaforge.workspace.videoQuality")}</div>
                  <div className="rounded-xl border border-[var(--ar-hairline)] px-3 py-2 text-sm text-[var(--ar-text-2)]">
                    {t("dramaforge.workspace.videoQualityFixedHint")}
                  </div>
                </div>
                <div>
                  <div className="mb-2 text-xs text-[var(--ar-text-4)]">{t("dramaforge.workspace.colorGrade")}</div>
                  <DfSelect
                    className="w-full"
                    value={colorGradePreset}
                    onChange={setColorGradePreset}
                    searchable={false}
                    options={[
                      { value: "none", label: t("dramaforge.workspace.colorGradeNone") },
                      { value: "neutral", label: t("dramaforge.workspace.colorGradeNeutral") },
                      { value: "warm", label: t("dramaforge.workspace.colorGradeWarm") },
                      { value: "cool", label: t("dramaforge.workspace.colorGradeCool") },
                      { value: "cinematic", label: t("dramaforge.workspace.colorGradeCinematic") },
                    ]}
                  />
                </div>
                <label className="flex items-center gap-2 text-xs text-[var(--ar-text-2)]">
                  <input
                    type="checkbox"
                    checked={mixDialogueAudioInCompose}
                    onChange={(e) => setMixDialogueAudioInCompose(e.target.checked)}
                  />
                  {t("dramaforge.workspace.mixDialogueAudioLabel")}
                </label>
                <label className="flex items-center gap-2 text-xs text-[var(--ar-text-2)]">
                  <input
                    type="checkbox"
                    checked={preferModelMultiShot}
                    onChange={(e) => setPreferModelMultiShot(e.target.checked)}
                  />
                  {t("dramaforge.workspace.preferModelMultiShotLabel")}
                </label>
                <div className="space-y-2 rounded-lg border border-[var(--ar-hairline)] p-3">
                  <div className="text-xs text-[var(--ar-text-4)]">{t("dramaforge.workspace.bgmLabel")}</div>
                  <input
                    type="file"
                    accept="audio/*"
                    disabled={busy || uploadingBgm}
                    className="block w-full text-xs text-[var(--ar-text-3)] file:mr-3 file:rounded-lg file:border-0 file:bg-[var(--ar-accent-dim)] file:px-3 file:py-1.5 file:text-xs file:text-[var(--ar-accent-2)]"
                    onChange={(e) => {
                      const file = e.target.files?.[0];
                      if (!file) return;
                      setUploadingBgm(true);
                      void uploadMedia(file)
                        .then((res) => setBgmUrl(res.data.url))
                        .finally(() => setUploadingBgm(false));
                    }}
                  />
                  {bgmUrl ? (
                    <div className="flex items-center justify-between gap-2">
                      <a href={bgmUrl} target="_blank" rel="noreferrer" className="truncate text-xs text-[var(--ar-accent-2)]">
                        {t("dramaforge.workspace.bgmBound")}
                      </a>
                      <button type="button" className="text-xs text-[var(--ar-text-4)]" onClick={() => setBgmUrl("")}>
                        {t("dramaforge.workspace.clear")}
                      </button>
                    </div>
                  ) : null}
                  <label className="flex items-center gap-2 text-xs text-[var(--ar-text-3)]">
                    {t("dramaforge.workspace.volume")}
                    <input
                      type="range"
                      min={0.05}
                      max={0.5}
                      step={0.01}
                      value={bgmVolume}
                      onChange={(e) => setBgmVolume(Number(e.target.value))}
                      className="flex-1"
                    />
                    <span>{Math.round(bgmVolume * 100)}%</span>
                  </label>
                </div>
                <div className="space-y-2 rounded-lg border border-[var(--ar-hairline)] p-3">
                  <label className="flex items-center gap-2 text-xs text-[var(--ar-text-2)]">
                    <input
                      type="checkbox"
                      checked={lipSyncEnabled}
                      onChange={(e) => setLipSyncEnabled(e.target.checked)}
                    />
                    {t("dramaforge.workspace.lipSyncLabel")}
                  </label>
                  {lipSyncEnabled && (
                    <input
                      value={lipSyncEndpoint}
                      onChange={(e) => setLipSyncEndpoint(e.target.value)}
                      placeholder="https://your-lipsync/api/sync"
                      className="dramaforge-input w-full rounded-xl px-3 py-2 text-xs"
                    />
                  )}
                </div>
              </div>
            </DramaForgePanel>
            <DramaForgePanel title={t("dramaforge.workspace.stylePromptTitle")}>
              <div className="mb-2 flex justify-end">
                <DramaForgeSecondaryButton
                  disabled={busy || !apiKey || optimizingKind === "style"}
                  className="text-xs"
                  onClick={() =>
                    void runPromptOptimize(
                      "style",
                      { kind: "style", draft: stylePrompt },
                      (text) => setStylePrompt(text),
                    )
                  }
                >
                  {optimizingKind === "style" ? t("dramaforge.workspace.aiOptimizing") : t("dramaforge.workspace.aiOptimize")}
                </DramaForgeSecondaryButton>
              </div>
              <textarea
                value={stylePrompt}
                onChange={(e) => setStylePrompt(e.target.value)}
                rows={3}
                className="dramaforge-input w-full rounded-xl px-3 py-2 text-sm"
                placeholder={t("dramaforge.workspace.stylePromptPlaceholder")}
              />
            </DramaForgePanel>
            <DramaForgePanel title={t("dramaforge.workspace.sourceTextPanel")}>
              <p className="mb-3 text-xs text-[var(--ar-text-3)]">
                {t("dramaforge.workspace.sourceTextHint")}
              </p>
              <input
                type="file"
                accept=".txt,.md"
                className="mb-3 block w-full text-sm text-[var(--ar-text-3)] file:mr-3 file:rounded-lg file:border-0 file:bg-[var(--ar-accent-dim)] file:px-3 file:py-1.5 file:text-xs file:text-[var(--ar-accent-2)]"
                onChange={(e) => {
                  const file = e.target.files?.[0];
                  if (file) void handleImportSourceFile(file);
                }}
              />
              <textarea
                value={sourceText}
                onChange={(e) => setSourceText(e.target.value)}
                rows={12}
                className="dramaforge-input w-full rounded-xl px-3 py-2 text-sm"
                placeholder={t("dramaforge.workspace.sourceTextPlaceholder")}
              />
              <div className="mt-3 flex flex-wrap gap-2">
                <DramaForgePrimaryButton disabled={busy} onClick={() => void handleSaveConfig()}>
                  {t("dramaforge.workspace.saveConfig")}
                </DramaForgePrimaryButton>
                <DramaForgeSecondaryButton
                  disabled={busy || !sourceText.trim()}
                  onClick={() => {
                    setWizardStep("script_locked");
                  }}
                >
                  {t("dramaforge.workspace.nextStepLockScript")}
                </DramaForgeSecondaryButton>
              </div>
            </DramaForgePanel>
          </div>
        ) : contentView === "assets" ? (
          <div className="grid gap-4 lg:grid-cols-[0.9fr_1.1fr]">
            <DramaForgePanel title={t("dramaforge.workspace.newAsset")}>
              <p className="mb-3 text-xs text-[var(--ar-text-4)]">
                {t("dramaforge.workspace.addAssetHint")}
              </p>
              <div className="space-y-3">
                <DfSelect
                  className="w-full"
                  value={assetType}
                  onChange={(v) => setAssetType(v as DramaForgeAssetType)}
                  searchable={false}
                  options={(Object.keys(DRAMA_FORGE_ASSET_TYPE_LABELS) as DramaForgeAssetType[]).map(
                    (type) => ({
                      value: type,
                      label: t(`dramaforge.assetType.${type}`),
                    }),
                  )}
                />
                <input
                  value={assetName}
                  onChange={(e) => setAssetName(e.target.value)}
                  placeholder={t("dramaforge.workspace.assetNamePlaceholder")}
                  className="dramaforge-input w-full rounded-xl px-3 py-2 text-sm"
                />
                <textarea
                  value={assetDescription}
                  onChange={(e) => setAssetDescription(e.target.value)}
                  rows={3}
                  placeholder={t("dramaforge.workspace.assetDescriptionPlaceholder")}
                  className="dramaforge-input w-full rounded-xl px-3 py-2 text-sm"
                />
                <textarea
                  value={assetDesignPrompt}
                  onChange={(e) => setAssetDesignPrompt(e.target.value)}
                  rows={3}
                  placeholder={t("dramaforge.workspace.assetDesignPromptPlaceholder")}
                  className="dramaforge-input w-full rounded-xl px-3 py-2 text-sm"
                />
                <div className="flex flex-wrap gap-2">
                  <DramaForgeSecondaryButton
                    disabled={busy || !apiKey || optimizingKind === "asset-new"}
                    className="text-xs"
                    onClick={() =>
                      void runPromptOptimize(
                        "asset-new",
                        {
                          kind: "asset_design",
                          draft: assetDesignPrompt || assetDescription,
                          assetType,
                          assetName: assetName.trim() || t("dramaforge.workspace.unnamed"),
                          assetDescription: assetDescription.trim() || undefined,
                        },
                        (text) => setAssetDesignPrompt(text),
                      )
                    }
                  >
                    {optimizingKind === "asset-new" ? t("dramaforge.workspace.optimizing") : t("dramaforge.workspace.aiOptimizeDesignPrompt")}
                  </DramaForgeSecondaryButton>
                </div>
                <div className="flex flex-wrap gap-2">
                  <DramaForgePrimaryButton disabled={busy} onClick={() => void handleCreateAsset()}>
                    {t("dramaforge.workspace.addAsset")}
                  </DramaForgePrimaryButton>
                  <DramaForgeSecondaryButton
                    disabled={busy || !apiKey}
                    onClick={() => void runAction(async () => { await extractDramaForgeAssets(projectId, apiKey); })}
                  >
                    {t("dramaforge.workspace.extractAssetsFromScript")}
                  </DramaForgeSecondaryButton>
                  <DramaForgeSecondaryButton
                    disabled={busy || !apiKey}
                    onClick={() => void runAction(async () => { await generateDramaForgeAssetDesigns(projectId, apiKey); })}
                  >
                    {t("dramaforge.workspace.batchGenerateDesigns")}
                  </DramaForgeSecondaryButton>
                  <DramaForgePrimaryButton disabled={busy} onClick={() => void handleLockAssets()}>
                    {t("dramaforge.workspace.confirmAssetLibrary")}
                  </DramaForgePrimaryButton>
                </div>
              </div>
            </DramaForgePanel>
            <DramaForgePanel title={t("dramaforge.workspace.assetList")}>
              <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
                <div className="flex flex-wrap gap-1.5">
                  {(
                    [
                      { id: null, label: t("dramaforge.workspace.all") },
                      { id: "character" as const, label: t("dramaforge.assetType.character") },
                      { id: "scene" as const, label: t("dramaforge.assetType.scene") },
                      { id: "prop" as const, label: t("dramaforge.assetType.prop") },
                    ] as const
                  ).map((chip) => (
                    <button
                      key={chip.label}
                      type="button"
                      onClick={() => {
                        setAssetFilter(chip.id);
                        if (chip.id) setAssetType(chip.id);
                      }}
                      className={`rounded-full border px-2.5 py-1 text-xs transition ${
                        assetFilter === chip.id
                          ? "border-[var(--ar-accent-soft)] bg-[var(--ar-accent-dim)] text-[var(--ar-accent-2)]"
                          : "border-[var(--ar-hairline)] text-[var(--ar-text-3)] hover:text-[var(--ar-text)]"
                      }`}
                    >
                      {chip.label}
                      <span className="num ml-1 opacity-70">
                        {chip.id
                          ? assets.filter((a) => a.type === chip.id).length
                          : assets.length}
                      </span>
                    </button>
                  ))}
                </div>
                <DramaForgeSecondaryButton
                  disabled={busy || !apiKey || assets.length === 0 || optimizingKind === "assets-bulk"}
                  className="text-xs"
                  onClick={() =>
                    void runAction(async () => {
                      setOptimizingKind("assets-bulk");
                      try {
                        await optimizeDramaForgeAssetDesignPrompts(projectId, apiKey);
                      } finally {
                        setOptimizingKind(null);
                      }
                    })
                  }
                >
                  {optimizingKind === "assets-bulk" ? t("dramaforge.workspace.bulkOptimizing") : t("dramaforge.workspace.bulkAiOptimizeDesignPrompts")}
                </DramaForgeSecondaryButton>
              </div>
              <p className="mb-3 text-xs text-[var(--ar-text-4)]">{assetListTitle}</p>
              <div className="space-y-3">
                {filteredAssets.map((asset) => (
                  <div key={asset.id} className="rounded-xl border border-[var(--ar-hairline)] bg-white/55 p-3">
                    {editingAssetId === asset.id ? (
                      <AssetEditForm
                        asset={asset}
                        disabled={busy}
                        optimizing={optimizingKind === `asset-${asset.id}`}
                        onCancel={() => setEditingAssetId(null)}
                        onAiOptimizeDesign={async (draft) => {
                          if (!apiKey) throw new Error(t("dramaforge.workspace.configureApiKey"));
                          const res = await optimizeDramaForgePrompt(
                            projectId,
                            { kind: "asset_design", assetId: asset.id, draft },
                            apiKey,
                          );
                          return res.data.optimizedText;
                        }}
                        onSave={async (input) => {
                          await runAction(async () => {
                            await updateDramaForgeAsset(projectId, asset.id, input);
                            setEditingAssetId(null);
                          });
                        }}
                      />
                    ) : (
                      <>
                        <div className="flex items-start justify-between gap-3">
                          <div>
                            <div className="font-medium text-[var(--ar-text)]">
                              {asset.name}
                              <span className="ml-2 text-xs text-[var(--ar-text-4)]">
                                {t(`dramaforge.assetType.${asset.type}`)}
                              </span>
                            </div>
                            {asset.description && (
                              <p className="mt-1 text-sm text-[var(--ar-text-3)]">{asset.description}</p>
                            )}
                            {asset.designPrompt && (
                              <p className="mt-1 text-xs text-[var(--ar-text-4)] line-clamp-2">
                                {t("dramaforge.workspace.designPromptPrefix", { prompt: asset.designPrompt })}
                              </p>
                            )}
                          </div>
                          <div className="flex flex-wrap gap-2">
                            <button
                              type="button"
                              className="text-xs text-[var(--ar-accent-2)]"
                              onClick={() => {
                                if (expandedAssetId === asset.id) {
                                  setExpandedAssetId(null);
                                } else {
                                  void loadAssetVersions(asset.id);
                                }
                              }}
                            >
                              {t("dramaforge.workspace.versions")}
                            </button>
                            <DramaForgeSecondaryButton
                              disabled={busy || !apiKey}
                              className="px-2 py-1 text-xs"
                              onClick={() => void handleGenerateAssetCandidates(asset.id)}
                            >
                              {t("dramaforge.workspace.generateThreeCandidates")}
                            </DramaForgeSecondaryButton>
                            <DramaForgeSecondaryButton
                              disabled={busy || !apiKey}
                              className="px-2 py-1 text-xs"
                              onClick={() =>
                                void runAction(async () => {
                                  await regenerateDramaForgeAssetDesign(projectId, asset.id, apiKey);
                                })
                              }
                            >
                              {t("dramaforge.workspace.regenerate")}
                            </DramaForgeSecondaryButton>
                            <DramaForgeSecondaryButton
                              disabled={busy || !apiKey}
                              className="px-2 py-1 text-xs"
                              title={t("dramaforge.workspace.privacySafeRegenerateHint")}
                              onClick={() =>
                                void runAction(async () => {
                                  await regenerateDramaForgeAssetDesign(projectId, asset.id, apiKey, {
                                    privacySafe: true,
                                  });
                                })
                              }
                            >
                              {t("dramaforge.workspace.privacySafeRegenerate")}
                            </DramaForgeSecondaryButton>
                            <button
                              type="button"
                              disabled={busy}
                              onClick={() => setEditingAssetId(asset.id)}
                              className="text-xs text-[var(--ar-accent-2)]"
                            >
                              {t("dramaforge.workspace.edit")}
                            </button>
                            <button
                              type="button"
                              disabled={busy}
                              onClick={() => void runAction(async () => { await deleteDramaForgeAsset(projectId, asset.id); })}
                              className="text-xs text-[var(--ar-danger)]"
                            >
                              {t("dramaforge.workspace.delete")}
                            </button>
                          </div>
                        </div>
                        {asset.referenceImageUrl && (
                          <Image
                            src={resolveMediaUrl(asset.referenceImageUrl)}
                            alt={asset.name}
                            width={640}
                            height={400}
                            className="mt-3 max-h-40 cursor-pointer rounded-lg object-cover hover:opacity-90"
                            onClick={() => setFramePreview({ url: resolveMediaUrl(asset.referenceImageUrl), label: asset.name })}
                            unoptimized
                          />
                        )}
                        {asset.type === "character" && (
                          <CharacterVoiceBar
                            asset={asset}
                            disabled={busy}
                            apiKey={apiKey}
                            generating={optimizingKind === `voice-${asset.id}`}
                            onError={(message) => setError(message)}
                            onGenerate={async () => {
                              if (!apiKey) {
      setError(t("dramaforge.workspace.configureTokenfreeApiKey"));
                                return;
                              }
                              setOptimizingKind(`voice-${asset.id}`);
                              setError(null);
                              try {
                                const res = await generateDramaForgeCharacterVoice(
                                  projectId,
                                  asset.id,
                                  apiKey,
                                );
                                setAssets((prev) =>
                                  prev.map((a) => (a.id === res.data.id ? res.data : a)),
                                );
                              } catch (e) {
                                setError(e instanceof Error ? e.message : t("dramaforge.workspace.voiceGenerationFailed"));
                              } finally {
                                setOptimizingKind(null);
                              }
                            }}
                            onSaveVoice={async (input) => {
                              await runAction(async () => {
                                const res = await updateDramaForgeAsset(projectId, asset.id, input);
                                setAssets((prev) =>
                                  prev.map((a) => (a.id === res.data.id ? res.data : a)),
                                );
                              });
                            }}
                          />
                        )}
                        {expandedAssetId === asset.id && assetVersions.length > 0 && (
                          <div className="mt-3 rounded-lg border border-[var(--ar-hairline)] bg-white/60 p-3">
                            <div className="mb-2 text-xs font-medium text-[var(--ar-text-4)]">{t("dramaforge.workspace.versionHistory")}</div>
                            <div className="space-y-2">
                              {assetVersions.map((version) => (
                                <div key={version.id} className="flex items-center justify-between gap-2 text-xs text-[var(--ar-text-2)]">
                                  <div className="flex min-w-0 items-center gap-2">
                                    {version.referenceImageUrl && (
                                      <Image
                                        src={resolveMediaUrl(version.referenceImageUrl)}
                                        alt={`v${version.versionNo}`}
                                        width={40}
                                        height={40}
                                        className="h-10 w-10 shrink-0 cursor-pointer rounded object-cover hover:opacity-80"
                                        onClick={() => setFramePreview({ url: resolveMediaUrl(version.referenceImageUrl), label: `v${version.versionNo}` })}
                                        unoptimized
                                      />
                                    )}
                                    <span>
                                      v{version.versionNo}
                                      {version.active ? t("dramaforge.workspace.currentSuffix") : ""}
                                    </span>
                                  </div>
                                  <button
                                    type="button"
                                    disabled={busy || version.active}
                                    onClick={() => void handleActivateAssetVersion(asset.id, version.id)}
                                    className="shrink-0 text-[var(--ar-accent-2)] disabled:opacity-40"
                                  >
                                    {t("dramaforge.workspace.rollback")}
                                  </button>
                                </div>
                              ))}
                            </div>
                          </div>
                        )}
                      </>
                    )}
                  </div>
                ))}
                {filteredAssets.length === 0 && (
                  <p className="text-sm text-[var(--ar-text-3)]">
                    {assetFilter
                      ? t("dramaforge.workspace.noAssetsOfType", { type: t(`dramaforge.assetType.${assetFilter}`) })
                      : t("dramaforge.workspace.noAssetsAtAll")}
                  </p>
                )}
              </div>
            </DramaForgePanel>
          </div>
        ) : contentView === "script" ? (
          <div className="mx-auto max-w-4xl space-y-4">
            <DramaForgePanel title={t("dramaforge.workspace.episodeScriptTitle", { n: episodes.find((e) => e.id === selectedEpisodeId)?.episodeNumber ?? "?" })}>
              <p className="mb-2 text-xs text-[var(--ar-text-4)]">
                {t("dramaforge.workspace.scriptStepHint")}
              </p>
              <input
                value={episodeTitle}
                onChange={(e) => setEpisodeTitle(e.target.value)}
                placeholder={t("dramaforge.workspace.episodeTitlePlaceholder")}
                className="dramaforge-input mb-2 w-full rounded-xl px-3 py-2 text-sm"
              />
              <DramaForgeScriptEditor
                scriptJson={episodeScript}
                disabled={busy}
                onChange={(json) => {
                  episodeScriptDirtyRef.current = true;
                  setEpisodeScript(json);
                }}
              />
              <div className="mt-3 flex flex-wrap gap-2">
                <DramaForgePrimaryButton
                  disabled={busy || !selectedEpisodeId || !episodeScript.trim() || !apiKey}
                  onClick={() => void handleStructureEpisodeScript()}
                >
                  {t("dramaforge.workspace.textToScript")}
                </DramaForgePrimaryButton>
                <DramaForgePrimaryButton
                  disabled={busy || !selectedEpisodeId || !apiKey}
                  onClick={() => void handleStructureEpisodeShots()}
                >
                  {t("dramaforge.workspace.scriptToShots")}
                </DramaForgePrimaryButton>
                <DramaForgeSecondaryButton
                  disabled={busy || !selectedEpisodeId || !episodeScript.trim()}
                  onClick={() => void handleParseShotsFromEpisode()}
                >
                  {t("dramaforge.workspace.writeToShotLibrary")}
                </DramaForgeSecondaryButton>
                <DramaForgeSecondaryButton disabled={busy || !selectedEpisodeId} onClick={() => void handleSaveEpisodeScript()}>
                  {t("dramaforge.workspace.saveEpisode")}
                </DramaForgeSecondaryButton>
                <DramaForgeSecondaryButton disabled={busy} onClick={() => void handleCreateEpisode()}>
                  {t("dramaforge.workspace.newEpisode")}
                </DramaForgeSecondaryButton>
                <DramaForgeSecondaryButton
                  disabled={busy || !selectedEpisodeId}
                  onClick={() => selectedEpisodeId && void handleDeleteEpisode(selectedEpisodeId)}
                >
                  {t("dramaforge.workspace.deleteCurrentEpisode")}
                </DramaForgeSecondaryButton>
                <DramaForgeSecondaryButton disabled={busy || !apiKey} onClick={() => void handlePlanEpisodes()}>
                  {t("dramaforge.workspace.multiEpisodePlan")}
                </DramaForgeSecondaryButton>
                <DramaForgePrimaryButton
                  disabled={busy || !selectedEpisodeId}
                  onClick={() => void handleLockScript()}
                >
                  {t("dramaforge.workspace.confirmScript")}
                </DramaForgePrimaryButton>
              </div>
            </DramaForgePanel>

            <DramaForgePanel title={t("dramaforge.workspace.episodeShotsPreview")}>
              <p className="mb-3 text-xs text-[var(--ar-text-4)]">
                {t("dramaforge.workspace.episodeShotsPreviewHint")}
              </p>
              {!selectedEpisodeId ? (
                <p className="text-sm text-[var(--ar-text-3)]">{t("dramaforge.workspace.selectOrCreateEpisode")}</p>
              ) : shotsLoading ? (
                <p className="text-sm text-[var(--ar-text-3)]">{t("dramaforge.workspace.loading")}</p>
              ) : shots.length === 0 ? (
                <p className="text-sm text-[var(--ar-text-3)]">{t("dramaforge.workspace.noShotsWriteFirst")}</p>
              ) : (
                <div className="space-y-2">
                  <DramaForgeBadge tone="accent">{t("dramaforge.workspace.shotCount", { n: shots.length })}</DramaForgeBadge>
                  <div className="max-h-[360px] space-y-2 overflow-auto df-scroll-area">
                    {shots.map((shot) => (
                      <div
                        key={shot.id}
                        className="rounded-xl border border-[var(--ar-hairline)] px-3 py-2 text-xs text-[var(--ar-text-2)]"
                      >
                        <div className="mb-1 font-medium text-[var(--ar-text)]">{t("dramaforge.workspace.shotNumber", { n: shot.shotNumber })}</div>
                        <p className="line-clamp-2">{shot.description}</p>
                        {shot.dialogue?.trim() && (
                          <p className="mt-1 text-[var(--ar-text-4)]">{t("dramaforge.workspace.dialoguePrefix", { text: shot.dialogue })}</p>
                        )}
                      </div>
                    ))}
                  </div>
                </div>
              )}
            </DramaForgePanel>

            {episodes.length > 0 && (
              <DramaForgePanel title={t("dramaforge.workspace.episodeList")}>
                <div className="space-y-2">
                  {episodes.map((episode) => (
                    <button
                      key={episode.id}
                      type="button"
                      onClick={() => {
                        episodeScriptDirtyRef.current = false;
                        setSelectedEpisodeId(episode.id);
                        setEpisodeTitle(episode.title);
                        setEpisodeScript(episode.scriptJson ?? "");
                      }}
                      className={`block w-full rounded-xl border px-3 py-2 text-left text-sm transition ${
                        selectedEpisodeId === episode.id
                          ? "border-[var(--ar-accent-soft)] bg-[var(--ar-accent-dim)] text-[var(--ar-accent-2)]"
                          : "border-[var(--ar-hairline)] text-[var(--ar-text-2)] hover:bg-white/65"
                      }`}
                    >
                      {t("dramaforge.workspace.episodeTitleWithName", { n: episode.episodeNumber, title: episode.title })}
                      <span className="ml-2 text-[var(--ar-text-4)]">{t("dramaforge.workspace.shotCount", { n: episode.shotCount })}</span>
                      {episode.scriptLockedAt && (
                        <span className="ml-2 text-[10px] text-[var(--ar-good)]">{t("dramaforge.workspace.confirmed")}</span>
                      )}
                    </button>
                  ))}
                </div>
              </DramaForgePanel>
            )}
          </div>
        ) : (
          <div className="space-y-4">
            <DramaForgePanel title={t("dramaforge.workspace.outputEpisode")}>
              <p className="mb-3 text-xs text-[var(--ar-text-4)]">
                {t("dramaforge.workspace.outputEpisodeHint")}
              </p>
              <div className="mb-4 flex flex-wrap gap-2">
                {episodes.length === 0 ? (
                  <p className="text-sm text-[var(--ar-text-3)]">{t("dramaforge.workspace.noEpisodesYet")}</p>
                ) : (
                  episodes.map((episode) => (
                    <button
                      key={episode.id}
                      type="button"
                      onClick={() => {
                        setSelectedEpisodeId(episode.id);
                        setEpisodeTitle(episode.title);
                        if (!episodeScriptDirtyRef.current) {
                          setEpisodeScript(episode.scriptJson ?? "");
                        }
                      }}
                      className={`rounded-full border px-3 py-1.5 text-xs transition ${
                        selectedEpisodeId === episode.id
                          ? "border-[var(--ar-accent-soft)] bg-[var(--ar-accent-dim)] text-[var(--ar-accent-2)]"
                          : "border-[var(--ar-hairline)] text-[var(--ar-text-3)] hover:text-[var(--ar-text)]"
                      }`}
                    >
                      E{episode.episodeNumber} · {episode.title}
                      <span className="ml-1 opacity-70">({episode.shotCount})</span>
                    </button>
                  ))
                )}
              </div>
            </DramaForgePanel>

            <DramaForgePanel>
              <div className="mb-4 space-y-3">
                <div>
                  <h3 className="text-lg font-semibold tracking-tight text-[var(--ar-text)]">{t("dramaforge.workspace.shotVideos")}</h3>
                  <p className="mt-1 text-xs text-[var(--ar-text-4)]">
                    {t("dramaforge.workspace.shotVideosHint")}
                  </p>
                </div>
                <div className="flex flex-wrap items-center gap-1 border-b border-[var(--ar-hairline)]">
                  {(
                    [
                      { id: "list" as const, label: t("dramaforge.workspace.shotList") },
                      { id: "batch" as const, label: t("dramaforge.workspace.batchGenerate") },
                      { id: "history" as const, label: t("dramaforge.workspace.generationHistory") },
                      { id: "versions" as const, label: t("dramaforge.workspace.versionManagement") },
                    ] as const
                  ).map((item) => (
                    <button
                      key={item.id}
                      type="button"
                      onClick={() => setShotListSubTab(item.id)}
                      className={`-mb-px border-b-2 px-3 py-2 text-xs font-medium transition ${
                        shotListSubTab === item.id
                          ? "border-[var(--ar-accent)] text-[var(--ar-accent-2)]"
                          : "border-transparent text-[var(--ar-text-4)] hover:text-[var(--ar-text-2)]"
                      }`}
                    >
                      {item.label}
                    </button>
                  ))}
                </div>
                <div className="flex flex-wrap items-center gap-2">
                  <input
                    value={shotSearch}
                    onChange={(e) => setShotSearch(e.target.value)}
                    placeholder={t("dramaforge.workspace.searchShotsPlaceholder")}
                    className="dramaforge-input min-w-[200px] flex-1 rounded-lg px-3 py-1.5 text-xs"
                  />
                  <DfSelect
                    size="sm"
                    className="min-w-[140px]"
                    value={shotSceneFilter}
                    onChange={setShotSceneFilter}
                    searchable={episodeSceneRefs.length > 8}
                    options={[
                      { value: "", label: t("dramaforge.workspace.allScenes") },
                      ...episodeSceneRefs.map((scene) => ({ value: scene, label: scene })),
                    ]}
                  />
                  <DfSelect
                    size="sm"
                    className="min-w-[120px]"
                    value={shotStatusFilter}
                    onChange={(v) => setShotStatusFilter(v as "all" | "done" | "run" | "wait")}
                    searchable={false}
                    options={[
                      { value: "all", label: t("dramaforge.workspace.allStatuses") },
                      { value: "done", label: t("dramaforge.workspace.statusDone") },
                      { value: "run", label: t("dramaforge.workspace.statusGenerating") },
                      { value: "wait", label: t("dramaforge.workspace.statusPending") },
                    ]}
                  />
                  <label className="flex items-center gap-1.5 text-[11px] text-[var(--ar-text-3)]">
                    <input
                      type="checkbox"
                      checked={pendingShotsOnly}
                      onChange={(e) => setPendingShotsOnly(e.target.checked)}
                      className="accent-[var(--ar-accent)]"
                    />
                    {t("dramaforge.workspace.pendingShotsOnlyLabel")}
                  </label>
                  <DramaForgeSecondaryButton
                    className="ml-auto px-2.5 py-1.5 text-xs"
                    disabled={busy || !apiKey || !selectedEpisodeId}
                    onClick={() => {
                      if (!selectedEpisodeId || !apiKey) return;
                      void runAction(async () => {
                        await generateDramaForgeVideos(projectId, selectedEpisodeId, apiKey);
                        await syncDramaForgeVideos(projectId, selectedEpisodeId, apiKey);
                      });
                    }}
                  >
                    {t("dramaforge.workspace.batchActions")}
                  </DramaForgeSecondaryButton>
                </div>
              </div>
              {(displayJobProgress?.type === "video" || displayJobProgress?.type === "shot_video")
                && (!displayJobProgress.episodeId || displayJobProgress.episodeId === selectedEpisodeId) && (
                <div className="mb-3">
                  <DramaForgeJobProgressBar
                    label={t("dramaforge.workspace.videoGeneratingStoryboard")}
                    current={displayJobProgress.current}
                    total={displayJobProgress.total}
                    message={displayJobProgress.message}
                  />
                </div>
              )}
              <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
                <DramaForgeBadge tone="accent">
                  {t("dramaforge.workspace.videoDoneCount", { done: videoDoneInEpisodeCount, total: shots.length })}
                  {filteredShots.length !== shots.length
                    ? t("dramaforge.workspace.showingCount", { n: filteredShots.length })
                    : ""}
                </DramaForgeBadge>
                <div className="flex flex-wrap gap-2">
                  <DramaForgeSecondaryButton
                        disabled={
                          busy
                          || !selectedEpisodeId
                          || !apiKey
                          || !shots.some((s) => s.dialogue?.trim())
                        }
                        className="px-3 py-1.5 text-xs"
                        onClick={() => void handleBatchDialogueTts()}
                      >
                        {t("dramaforge.workspace.batchDialogueTts")}
                      </DramaForgeSecondaryButton>
                      <DramaForgePrimaryButton
                        disabled={busy || !selectedEpisodeId || !apiKey}
                        className="px-3 py-1.5 text-xs"
                        onClick={() => selectedEpisodeId && void runAction(async () => {
                          await generateDramaForgeVideos(projectId, selectedEpisodeId, apiKey);
                          await syncDramaForgeVideos(projectId, selectedEpisodeId, apiKey);
                        })}
                      >
                        {t("dramaforge.workspace.generateEpisodeVideos")}
                      </DramaForgePrimaryButton>
                      <DramaForgeSecondaryButton
                        disabled={busy || !selectedEpisodeId}
                        className="border-[var(--ar-good)]/30 px-3 py-1.5 text-xs text-[var(--ar-good)]"
                        onClick={() => setWizardStep("composed")}
                      >
                        {t("dramaforge.workspace.goToAiEdit")}
                      </DramaForgeSecondaryButton>
                </div>
              </div>

              {composeReadiness && selectedEpisodeId && (
                <div
                  className={`mb-3 rounded-lg border px-3 py-2 text-xs ${
                    composeReadiness.blockers.length > 0
                      ? "border-[var(--ar-danger)]/40 bg-[var(--ar-danger)]/5"
                      : composeReadiness.warnings.length > 0
                        ? "border-amber-500/30 bg-amber-500/5"
                        : "border-[var(--ar-good)]/30 bg-[var(--ar-good)]/5"
                  }`}
                >
                  <div className="mb-1 flex flex-wrap items-center gap-x-3 gap-y-1 font-medium text-[var(--ar-text-2)]">
                    <span>{t("dramaforge.workspace.composeReadiness")}</span>
                    <span className="font-normal text-[var(--ar-text-3)]">
                      {t("dramaforge.workspace.readinessVideoCount", { done: composeReadiness.videoDoneShots, total: composeReadiness.totalShots })}
                    </span>
                    {composeReadiness.shotsWithDialogue > 0 && (
                      <span className="font-normal text-[var(--ar-text-3)]">
                        {t("dramaforge.workspace.readinessDialogueCount", { n: composeReadiness.shotsWithDialogue })}
                        {composeReadiness.missingDialogueAudio > 0
                          && t("dramaforge.workspace.missingAudioCount", { n: composeReadiness.missingDialogueAudio })}
                      </span>
                    )}
                    {composeReadiness.lipSyncEnabled && (
                      <span className="font-normal text-[var(--ar-text-4)]">
                        LipSync {composeReadiness.lipSyncEndpointConfigured ? t("dramaforge.workspace.lipSyncReady") : t("dramaforge.workspace.lipSyncNotConfigured")}
                      </span>
                    )}
                  </div>
                  {composeReadiness.blockers.map((item) => (
                    <p key={item} className="text-[var(--ar-danger)]">{item}</p>
                  ))}
                  {composeReadiness.warnings.map((item) => (
                    <p key={item} className="text-amber-400">{item}</p>
                  ))}
                  {(composeReadiness.missingDialogueAudio ?? 0) > 0 && apiKey && (
                    <div className="mt-2">
                      <DramaForgeSecondaryButton
                        disabled={busy || !selectedEpisodeId}
                        className="px-3 py-1.5 text-xs"
                        onClick={() => void handleBatchDialogueTts()}
                      >
                        {t("dramaforge.workspace.batchDialogueTtsMissing", { n: composeReadiness.missingDialogueAudio })}
                      </DramaForgeSecondaryButton>
                    </div>
                  )}
                  {composeReadiness.blockers.length === 0 && composeReadiness.warnings.length === 0 && (
                    <p className="text-[var(--ar-good)]">{t("dramaforge.workspace.composeReady")}</p>
                  )}
                </div>
              )}

              <div className="mb-4 flex gap-2">
                <input
                  value={shotDescription}
                  onChange={(e) => setShotDescription(e.target.value)}
                  placeholder={t("dramaforge.workspace.manualShotDescriptionPlaceholder")}
                  className="dramaforge-input flex-1 rounded-xl px-3 py-2 text-sm"
                />
                <DramaForgeSecondaryButton disabled={busy || !selectedEpisodeId} onClick={() => void handleCreateShot()}>
                  {t("dramaforge.workspace.add")}
                </DramaForgeSecondaryButton>
              </div>

              <div className="space-y-3">
                {filteredShots.map((shot) => {
                  const listMode = "video";
                  const visual = shotVisualStatus(shot, listMode, jobs);
                  const activeShotJob = findActiveShotJob(shot, jobs, listMode);
                  const failureReason =
                    visual.key === "fail" ? resolveShotFailureReason(shot, jobs, listMode) : null;
                  const progressPct =
                    visual.key === "run" &&
                    displayJobProgress &&
                    displayJobProgress.total > 0
                      ? Math.min(
                          99,
                          Math.round(
                            (displayJobProgress.current / displayJobProgress.total) * 100,
                          ),
                        )
                      : visual.key === "run"
                        ? 45
                        : 0;
                  const statusClass =
                    visual.key === "done"
                      ? "df-shot-status-done"
                      : visual.key === "run"
                        ? "df-shot-status-run"
                        : visual.key === "fail"
                          ? "df-shot-status-fail"
                          : "df-shot-status-wait";
                  return (
                  <div key={shot.id} className="df-shot-card">
                    <div className="df-shot-thumb">
                      {shot.videoUrl ? (
                        <video src={shot.videoUrl} muted className="h-full w-full object-cover" />
                      ) : (
                        <div className="flex h-full min-h-[112px] items-center justify-center bg-black text-xs text-white/30">
                          {t("dramaforge.workspace.noVideo")}
                        </div>
                      )}
                      <span className={`df-shot-status df-shot-thumb-badge ${statusClass}`}>
                        {t(`dramaforge.shotStatus.${visual.thumbKey}`)}
                      </span>
                      <span className="df-shot-thumb-duration">
                        {formatShotDuration(shot.durationSeconds)}
                      </span>
                    </div>

                    <div className="flex min-w-0 flex-col gap-2 py-3 pr-2">
                      <div className="flex flex-wrap items-start justify-between gap-2">
                        <div>
                          <div className="text-sm font-semibold text-[var(--ar-text)]">
                            {t("dramaforge.workspace.shotNumber", { n: shot.shotNumber })}
                          </div>
                          {shot.qaStatus && shot.qaStatus !== "pending" && (
                            <span
                              className={`text-[10px] ${
                                shot.qaStatus === "pass"
                                  ? "text-[var(--ar-good)]"
                                  : "text-[var(--ar-danger)]"
                              }`}
                            >
                              QA:{shot.qaStatus}
                            </span>
                          )}
                        </div>
                        <span className={`df-shot-status ${statusClass}`}>
                          {visual.key === "run"
                            ? <GeneratingText label={t(`dramaforge.shotStatus.${visual.statusKey}`)} />
                            : t(`dramaforge.shotStatus.${visual.statusKey}`)}
                        </span>
                      </div>

                      {visual.key === "fail" && (
                        <details className="df-shot-fail-reason">
                          <summary className="df-shot-fail-reason__summary cursor-pointer select-none">{t("dramaforge.workspace.failureReason")}</summary>
                          <div className="df-shot-fail-reason__body">
                            {failureReason || t("dramaforge.workspace.failureNoDetail")}
                          </div>
                        </details>
                      )}

                      <DfExpandableText
                        text={
                            shot.videoPrompt || shot.description || ""
                          }
                        maxLines={2}
                      />

                      <div className="flex flex-wrap gap-1.5 text-[10px]">
                        {(shot.characterRefs?.length ?? 0) > 0 && (
                          <span className="rounded-md border border-[var(--ar-hairline)] px-1.5 py-0.5 text-[var(--ar-text-4)]">
                            {t("dramaforge.workspace.referenceCharacters", { n: shot.characterRefs.length })}
                          </span>
                        )}
                        {(shot.audioBindings?.length ?? 0) > 0 && (
                          <span className="rounded-md border border-[var(--ar-hairline)] px-1.5 py-0.5 text-[var(--ar-text-4)]">
                            {t("dramaforge.workspace.audioBindingsCount", { n: shot.audioBindings?.length ?? 0 })}
                          </span>
                        )}
                        {(shot.imageBindings?.length ?? 0) > 0 && (
                          <span className="rounded-md border border-[var(--ar-hairline)] px-1.5 py-0.5 text-[var(--ar-text-4)]">
                            {t("dramaforge.workspace.imageBindingsCount", { n: shot.imageBindings?.length ?? 0 })}
                          </span>
                        )}
                        <span className="rounded-md border border-[var(--ar-hairline)] px-1.5 py-0.5 text-[var(--ar-text-4)]">
                          {shot.durationSeconds ?? 5}s
                        </span>
                      </div>

                      <div className="df-shot-params">
                        <span>
                          {t("dramaforge.workspace.modelParam")} <strong>{config?.videoBackend ?? "Seedance 2.5"}</strong>
                        </span>
                        <span>
                          {t("dramaforge.workspace.aspectRatioParam")} <strong>{config?.aspectRatio ?? "16:9"} / {config?.videoQuality ?? "480p"}</strong>
                        </span>
                        <span>
                          {t("dramaforge.workspace.frameRateParam")} <strong>24</strong>
                        </span>
                      </div>

                      {visual.key === "run" && (
                        <div className="df-shot-stepper">
                          {[
                            { label: t("dramaforge.workspace.stepTaskReceived"), state: "done" as const },
                            {
                              label: t("dramaforge.workspace.stepAssetAnalysis"),
                              state: progressPct >= 20 ? ("done" as const) : ("idle" as const),
                            },
                            { label: t("dramaforge.workspace.stepShotGeneration"), state: "active" as const },
                            { label: t("dramaforge.workspace.stepImageOptimization"), state: "idle" as const },
                            { label: t("dramaforge.workspace.stepOutputPackaging"), state: "idle" as const },
                          ].map((step) => (
                            <span
                              key={step.label}
                              className={`df-shot-step ${
                                step.state === "active" ? "active" : step.state === "done" ? "done" : ""
                              }`}
                            >
                              {step.label}
                            </span>
                          ))}
                        </div>
                      )}

                      <details className="mt-1">
                        <summary className="cursor-pointer text-[11px] text-[var(--ar-text-4)] hover:text-[var(--ar-accent-2)]">
                          {t("dramaforge.workspace.moreActions")}
                        </summary>
                        <div className="mt-2 flex flex-wrap gap-2 border-t border-[var(--ar-hairline)] pt-2">
                        <button
                          type="button"
                          className="text-xs text-[var(--ar-accent-2)]"
                          disabled={busy || !selectedEpisodeId}
                          onClick={() =>
                            selectedEpisodeId &&
                            void runAction(async () => {
                              const res = await promoteDramaForgeShotAssets(
                                projectId,
                                selectedEpisodeId,
                                shot.id,
                              );
                              setAssets(res.data);
                            })
                          }
                        >
                          {t("dramaforge.workspace.extractAssetsGlobal")}
                        </button>
                        <button
                          type="button"
                          className="text-xs text-[var(--ar-accent-2)]"
                          disabled={busy || !selectedEpisodeId || shot.shotNumber <= 1}
                          onClick={() =>
                            selectedEpisodeId &&
                            void runAction(async () => {
                              const res = await promotePreviousDramaForgeShotAssets(
                                projectId,
                                selectedEpisodeId,
                                shot.id,
                              );
                              setAssets(res.data);
                              const shotsRes = await fetchDramaForgeShots(
                                projectId,
                                selectedEpisodeId,
                              );
                              setShots(shotsRes.data);
                            })
                          }
                        >
                          {t("dramaforge.workspace.extractFromPrevious")}
                        </button>
                        {shot.dialogue && (
                          <button
                            type="button"
                            className="text-xs text-[var(--ar-accent-2)]"
                            disabled={busy || !apiKey || !selectedEpisodeId}
                            onClick={() =>
                              selectedEpisodeId &&
                              void runAction(async () => {
                                await generateDramaForgeShotDialogueAudio(
                                  projectId,
                                  selectedEpisodeId,
                                  shot.id,
                                  apiKey,
                                );
                                await refreshShots(selectedEpisodeId);
                              })
                            }
                          >
                            {t("dramaforge.workspace.dialogueTts")}
                          </button>
                        )}
                        <button
                          type="button"
                          className="text-xs text-[#7c3aed]"
                          disabled={busy || !selectedEpisodeId}
                          onClick={() =>
                            selectedEpisodeId &&
                            void runAction(async () => {
                              await updateDramaForgeShot(projectId, selectedEpisodeId, shot.id, {
                                qaStatus: "pass",
                              });
                              await refreshShots(selectedEpisodeId);
                            })
                          }
                        >
                          {t("dramaforge.workspace.qaPass")}
                        </button>
                        <button
                          type="button"
                          className="text-xs text-[var(--ar-danger)]"
                          disabled={busy || !selectedEpisodeId}
                          onClick={() =>
                            selectedEpisodeId &&
                            void runAction(async () => {
                              await updateDramaForgeShot(projectId, selectedEpisodeId, shot.id, {
                                qaStatus: "fail",
                              });
                              await refreshShots(selectedEpisodeId);
                            })
                          }
                        >
                          {t("dramaforge.workspace.qaFail")}
                        </button>
                        <button
                          type="button"
                          className="text-xs text-[var(--ar-accent-2)]"
                          onClick={() => void loadShotVersions(shot.id)}
                        >
                          {t("dramaforge.workspace.versions")}
                        </button>
                        </div>
                    <details className="mt-2">
                      <summary className="cursor-pointer text-xs text-[var(--ar-text-4)]">
                        {t("dramaforge.workspace.assetDetails")}
                      </summary>
                      <ShotBoundAssets
                        shot={shot}
                        assets={assets}
                        disabled={busy}
                        apiKey={apiKey}
                        optimizingKind={optimizingKind}
                        onPreviewImage={(url, label) => setFramePreview({ url, label })}
                        onError={(message: string) => setError(message)}
                        onGenerateVoice={async (assetId: string) => {
                          if (!apiKey) {
                            setError(t("dramaforge.workspace.configureTokenfreeApiKey"));
                            return;
                          }
                          setOptimizingKind(`voice-${assetId}`);
                          setError(null);
                          try {
                            const res = await generateDramaForgeCharacterVoice(
                              projectId,
                              assetId,
                              apiKey,
                            );
                            setAssets((prev) =>
                              prev.map((a) => (a.id === res.data.id ? res.data : a)),
                            );
                             } catch (e) {
                            setError(e instanceof Error ? e.message : t("dramaforge.workspace.voiceGenerationFailed"));
                          } finally {
                            setOptimizingKind(null);
                          }
                        }}
                        onSaveVoice={async (
                          assetId: string,
                          input: { voiceLabel?: string; voiceSampleUrl?: string },
                        ) => {
                          await runAction(async () => {
                            const res = await updateDramaForgeAsset(projectId, assetId, input);
                            setAssets((prev) =>
                              prev.map((a) => (a.id === res.data.id ? res.data : a)),
                            );
                          });
                        }}
                      />

                    </details>
                    {editingShotId === shot.id ? (
                      <ShotEditForm
                        shot={shot}
                        assets={assets}
                        disabled={busy}
                        optimizing={optimizingKind === `shot-${shot.id}`}
                        onCancel={() => setEditingShotId(null)}
                        onAiOptimizeDescription={async (draft) => {
                          if (!apiKey || !selectedEpisodeId) throw new Error(t("dramaforge.workspace.configureApiKey"));
                          const res = await optimizeDramaForgePrompt(
                            projectId,
                            {
                              kind: "shot",
                              episodeId: selectedEpisodeId,
                              shotId: shot.id,
                              draft,
                            },
                            apiKey,
                          );
                          return res.data.optimizedText;
                        }}
                        onSave={async (input) => {
                          if (!selectedEpisodeId) return;
                          await runAction(async () => {
                            await updateDramaForgeShot(projectId, selectedEpisodeId, shot.id, input);
                            setEditingShotId(null);
                          });
                        }}
                      />
                    ) : (
                      <>
                        {shot.dialogue && (
                          <div className="mt-1 space-y-1">
                            <p className="text-sm text-[var(--ar-text-3)]">{t("dramaforge.workspace.dialoguePrefix", { text: shot.dialogue })}</p>
                            {shot.dialogueAudioUrl ? (
                              <audio
                                controls
                                preload="metadata"
                                src={resolveMediaUrl(shot.dialogueAudioUrl)}
                                className="h-8 w-full max-w-md"
                              >
                                {t("dramaforge.workspace.audioUnsupported")}
                              </audio>
                            ) : (
                              <p className="text-[10px] text-[var(--ar-text-4)]">
                                {t("dramaforge.workspace.dialogueTtsNotGenerated")}
                              </p>
                            )}
                          </div>
                        )}
                        <button
                          type="button"
                          className="mt-2 text-xs text-[var(--ar-accent-2)]"
                          onClick={() => setEditingShotId(shot.id)}
                        >
                          {t("dramaforge.workspace.editShot")}
                        </button>
                      </>
                    )}
                    {expandedShotId === shot.id && shotVersions.length > 0 && (
                      <div className="mt-3 rounded-lg border border-[var(--ar-hairline)] bg-white/60 p-3">
                        <div className="mb-2 text-xs font-medium text-[var(--ar-text-4)]">{t("dramaforge.workspace.versionHistory")}</div>
                        <div className="space-y-2">
                          {shotVersions.map((version) => (
                            <div key={version.id} className="flex items-center justify-between gap-2 text-xs text-[var(--ar-text-2)]">
                              <div className="flex min-w-0 items-center gap-2">
                                {version.videoUrl ? (
                                  <video
                                    src={version.videoUrl}
                                    className="h-10 w-10 shrink-0 rounded object-cover"
                                    muted
                                  />
                                ) : null}
                                <span>
                                  v{version.versionNo}
                                  {version.active ? t("dramaforge.workspace.currentSuffix") : ""}
                                  {version.videoUrl ? t("dramaforge.workspace.hasVideoSuffix") : ""}
                                </span>
                              </div>
                              <button
                                type="button"
                                disabled={busy || version.active}
                                onClick={() => void handleActivateVersion(shot.id, version.id)}
                                className="shrink-0 text-[var(--ar-accent-2)] disabled:opacity-40"
                              >
                                {t("dramaforge.workspace.rollback")}
                              </button>
                            </div>
                          ))}
                        </div>
                      </div>
                    )}
                        </details>
                    </div>

                    <div className="df-shot-actions">
                      {visual.key === "done" && shot.videoUrl && (
                        <button
                          type="button"
                          className="dramaforge-btn-secondary rounded-lg px-2 py-2 text-xs"
                          onClick={() => {
                            setShotPreview({
                              shotId: shot.id,
                              shotNumber: shot.shotNumber,
                              videoUrl: shot.videoUrl!,
                              description: shot.videoPrompt || shot.description,
                            });
                            setRightPanel("preview");
                          }}
                        >
                          {t("dramaforge.workspace.viewResult")}
                        </button>
                      )}
                      {activeShotJob?.status === "running" ? (
                        <div
                          className="invisible pointer-events-none select-none rounded-lg px-2 py-2 text-xs"
                          aria-hidden
                        >
                          {t("dramaforge.workspace.cancelGeneration")}
                        </div>
                      ) : activeShotJob?.status === "queued" ? (
                        <button
                          type="button"
                          className="dramaforge-btn-secondary rounded-lg px-2 py-2 text-xs text-[var(--ar-danger)]"
                          disabled={busy}
                          onClick={() => {
                            void runAction(async () => {
                              await cancelDramaForgeJob(projectId, activeShotJob.id);
                            });
                          }}
                        >
                          {t("dramaforge.workspace.cancelGeneration")}
                        </button>
                      ) : (
                        <DramaForgePrimaryButton
                          disabled={
                            busy ||
                            Boolean(pendingShotIds[shot.id]) ||
                            !apiKey ||
                            !selectedEpisodeId ||
                            hasActiveShotJob(shot, jobs, "video") ||
                            (Boolean(shot.videoJobId) && !shot.videoUrl && shot.status !== "failed" && !jobs.some((j) => j.targetId === shot.id && j.status === "completed"))
                          }
                          className="px-2 py-2 text-xs"
                          title={t("dramaforge.workspace.generateVideoTitle")}
                          onClick={() => {
                            if (!selectedEpisodeId) return;
                            const episodeId = selectedEpisodeId;
                            const shotId = shot.id;
                            void (async () => {
                              setPendingShotIds((prev) => ({ ...prev, [shotId]: true }));
                              setError(null);
                              try {
                                const res = await regenerateDramaForgeShotVideo(
                                  projectId,
                                  episodeId,
                                  shotId,
                                  apiKey,
                                );
                                upsertJob(res.data);
                                await refreshShot(episodeId, shotId);
                                // 入队后本地短轮询当前镜，避免 SSE 丢事件；不整页 refreshAll
                                const jobId = res.data.id;
                                for (let i = 0; i < 90; i++) {
                                  await new Promise((r) => setTimeout(r, 2000));
                                  const jobsRes = await fetchDramaForgeJobs(projectId, 50).catch(
                                    () => null,
                                  );
                                  if (jobsRes) setJobs(jobsRes.data);
                                  await refreshShot(episodeId, shotId);
                                  const job = jobsRes?.data.find((j) => j.id === jobId);
                                  // cancelled / failed 直接退出
                                  if (!job || job.status === "cancelled") {
                                    break;
                                  }
                                  if (job.status === "failed") {
                                    const detail =
                                      job.errorMessage?.trim() ||
                                      t("dramaforge.workspace.videoGenerationFailedDetail");
                                    setError(detail);
                                    break;
                                  }
                                  // SHOT_VIDEO completed 仅表示提交完成，需等 SYNC_VIDEOS 拿到实际成片 videoUrl
                                  if (job.status === "completed") {
                                    try {
                                      const fresh = await fetchDramaForgeShot(projectId, episodeId, shotId);
                                      if (fresh.data?.videoUrl) {
                                        setShots((prev) => {
                                          const idx = prev.findIndex((s) => s.id === shotId);
                                          if (idx < 0) return prev;
                                          const next = prev.slice();
                                          next[idx] = fresh.data;
                                          return next;
                                        });
                                        break;
                                      }
                                    } catch {
                                      // endpoint 未注册时回退：继续轮询
                                    }
                                  }
                                }
                              } catch (e) {
                                await alert(getErrorMessage(e, t("dramaforge.workspace.operationFailed")), t("dramaforge.workspace.operationFailed"), {
                                  variant: "danger",
                                });
                              } finally {
                                setPendingShotIds((prev) => {
                                  const next = { ...prev };
                                  delete next[shotId];
                                  return next;
                                });
                              }
                            })();
                          }}
                        >
{hasActiveShotJob(shot, jobs, "video") || pendingShotIds[shot.id] || (Boolean(shot.videoJobId) && !shot.videoUrl && shot.status !== "failed" && !jobs.some((j) => j.targetId === shot.id && j.status === "completed"))
                              ? t("dramaforge.workspace.generating")
                            : visual.key === "done"
                              ? t("dramaforge.workspace.regenerate")
                              : t("dramaforge.workspace.generateVideo")}
                        </DramaForgePrimaryButton>
                      )}
                    </div>
                  </div>
                  );
                })}

                {shotsLoading ? (
                  <p className="text-sm text-[var(--ar-text-3)]">{t("dramaforge.workspace.loading")}</p>
                ) : filteredShots.length === 0 ? (
                  <p className="text-sm text-[var(--ar-text-3)]">
                    {shots.length === 0
                      ? t("dramaforge.workspace.noShotsGoToScript")
                      : t("dramaforge.workspace.noFilteredShots")}
                  </p>
                ) : null}
              </div>
            </DramaForgePanel>
          </div>
        )}
        </div>
      </div>

      {rightPanelNode}
      {ConfirmDialog}

      <Dialog
        open={framePreview != null}
        onOpenChange={(open) => {
          if (!open) setFramePreview(null);
        }}
      >
        <DialogContent
          className="max-h-[90vh] w-[min(96vw,960px)] max-w-[960px] overflow-hidden border-[var(--ar-hairline)] bg-white p-3 shadow-xl sm:max-w-[960px]"
          aria-describedby={undefined}
        >
          <DialogHeader className="pr-8">
            <DialogTitle className="text-sm font-medium text-slate-800">
              {framePreview?.label ?? t("dramaforge.workspace.imagePreview")}
            </DialogTitle>
            <DialogDescription className="sr-only">
              {t("dramaforge.workspace.imagePreviewDescription")}
            </DialogDescription>
          </DialogHeader>
          {framePreview && (
            <div className="flex max-h-[calc(90vh-4.5rem)] items-center justify-center overflow-auto rounded-lg bg-slate-950/90 p-2">
              {/* eslint-disable-next-line @next/next/no-img-element */}
              <img
                src={framePreview.url}
                alt={framePreview.label}
                className="max-h-[calc(90vh-5.5rem)] max-w-full object-contain"
              />
            </div>
          )}
        </DialogContent>
      </Dialog>
    </div>
  );
}

function scriptPreview(scriptJson?: string | null, t?: (key: string, params?: Record<string, string | number>) => string): string {
  if (!scriptJson?.trim()) return t ? t("dramaforge.workspace.scriptEmpty") : "（剧本为空，请重新生成或手动编辑）";
  try {
    const data = JSON.parse(scriptJson) as {
      title?: string;
      scenes?: { shots?: { description?: string }[] }[];
    };
    const firstShot = data.scenes?.[0]?.shots?.[0]?.description;
    if (firstShot) return firstShot;
    if (data.title) return data.title;
  } catch {
    // fall through
  }
  return scriptJson.slice(0, 120) + (scriptJson.length > 120 ? "…" : "");
}

/** 合成成片 URL：公网 OSS 直链；历史 localhost 上传地址改写为当前 API 基址 */
function resolveComposeOutputUrl(url: string | null | undefined): string {
  if (!url?.trim()) return "";
  const trimmed = url.trim();
  const local = trimmed.match(/^https?:\/\/localhost(?::\d+)?(\/.*)$/i);
  if (local) {
    return resolveMediaUrl(local[1]);
  }
  return resolveMediaUrl(trimmed);
}

function resolveShotAsset(
  assets: DramaForgeAsset[],
  type: DramaForgeAssetType,
  name: string,
): DramaForgeAsset | undefined {
  const key = name.trim().toLowerCase();
  return assets.find((a) => a.type === type && a.name.trim().toLowerCase() === key);
}

/** 镜头绑定的角色/场景/道具：展示设计图与角色音色 */
function ShotBoundAssets({
  shot,
  assets,
  disabled,
  apiKey,
  optimizingKind,
  onPreviewImage,
  onGenerateVoice,
  onSaveVoice,
  onError,
}: {
  shot: DramaForgeShot;
  assets: DramaForgeAsset[];
  disabled?: boolean;
  apiKey: string | null;
  optimizingKind: string | null;
  onPreviewImage?: (url: string, label: string) => void;
  onGenerateVoice: (assetId: string) => Promise<void>;
  onSaveVoice: (assetId: string, input: { voiceLabel?: string; voiceSampleUrl?: string }) => Promise<void>;
  onError: (message: string) => void;
}) {
  const t = useT();
  const characters = (shot.characterRefs ?? [])
    .map((name) => ({ name, asset: resolveShotAsset(assets, "character", name) }))
    .filter((item) => item.name);
  const sceneName = shot.sceneRef?.trim();
  const scene = sceneName
    ? { name: sceneName, asset: resolveShotAsset(assets, "scene", sceneName) }
    : null;
  const props = (shot.propRefs ?? [])
    .map((name) => ({ name, asset: resolveShotAsset(assets, "prop", name) }))
    .filter((item) => item.name);

  if (characters.length === 0 && !scene && props.length === 0) {
    return (
      <p className="mt-2 text-xs text-[var(--ar-text-4)]">
        {t("dramaforge.workspace.shotAssetsNotBound")}
      </p>
    );
  }

  const imageBindings = shot.imageBindings ?? [];
  const audioBindings = shot.audioBindings ?? [];

  function imageTagFor(kind: string, name: string): string | null {
    const needle = `${kind}:${name}`.toLowerCase();
    const hit = imageBindings.find((b) => b.label.toLowerCase() === needle);
    return hit?.tag ?? null;
  }

  function audioTagFor(name: string): string | null {
    const hit = audioBindings.find((b) =>
      b.label.toLowerCase().includes(name.trim().toLowerCase()),
    );
    return hit?.tag ?? null;
  }

  return (
    <div className="mt-3 space-y-2">
      <div className="flex flex-wrap items-center gap-2 text-[11px] font-medium text-[var(--ar-text-4)]">
        <span>{t("dramaforge.workspace.shotBoundAssets")}</span>
        {imageBindings.length > 0 && (
          <span className="font-normal text-[var(--ar-text-3)]">
            {t("dramaforge.workspace.submittedMapping", { mapping: imageBindings.map((b) => `${b.tag}=${b.label}`).join("；") + (audioBindings.length > 0 ? `；${audioBindings.map((b) => `${b.tag}=${b.label}`).join("；")}` : "") })}
          </span>
        )}
      </div>
      <div className="grid gap-2 sm:grid-cols-2 lg:grid-cols-3">
        {characters.map(({ name, asset }) => (
          <ShotAssetCard
            key={`c-${name}`}
            kind={t("dramaforge.assetType.character")}
            name={name}
            asset={asset}
            bindingTag={imageTagFor("角色", name)}
            audioTag={audioTagFor(name)}
            showVoice
            disabled={disabled}
            apiKey={apiKey}
            generating={asset ? optimizingKind === `voice-${asset.id}` : false}
            onPreviewImage={onPreviewImage}
            onGenerateVoice={asset ? () => onGenerateVoice(asset.id) : undefined}
            onSaveVoice={asset ? (input) => onSaveVoice(asset.id, input) : undefined}
            onError={onError}
          />
        ))}
        {scene && (
          <ShotAssetCard
            key={`s-${scene.name}`}
            kind={t("dramaforge.assetType.scene")}
            name={scene.name}
            asset={scene.asset}
            bindingTag={imageTagFor("场景", scene.name)}
            disabled={disabled}
            apiKey={apiKey}
            onPreviewImage={onPreviewImage}
            onError={onError}
          />
        )}
        {props.map(({ name, asset }) => (
          <ShotAssetCard
            key={`p-${name}`}
            kind={t("dramaforge.assetType.prop")}
            name={name}
            asset={asset}
            bindingTag={imageTagFor("道具", name)}
            disabled={disabled}
            apiKey={apiKey}
            onPreviewImage={onPreviewImage}
            onError={onError}
          />
        ))}
      </div>
    </div>
  );
}

function ShotAssetCard({
  kind,
  name,
  asset,
  bindingTag,
  audioTag,
  showVoice,
  disabled,
  apiKey,
  generating,
  onPreviewImage,
  onGenerateVoice,
  onSaveVoice,
  onError,
}: {
  kind: string;
  name: string;
  asset?: DramaForgeAsset;
  bindingTag?: string | null;
  audioTag?: string | null;
  showVoice?: boolean;
  disabled?: boolean;
  apiKey: string | null;
  generating?: boolean;
  onPreviewImage?: (url: string, label: string) => void;
  onGenerateVoice?: () => Promise<void>;
  onSaveVoice?: (input: { voiceLabel?: string; voiceSampleUrl?: string }) => Promise<void>;
  onError: (message: string) => void;
}) {
  const t = useT();
  const audioRef = useRef<HTMLAudioElement | null>(null);
  const imageUrl = resolveMediaUrl(asset?.referenceImageUrl);
  const missingAsset = !asset;

  function handlePreview() {
    if (!asset?.voiceSampleUrl) return;
    const src = resolveMediaUrl(asset.voiceSampleUrl);
    if (!src) return;
    if (!audioRef.current) {
      audioRef.current = new Audio(src);
    } else {
      audioRef.current.src = src;
    }
    void audioRef.current.play().catch(() => {
      onError(t("dramaforge.workspace.playbackFailed"));
    });
  }

  return (
    <div className="overflow-hidden rounded-xl border border-[var(--ar-hairline)] bg-white/60">
      <div className="relative aspect-[4/3] bg-[#17131f]">
        {imageUrl ? (
          <button
            type="button"
            className="h-full w-full cursor-zoom-in text-left transition hover:opacity-95 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-[#7c3aed]/50"
            title={t("dramaforge.workspace.clickToZoom")}
            onClick={() => onPreviewImage?.(imageUrl, `${kind} · ${name}`)}
          >
            {/* eslint-disable-next-line @next/next/no-img-element */}
            <img src={imageUrl} alt={name} className="h-full w-full object-cover" />
          </button>
        ) : (
          <div className="flex h-full w-full flex-col items-center justify-center gap-1 px-3 text-center text-[11px] text-[var(--ar-text-4)]">
            <span>{missingAsset ? t("dramaforge.workspace.assetNotFoundInLibrary") : t("dramaforge.workspace.noDesignImage")}</span>
            <span className="opacity-70">{t("dramaforge.workspace.notSubmittedAsImage")}</span>
          </div>
        )}
        <div className="absolute left-2 top-2 flex flex-wrap gap-1">
          <span className="rounded-full bg-black/55 px-2 py-0.5 text-[10px] text-white">{kind}</span>
          {bindingTag ? (
            <span className="rounded-full bg-[var(--ar-accent)]/90 px-2 py-0.5 text-[10px] text-[#17131f]">
              {bindingTag}
            </span>
          ) : (
            <span className="rounded-full bg-black/40 px-2 py-0.5 text-[10px] text-zinc-300">{t("dramaforge.workspace.notSubmitted")}</span>
          )}
          {audioTag && (
            <span className="rounded-full bg-[#7c3aed]/90 px-2 py-0.5 text-[10px] text-[#17131f]">
              {audioTag}
            </span>
          )}
        </div>
      </div>
      <div className="space-y-1.5 p-2.5">
        <div className="truncate text-sm font-medium text-[var(--ar-text)]">{name}</div>
        {asset?.description && (
          <p className="line-clamp-2 text-[11px] leading-snug text-[var(--ar-text-3)]">{asset.description}</p>
        )}
        {showVoice && (
          <div className="space-y-1.5 border-t border-[var(--ar-hairline)] pt-2">
            <div className="text-[10px] text-[var(--ar-text-4)]">
              {t("dramaforge.workspace.voice")}
              {asset?.voiceLabel ? (
                <span className="ml-1 text-[var(--ar-text-2)]">{asset.voiceLabel}</span>
              ) : (
                <span className="ml-1 text-[var(--ar-danger)]">{t("dramaforge.workspace.notSet")}</span>
              )}
              {asset?.voiceSampleUrl ? (
                <span className="ml-1 text-[var(--ar-accent-2)]">{t("dramaforge.workspace.hasReferenceVoice")}</span>
              ) : (
                <span className="ml-1 text-[var(--ar-text-4)]">{t("dramaforge.workspace.noReferenceVoice")}</span>
              )}
            </div>
            {asset && onGenerateVoice && onSaveVoice ? (
              <div className="flex flex-wrap gap-1.5">
                <button
                  type="button"
                  disabled={disabled || !asset.voiceSampleUrl}
                  onClick={handlePreview}
                  className="rounded-full border border-[var(--ar-hairline)] px-2 py-0.5 text-[10px] text-[var(--ar-text-3)] disabled:opacity-40"
                >
                  {t("dramaforge.workspace.previewAudio")}
                </button>
                <button
                  type="button"
                  disabled={disabled || generating || !apiKey}
                  onClick={() => void onGenerateVoice()}
                  className="rounded-full border border-[var(--ar-hairline)] px-2 py-0.5 text-[10px] text-[var(--ar-accent-2)] disabled:opacity-40"
                >
                  {generating ? t("dramaforge.workspace.generating") : t("dramaforge.workspace.aiVoice")}
                </button>
                <label className="cursor-pointer rounded-full border border-[var(--ar-hairline)] px-2 py-0.5 text-[10px] text-[var(--ar-text-3)]">
                  {t("dramaforge.workspace.uploadVoice")}
                  <input
                    type="file"
                    accept="audio/*"
                    className="hidden"
                    disabled={disabled}
                    onChange={(e) => {
                      const file = e.target.files?.[0];
                      if (!file) return;
                      void uploadMedia(file)
                        .then((res) =>
                          onSaveVoice({
                            voiceLabel: asset.voiceLabel ?? undefined,
                            voiceSampleUrl: res.data.url,
                          }),
                        )
                        .catch((err) =>
                          onError(err instanceof Error ? err.message : t("dramaforge.workspace.voiceUploadFailed")),
                        );
                    }}
                  />
                </label>
              </div>
            ) : null}
          </div>
        )}
      </div>
    </div>
  );
}

function CharacterVoiceBar({
  asset,
  disabled,
  apiKey,
  generating,
  onGenerate,
  onSaveVoice,
  onError,
}: {
  asset: DramaForgeAsset;
  disabled?: boolean;
  apiKey: string | null;
  generating?: boolean;
  onGenerate: () => Promise<void>;
  onSaveVoice: (input: { voiceLabel?: string; voiceSampleUrl?: string }) => Promise<void>;
  onError: (message: string) => void;
}) {
  const t = useT();
  const [voiceLabel, setVoiceLabel] = useState(asset.voiceLabel ?? "");
  const [uploading, setUploading] = useState(false);
  const audioRef = useRef<HTMLAudioElement | null>(null);

  const voiceKey = `${asset.id}:${asset.voiceLabel ?? ""}`;
  const [prevVoiceKey, setPrevVoiceKey] = useState(voiceKey);
  if (prevVoiceKey !== voiceKey) {
    setPrevVoiceKey(voiceKey);
    setVoiceLabel(asset.voiceLabel ?? "");
  }

  async function handleUpload(file: File | null) {
    if (!file) return;
    setUploading(true);
    try {
      const res = await uploadMedia(file);
      await onSaveVoice({
        voiceLabel: voiceLabel.trim() || undefined,
        voiceSampleUrl: res.data.url,
      });
    } catch (e) {
      onError(e instanceof Error ? e.message : t("dramaforge.workspace.voiceUploadFailed"));
    } finally {
      setUploading(false);
    }
  }

  function handlePreview() {
    if (!asset.voiceSampleUrl) return;
    const src = resolveMediaUrl(asset.voiceSampleUrl);
    if (!src) return;
    if (!audioRef.current) {
      audioRef.current = new Audio(src);
    } else {
      audioRef.current.src = src;
    }
    void audioRef.current.play().catch(() => {
      onError(t("dramaforge.workspace.playbackFailed"));
    });
  }

  const sampleUrl = resolveMediaUrl(asset.voiceSampleUrl);

  return (
    <div className="mt-3 space-y-2 rounded-lg border border-[var(--ar-hairline)] bg-white/60 p-3">
      <div className="flex items-center justify-between gap-2">
        <div className="text-xs font-medium text-[var(--ar-text-3)]">{t("dramaforge.workspace.characterVoice")}</div>
        <div className="flex flex-wrap gap-2">
          <DramaForgeSecondaryButton
            disabled={disabled || uploading || !sampleUrl}
            className="px-2 py-1 text-xs"
            onClick={handlePreview}
          >
            {t("dramaforge.workspace.previewAudio")}
          </DramaForgeSecondaryButton>
          <DramaForgeSecondaryButton
            disabled={disabled || uploading || generating || !apiKey}
            className="px-2 py-1 text-xs"
            onClick={() => void onGenerate()}
          >
            {generating ? t("dramaforge.workspace.aiGenerating") : t("dramaforge.workspace.aiGenerateVoice")}
          </DramaForgeSecondaryButton>
        </div>
      </div>
      <div className="flex flex-wrap gap-2">
        <          input
          value={voiceLabel}
          onChange={(e) => setVoiceLabel(e.target.value)}
          onBlur={() => {
            const next = voiceLabel.trim();
            if (next === (asset.voiceLabel ?? "").trim()) return;
            void onSaveVoice({ voiceLabel: next || undefined, voiceSampleUrl: asset.voiceSampleUrl ?? undefined });
          }}
          placeholder={t("dramaforge.workspace.voiceDescriptionPlaceholder")}
          className="dramaforge-input min-w-[12rem] flex-1 rounded-xl px-3 py-1.5 text-xs"
          disabled={disabled || uploading || generating}
        />
        <label className="inline-flex cursor-pointer items-center rounded-full border border-[var(--ar-hairline)] px-3 py-1.5 text-xs text-[var(--ar-text-3)] hover:text-[var(--ar-text)]">
          {uploading ? t("dramaforge.workspace.uploading") : t("dramaforge.workspace.uploadReferenceVoice")}
          <input
            type="file"
            accept="audio/*"
            className="hidden"
            disabled={disabled || uploading || generating}
            onChange={(e) => void handleUpload(e.target.files?.[0] ?? null)}
          />
        </label>
      </div>
      {sampleUrl ? (
        <audio key={sampleUrl} src={sampleUrl} controls preload="metadata" className="h-8 w-full" />
      ) : (
        <p className="text-[11px] text-[var(--ar-text-4)]">
          {t("dramaforge.workspace.voiceNotBoundHint")}
        </p>
      )}
    </div>
  );
}

function AssetEditForm({
  asset,
  disabled,
  optimizing,
  onCancel,
  onSave,
  onAiOptimizeDesign,
}: {
  asset: DramaForgeAsset;
  disabled?: boolean;
  optimizing?: boolean;
  onCancel: () => void;
  onSave: (input: {
    name?: string;
    description?: string;
    designPrompt?: string;
    referenceImageUrl?: string;
    voiceLabel?: string;
    voiceSampleUrl?: string;
    voiceSpeakerId?: string;
    identityLockStrength?: number;
    loraRef?: string;
  }) => Promise<void>;
  onAiOptimizeDesign?: (draft: string) => Promise<string>;
}) {
  const t = useT();
  const [name, setName] = useState(asset.name);
  const [description, setDescription] = useState(asset.description ?? "");
  const [designPrompt, setDesignPrompt] = useState(asset.designPrompt ?? "");
  const [referenceImageUrl, setReferenceImageUrl] = useState(asset.referenceImageUrl ?? "");
  const [voiceLabel, setVoiceLabel] = useState(asset.voiceLabel ?? "");
  const [voiceSampleUrl, setVoiceSampleUrl] = useState(asset.voiceSampleUrl ?? "");
  const [voiceSpeakerId, setVoiceSpeakerId] = useState(asset.voiceSpeakerId ?? "");
  const [identityLockStrength, setIdentityLockStrength] = useState(
    asset.identityLockStrength ?? 75,
  );
  const [loraRef, setLoraRef] = useState(asset.loraRef ?? "");
  const [uploadingVoice, setUploadingVoice] = useState(false);
  const [uploadingImage, setUploadingImage] = useState(false);
  const [localOptimizing, setLocalOptimizing] = useState(false);

  async function handleOptimize() {
    if (!onAiOptimizeDesign) return;
    setLocalOptimizing(true);
    try {
      const text = await onAiOptimizeDesign(designPrompt || description);
      setDesignPrompt(text);
    } finally {
      setLocalOptimizing(false);
    }
  }

  async function handleVoiceUpload(file: File | null) {
    if (!file) return;
    setUploadingVoice(true);
    try {
      const res = await uploadMedia(file);
      setVoiceSampleUrl(res.data.url);
    } finally {
      setUploadingVoice(false);
    }
  }

  async function handleImageUpload(file: File | null) {
    if (!file) return;
    setUploadingImage(true);
    try {
      const res = await uploadMedia(file);
      setReferenceImageUrl(res.data.url);
    } finally {
      setUploadingImage(false);
    }
  }

  return (
    <div className="space-y-2">
      <input
        value={name}
        onChange={(e) => setName(e.target.value)}
        className="dramaforge-input w-full rounded-xl px-3 py-2 text-sm"
      />
      <textarea
        value={description}
        onChange={(e) => setDescription(e.target.value)}
        rows={3}
        placeholder={t("dramaforge.workspace.assetEditDescriptionPlaceholder")}
        className="dramaforge-input w-full rounded-xl px-3 py-2 text-sm"
      />
      <textarea
        value={designPrompt}
        onChange={(e) => setDesignPrompt(e.target.value)}
        rows={3}
        placeholder={t("dramaforge.workspace.designPromptAiPlaceholder")}
        className="dramaforge-input w-full rounded-xl px-3 py-2 text-sm"
      />
      <div className="space-y-2 rounded-lg border border-[var(--ar-hairline)] p-3">
        <div className="text-xs text-[var(--ar-text-4)]">{t("dramaforge.workspace.designReferenceImageLabel")}</div>
        <input
          type="file"
          accept="image/*"
          disabled={disabled || uploadingImage}
          className="block w-full text-xs text-[var(--ar-text-3)] file:mr-3 file:rounded-lg file:border-0 file:bg-[var(--ar-accent-dim)] file:px-3 file:py-1.5 file:text-xs file:text-[var(--ar-accent-2)]"
          onChange={(e) => void handleImageUpload(e.target.files?.[0] ?? null)}
        />
        {referenceImageUrl ? (
          <div className="space-y-2">
            {/* eslint-disable-next-line @next/next/no-img-element */}
            <img
              src={referenceImageUrl}
              alt={t("dramaforge.workspace.referenceImageAlt")}
              className="max-h-32 rounded-lg border border-[var(--ar-hairline)] object-contain"
            />
            <button
              type="button"
              className="text-xs text-[var(--ar-text-4)]"
              onClick={() => setReferenceImageUrl("")}
            >
              {t("dramaforge.workspace.clearReferenceImage")}
            </button>
          </div>
        ) : null}
      </div>
      {asset.type === "character" && (
        <div className="space-y-2 rounded-lg border border-[var(--ar-hairline)] p-3">
          <div className="text-xs text-[var(--ar-text-4)]">{t("dramaforge.workspace.characterVoiceGlobal")}</div>
          <input
            value={voiceLabel}
            onChange={(e) => setVoiceLabel(e.target.value)}
            placeholder={t("dramaforge.workspace.voiceDescriptionPlaceholderEdit")}
            className="dramaforge-input w-full rounded-xl px-3 py-2 text-sm"
          />
          <input
            value={voiceSpeakerId}
            onChange={(e) => setVoiceSpeakerId(e.target.value)}
            placeholder={t("dramaforge.workspace.speakerIdPlaceholder")}
            className="dramaforge-input w-full rounded-xl px-3 py-2 text-xs"
          />
          <label className="flex items-center gap-2 text-xs text-[var(--ar-text-3)]">
            {t("dramaforge.workspace.identityLockStrength")}
            <input
              type="range"
              min={0}
              max={100}
              value={identityLockStrength}
              onChange={(e) => setIdentityLockStrength(Number(e.target.value))}
              className="flex-1"
            />
            <span>{identityLockStrength}%</span>
          </label>
          <input
            value={loraRef}
            onChange={(e) => setLoraRef(e.target.value)}
            placeholder={t("dramaforge.workspace.loraRefPlaceholder")}
            className="dramaforge-input w-full rounded-xl px-3 py-2 text-xs"
          />
          <input
            type="file"
            accept="audio/*"
            disabled={disabled || uploadingVoice}
            className="block w-full text-xs text-[var(--ar-text-3)] file:mr-3 file:rounded-lg file:border-0 file:bg-[var(--ar-accent-dim)] file:px-3 file:py-1.5 file:text-xs file:text-[var(--ar-accent-2)]"
            onChange={(e) => void handleVoiceUpload(e.target.files?.[0] ?? null)}
          />
          {voiceSampleUrl && (
            <div className="flex items-center justify-between gap-2">
              <a
                href={voiceSampleUrl}
                target="_blank"
                rel="noreferrer"
                className="truncate text-xs text-[var(--ar-accent-2)]"
              >
                {t("dramaforge.workspace.voiceBound")}
              </a>
              <button
                type="button"
                className="text-xs text-[var(--ar-text-4)]"
                onClick={() => setVoiceSampleUrl("")}
              >
                {t("dramaforge.workspace.clear")}
              </button>
            </div>
          )}
        </div>
      )}
      {onAiOptimizeDesign && (
        <DramaForgeSecondaryButton
          disabled={disabled || optimizing || localOptimizing}
          className="text-xs"
          onClick={() => void handleOptimize()}
        >
          {optimizing || localOptimizing ? t("dramaforge.workspace.aiOptimizing") : t("dramaforge.workspace.aiOptimizeDesignPrompt")}
        </DramaForgeSecondaryButton>
      )}
      <div className="flex gap-2">
        <DramaForgePrimaryButton
          disabled={disabled || uploadingVoice || uploadingImage}
          className="text-xs"
          onClick={() =>
            void onSave({
              name,
              description,
              designPrompt,
              referenceImageUrl: referenceImageUrl || undefined,
              ...(asset.type === "character"
                ? {
                    voiceLabel,
                    voiceSampleUrl: voiceSampleUrl || undefined,
                    voiceSpeakerId: voiceSpeakerId || undefined,
                    identityLockStrength,
                    loraRef: loraRef || undefined,
                  }
                : {}),
            })
          }
        >
          {t("dramaforge.workspace.save")}
        </DramaForgePrimaryButton>
        <DramaForgeSecondaryButton disabled={disabled} className="text-xs" onClick={onCancel}>
          {t("dramaforge.workspace.cancel")}
        </DramaForgeSecondaryButton>
      </div>
    </div>
  );
}

function ShotEditForm({
  shot,
  assets,
  disabled,
  optimizing,
  onCancel,
  onSave,
  onAiOptimizeDescription,
}: {
  shot: DramaForgeShot;
  assets: DramaForgeAsset[];
  disabled?: boolean;
  optimizing?: boolean;
  onCancel: () => void;
  onSave: (input: {
    description?: string;
    dialogue?: string;
    cameraNote?: string;
    durationSeconds?: number;
    characterRefs?: string[];
    sceneRef?: string;
    propRefs?: string[];
    forceCharacterBinding?: boolean;
    referenceVideoMode?: string;
    referenceVideoUrl?: string;
    firstFrameUrl?: string;
  }) => Promise<void>;
  onAiOptimizeDescription?: (draft: string) => Promise<string>;
}) {
  const t = useT();
  const characterOptions = assets.filter((a) => a.type === "character").map((a) => a.name);
  const sceneOptions = assets.filter((a) => a.type === "scene").map((a) => a.name);
  const propOptions = assets.filter((a) => a.type === "prop").map((a) => a.name);

  const [description, setDescription] = useState(shot.description);
  const [dialogue, setDialogue] = useState(shot.dialogue ?? "");
  const [cameraNote, setCameraNote] = useState(shot.cameraNote ?? "");
  const [durationSeconds, setDurationSeconds] = useState(shot.durationSeconds ?? 5);
  const [characterRefs, setCharacterRefs] = useState<string[]>(shot.characterRefs ?? []);
  const [sceneRef, setSceneRef] = useState(shot.sceneRef ?? "");
  const [propRefs, setPropRefs] = useState<string[]>(shot.propRefs ?? []);
  const [forceCharacterBinding, setForceCharacterBinding] = useState(
    shot.forceCharacterBinding ?? false,
  );
  const [referenceVideoMode, setReferenceVideoMode] = useState(
    shot.referenceVideoMode ?? "auto",
  );
  const [referenceVideoUrl, setReferenceVideoUrl] = useState(shot.referenceVideoUrl ?? "");
  const [firstFrameUrl, setFirstFrameUrl] = useState(shot.firstFrameUrl ?? "");
  const [uploadingFirstFrame, setUploadingFirstFrame] = useState(false);
  const [localOptimizing, setLocalOptimizing] = useState(false);

  function toggleRef(list: string[], name: string, checked: boolean): string[] {
    if (checked) {
      return list.includes(name) ? list : [...list, name];
    }
    return list.filter((n) => n !== name);
  }

  async function handleOptimize() {
    if (!onAiOptimizeDescription) return;
    setLocalOptimizing(true);
    try {
      const text = await onAiOptimizeDescription(description);
      setDescription(text);
    } finally {
      setLocalOptimizing(false);
    }
  }

  return (
    <div className="mt-2 space-y-2 rounded-lg border border-[var(--ar-hairline)] p-3">
      <div className="space-y-2 rounded-lg border border-dashed border-[var(--ar-hairline)] p-2">
        <div className="text-xs font-medium text-[var(--ar-text-3)]">{t("dramaforge.workspace.shotAssetBinding")}</div>
        {characterOptions.length > 0 && (
          <div>
            <div className="mb-1 text-[11px] text-[var(--ar-text-4)]">{t("dramaforge.assetType.character")}</div>
            <div className="flex flex-wrap gap-2">
              {characterOptions.map((name) => (
                <label key={name} className="flex items-center gap-1 text-xs text-[var(--ar-text-2)]">
                  <input
                    type="checkbox"
                    checked={characterRefs.includes(name)}
                    onChange={(e) =>
                      setCharacterRefs(toggleRef(characterRefs, name, e.target.checked))
                    }
                  />
                  {name}
                </label>
              ))}
            </div>
          </div>
        )}
        {sceneOptions.length > 0 && (
          <label className="block text-xs text-[var(--ar-text-2)]">
            {t("dramaforge.assetType.scene")}
            <DfSelect
              size="sm"
              className="mt-1 w-full"
              value={sceneRef}
              onChange={setSceneRef}
              searchable={sceneOptions.length > 8}
              options={[
                { value: "", label: t("dramaforge.workspace.unselected") },
                ...sceneOptions.map((name) => ({ value: name, label: name })),
              ]}
            />
          </label>
        )}
        {propOptions.length > 0 && (
          <div>
            <div className="mb-1 text-[11px] text-[var(--ar-text-4)]">{t("dramaforge.assetType.prop")}</div>
            <div className="flex flex-wrap gap-2">
              {propOptions.map((name) => (
                <label key={name} className="flex items-center gap-1 text-xs text-[var(--ar-text-2)]">
                  <input
                    type="checkbox"
                    checked={propRefs.includes(name)}
                    onChange={(e) => setPropRefs(toggleRef(propRefs, name, e.target.checked))}
                  />
                  {name}
                </label>
              ))}
            </div>
          </div>
        )}
        <label className="flex items-center gap-2 text-xs text-[var(--ar-text-2)]">
          <input
            type="checkbox"
            checked={forceCharacterBinding}
            onChange={(e) => setForceCharacterBinding(e.target.checked)}
          />
          {t("dramaforge.workspace.forceCharacterBindingLabel")}
        </label>
      </div>
      <div className="space-y-2 rounded-lg border border-dashed border-[var(--ar-hairline)] p-2">
        <div className="text-xs font-medium text-[var(--ar-text-3)]">{t("dramaforge.workspace.crossShotVideoReference")}</div>
        <DfSelect
          size="sm"
          className="w-full"
          value={referenceVideoMode}
          onChange={setReferenceVideoMode}
          searchable={false}
          options={[
            { value: "auto", label: t("dramaforge.workspace.refVideoAuto") },
            { value: "none", label: t("dramaforge.workspace.refVideoNone") },
            { value: "custom", label: t("dramaforge.workspace.refVideoCustom") },
          ]}
        />
        {(referenceVideoMode === "auto" || referenceVideoMode === "custom") && (
          <input
            value={referenceVideoUrl}
            onChange={(e) => setReferenceVideoUrl(e.target.value)}
            placeholder={t("dramaforge.workspace.refVideoUrlPlaceholder")}
            className="dramaforge-input w-full rounded-xl px-3 py-2 text-xs"
          />
        )}
      </div>
      <div className="space-y-2 rounded-lg border border-dashed border-[var(--ar-hairline)] p-2">
        <div className="text-xs font-medium text-[var(--ar-text-3)]">{t("dramaforge.workspace.firstFrameAnchor")}</div>
        <input
          type="file"
          accept="image/*"
          disabled={disabled || uploadingFirstFrame}
          className="block w-full text-xs text-[var(--ar-text-3)] file:mr-3 file:rounded-lg file:border-0 file:bg-[var(--ar-accent-dim)] file:px-3 file:py-1.5 file:text-xs file:text-[var(--ar-accent-2)]"
          onChange={(e) => {
            const file = e.target.files?.[0];
            if (!file) return;
            setUploadingFirstFrame(true);
            void uploadMedia(file)
              .then((res) => setFirstFrameUrl(res.data.url))
              .finally(() => setUploadingFirstFrame(false));
          }}
        />
        {firstFrameUrl ? (
          <div className="space-y-1">
            {/* eslint-disable-next-line @next/next/no-img-element */}
            <img src={firstFrameUrl} alt={t("dramaforge.workspace.firstFrameAlt")} className="max-h-24 rounded border border-[var(--ar-hairline)]" />
            <button type="button" className="text-xs text-[var(--ar-text-4)]" onClick={() => setFirstFrameUrl("")}>
              {t("dramaforge.workspace.clearFirstFrame")}
            </button>
          </div>
        ) : (
          <p className="text-[11px] text-[var(--ar-text-4)]">{t("dramaforge.workspace.firstFrameNotSetHint")}</p>
        )}
      </div>
      <textarea
        value={description}
        onChange={(e) => setDescription(e.target.value)}
        rows={10}
        className="dramaforge-input w-full rounded-xl px-3 py-2 text-sm leading-relaxed"
        placeholder={t("dramaforge.workspace.shotDescriptionPlaceholder")}
      />
      {onAiOptimizeDescription && (
        <DramaForgeSecondaryButton
          disabled={disabled || optimizing || localOptimizing}
          className="text-xs"
          onClick={() => void handleOptimize()}
        >
          {optimizing || localOptimizing ? t("dramaforge.workspace.aiOptimizing") : t("dramaforge.workspace.aiOptimizeShotDescription")}
        </DramaForgeSecondaryButton>
      )}
      <input
        value={dialogue}
        onChange={(e) => setDialogue(e.target.value)}
        className="dramaforge-input w-full rounded-xl px-3 py-2 text-sm"
        placeholder={t("dramaforge.workspace.dialogue")}
      />
      <input
        value={cameraNote}
        onChange={(e) => setCameraNote(e.target.value)}
        className="dramaforge-input w-full rounded-xl px-3 py-2 text-sm"
        placeholder={t("dramaforge.workspace.cameraMove")}
      />
      <label className="block text-xs text-[var(--ar-text-3)]">
        {t("dramaforge.workspace.durationSeconds")}
        <DfNumberStepper
          size="sm"
          className="mt-1.5 max-w-[140px]"
          min={2}
          max={15}
          step={1}
          value={durationSeconds}
          onChange={setDurationSeconds}
          disabled={disabled}
        />
      </label>
      <div className="flex gap-2">
        <DramaForgePrimaryButton
          disabled={disabled}
          className="text-xs"
          onClick={() =>
            void onSave({
              description,
              dialogue,
              cameraNote,
              durationSeconds,
              characterRefs,
              sceneRef: sceneRef || undefined,
              propRefs,
              forceCharacterBinding,
              referenceVideoMode,
              referenceVideoUrl: referenceVideoUrl || undefined,
              firstFrameUrl: firstFrameUrl || undefined,
            })
          }
        >
          {t("dramaforge.workspace.save")}
        </DramaForgePrimaryButton>
        <DramaForgeSecondaryButton disabled={disabled || uploadingFirstFrame} className="text-xs" onClick={onCancel}>
          {t("dramaforge.workspace.cancel")}
        </DramaForgeSecondaryButton>
      </div>
    </div>
  );
}
