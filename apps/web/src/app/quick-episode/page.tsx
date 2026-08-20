"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import Link from "next/link";
import { CheckCircle2, CircleDashed, Loader2, Sparkles, XCircle } from "lucide-react";
import type {
  DramaForgeAspectRatio,
  DramaForgeComposition,
  DramaForgeJob,
} from "@dreamreel/shared-types";
import { defaultDramaForgeAspectRatio } from "@dreamreel/shared-types";
import { useAuth } from "@/components/auth/auth-provider";
import { useT } from "@/i18n/locale-provider";
import type { MessagePath } from "@/i18n/translate";
import { createProject } from "@/lib/api";
import { resolveTokenfreeApiKey } from "@/lib/api-key";
import { resolveMediaUrl } from "@/lib/api-base";
import {
  composeDramaForgeEpisode,
  extractDramaForgeAssets,
  fetchDramaForgeAssets,
  fetchDramaForgeCompositions,
  fetchDramaForgeConfig,
  fetchDramaForgeEpisodes,
  fetchDramaForgeJobs,
  fetchDramaForgeShots,
  generateDramaForgeAssetDesigns,
  generateDramaForgeCharacterVoice,
  generateDramaForgeScript,
  generateDramaForgeVideos,
  lockDramaForgeAssets,
  lockDramaForgeScript,
  regenerateDramaForgeAssetDesign,
  syncDramaForgeVideos,
  updateDramaForgeConfig,
} from "@/modules/dramaforge/api";

function formatJobTime(iso: string) {
  try {
    const d = new Date(iso);
    return d.toLocaleString(undefined, {
      month: "2-digit",
      day: "2-digit",
      hour: "2-digit",
      minute: "2-digit",
      second: "2-digit",
    });
  } catch {
    return iso;
  }
}

type StepKey = "config" | "script" | "assets" | "video" | "compose";
type StepState = "idle" | "running" | "done" | "failed";
type RunStatus = "running" | "completed" | "failed";

interface QuickEpisodeRun {
  id: string;
  projectId: string;
  projectName: string;
  idea: string;
  aspectRatio: DramaForgeAspectRatio;
  status: RunStatus;
  currentStep?: StepKey;
  errorMessage?: string | null;
  outputUrl?: string | null;
  stepStates: Record<StepKey, StepState>;
  stepMessages: Partial<Record<StepKey, string>>;
  createdAt: string;
  updatedAt: string;
}

const STEP_ORDER: StepKey[] = ["config", "script", "assets", "video", "compose"];
const RUNS_STORAGE_PREFIX = "dreamreel.quickEpisode.runs.v1";
const ACTIVE_RUN_STORAGE_PREFIX = "dreamreel.quickEpisode.activeRun.v1";
const MAX_PERSISTED_RUNS = 40;

const IDLE_STEP_STATES: Record<StepKey, StepState> = {
  config: "idle",
  script: "idle",
  assets: "idle",
  video: "idle",
  compose: "idle",
};

function storageKeyForUser(prefix: string, userId?: string | null) {
  return `${prefix}:${userId || "anon"}`;
}

function loadRuns(userId?: string | null): QuickEpisodeRun[] {
  if (typeof window === "undefined") return [];
  try {
    const raw = window.localStorage.getItem(storageKeyForUser(RUNS_STORAGE_PREFIX, userId));
    if (!raw) return [];
    const parsed = JSON.parse(raw) as QuickEpisodeRun[];
    if (!Array.isArray(parsed)) return [];
    return parsed
      .filter((r) => r && typeof r.projectId === "string" && typeof r.id === "string")
      .sort((a, b) => new Date(b.updatedAt).getTime() - new Date(a.updatedAt).getTime());
  } catch {
    return [];
  }
}

function saveRuns(userId: string | null | undefined, runs: QuickEpisodeRun[]) {
  if (typeof window === "undefined") return;
  try {
    window.localStorage.setItem(
      storageKeyForUser(RUNS_STORAGE_PREFIX, userId),
      JSON.stringify(runs.slice(0, MAX_PERSISTED_RUNS)),
    );
  } catch {
    /* quota / private mode */
  }
}

function loadActiveRunId(userId?: string | null): string | null {
  if (typeof window === "undefined") return null;
  try {
    return window.localStorage.getItem(storageKeyForUser(ACTIVE_RUN_STORAGE_PREFIX, userId));
  } catch {
    return null;
  }
}

function saveActiveRunId(userId: string | null | undefined, runId: string | null) {
  if (typeof window === "undefined") return;
  try {
    const key = storageKeyForUser(ACTIVE_RUN_STORAGE_PREFIX, userId);
    if (runId) window.localStorage.setItem(key, runId);
    else window.localStorage.removeItem(key);
  } catch {
    /* ignore */
  }
}

function upsertRun(userId: string | null | undefined, run: QuickEpisodeRun): QuickEpisodeRun[] {
  const existing = loadRuns(userId).filter((r) => r.id !== run.id);
  const next = [run, ...existing].slice(0, MAX_PERSISTED_RUNS);
  saveRuns(userId, next);
  return next;
}

// TokenFree 网关实测可用：seedream/4.5-text-to-image（doubao-seedream-5-0-pro 无可用渠道）
const PRIMARY_IMAGE_BACKEND = "seedream/4.5-text-to-image";
const FALLBACK_IMAGE_BACKEND = "seedream/5-lite-text-to-image";

function isNoChannelError(e: unknown): boolean {
  const message = e instanceof Error ? e.message : String(e);
  return message.includes("无可用渠道") || message.includes("model_not_found");
}

const ASPECT_OPTIONS: { value: DramaForgeAspectRatio; labelKey: string }[] = [
  { value: "9:16", labelKey: "quickEpisode.aspectPortrait" },
  { value: "16:9", labelKey: "quickEpisode.aspectLandscape" },
];

function sleep(ms: number) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

function resolveComposeOutputUrl(url: string | null | undefined): string {
  if (!url?.trim()) return "";
  const trimmed = url.trim();
  const local = trimmed.match(/^https?:\/\/localhost(?::\d+)?(\/.*)$/i);
  if (local) {
    return resolveMediaUrl(local[1]);
  }
  if (/^https?:\/\//i.test(trimmed)) {
    return trimmed;
  }
  return resolveMediaUrl(trimmed);
}

function runStatusLabel(
  status: RunStatus,
  t: (key: MessagePath, params?: Record<string, string | number>) => string,
) {
  if (status === "completed") return t("quickEpisode.runStatusCompleted");
  if (status === "failed") return t("quickEpisode.runStatusFailed");
  return t("quickEpisode.runStatusRunning");
}

/** 刷新后仍显示 spinning 的步骤视为中断，便于展示「继续」 */
function freezeInterruptedRun(run: QuickEpisodeRun): QuickEpisodeRun {
  if (run.status === "completed") return run;
  const stepStates = { ...(run.stepStates ?? IDLE_STEP_STATES) };
  let touched = false;
  for (const key of STEP_ORDER) {
    if (stepStates[key] === "running") {
      stepStates[key] = "failed";
      touched = true;
    }
  }
  if (!touched && run.status === "running") {
    return {
      ...run,
      status: "failed",
      errorMessage: run.errorMessage ?? null,
    };
  }
  if (!touched) return run;
  return {
    ...run,
    status: "failed",
    stepStates,
    errorMessage: run.errorMessage ?? null,
  };
}

interface PipelineProbe {
  configDone: boolean;
  scriptDone: boolean;
  episodeId: string | null;
  assetsDone: boolean;
  assetsExtracted: boolean;
  assetsDesigned: boolean;
  videoDone: boolean;
  composeDone: boolean;
  outputUrl: string | null;
}

async function probePipeline(pid: string): Promise<PipelineProbe> {
  const probe: PipelineProbe = {
    configDone: false,
    scriptDone: false,
    episodeId: null,
    assetsDone: false,
    assetsExtracted: false,
    assetsDesigned: false,
    videoDone: false,
    composeDone: false,
    outputUrl: null,
  };

  const cfg = await fetchDramaForgeConfig(pid).catch(() => null);
  // 续跑时已有 projectId，配置步视为已完成；若控制台已锁资产则一并标记
  probe.configDone = true;
  if (cfg?.data?.assetsLockedAt) {
    probe.assetsDone = true;
    probe.assetsExtracted = true;
    probe.assetsDesigned = true;
  }

  const eps = await fetchDramaForgeEpisodes(pid).catch(() => null);
  const episode = eps?.data?.[0] ?? null;
  if (episode) {
    probe.episodeId = episode.id;
    if (episode.scriptLockedAt || episode.shotCount > 0) {
      probe.scriptDone = true;
    }
  }

  if (!probe.assetsDone) {
    const assets = await fetchDramaForgeAssets(pid).catch(() => null);
    const list = assets?.data ?? [];
    if (list.length > 0) {
      probe.assetsExtracted = true;
      probe.assetsDesigned = list.every((a) => Boolean(a.referenceImageUrl?.trim()));
      // 有图但未锁定：仍进资产步做 lock，不算整步完成
    }
  }

  if (probe.episodeId) {
    const shotsRes = await fetchDramaForgeShots(pid, probe.episodeId).catch(() => null);
    const shots = shotsRes?.data ?? [];
    if (shots.length > 0 && shots.every((s) => s.status === "video_done")) {
      probe.videoDone = true;
    }
  }

  const compositions = await fetchDramaForgeCompositions(pid).catch(() => null);
  const completed = (compositions?.data ?? [])
    .filter((c) => c.status === "completed" && c.outputUrl?.trim())
    .filter((c) => !probe.episodeId || !c.episodeId || c.episodeId === probe.episodeId)
    .sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime())[0];
  if (completed?.outputUrl) {
    probe.composeDone = true;
    probe.outputUrl = resolveComposeOutputUrl(completed.outputUrl);
    probe.videoDone = true;
    probe.assetsDone = true;
    probe.scriptDone = true;
    probe.configDone = true;
  }

  return probe;
}

export default function QuickEpisodePage() {
  const t = useT();
  const { user } = useAuth();
  const userId = user?.id ?? null;
  const apiKey = resolveTokenfreeApiKey(user);

  const [idea, setIdea] = useState("");
  const [aspectRatio, setAspectRatio] = useState<DramaForgeAspectRatio>(
    defaultDramaForgeAspectRatio("drama"),
  );
  const [running, setRunning] = useState(false);
  const [stepStates, setStepStates] = useState<Record<StepKey, StepState>>({ ...IDLE_STEP_STATES });
  const [stepMessages, setStepMessages] = useState<Partial<Record<StepKey, string>>>({});
  const [error, setError] = useState<string | null>(null);
  const [projectId, setProjectId] = useState<string | null>(null);
  const [outputUrl, setOutputUrl] = useState<string | null>(null);
  const [runs, setRuns] = useState<QuickEpisodeRun[]>([]);
  const [activeRunId, setActiveRunId] = useState<string | null>(null);
  const [hydrated, setHydrated] = useState(false);
  const cancelledRef = useRef(false);
  const activeRunRef = useRef<QuickEpisodeRun | null>(null);
  const stepStatesRef = useRef(stepStates);
  const stepMessagesRef = useRef(stepMessages);

  useEffect(() => {
    stepStatesRef.current = stepStates;
  }, [stepStates]);
  useEffect(() => {
    stepMessagesRef.current = stepMessages;
  }, [stepMessages]);

  const persistRunSnapshot = useCallback(
    (patch: Partial<QuickEpisodeRun> & { id: string; projectId: string }) => {
      const prev = activeRunRef.current;
      if (!prev || prev.id !== patch.id) {
        // allow first write when prev not set yet
      }
      const base: QuickEpisodeRun = prev && prev.id === patch.id
        ? prev
        : {
            id: patch.id,
            projectId: patch.projectId,
            projectName: patch.projectName ?? "",
            idea: patch.idea ?? "",
            aspectRatio: patch.aspectRatio ?? "9:16",
            status: "running",
            stepStates: { ...IDLE_STEP_STATES },
            stepMessages: {},
            createdAt: new Date().toISOString(),
            updatedAt: new Date().toISOString(),
          };
      const next: QuickEpisodeRun = {
        ...base,
        ...patch,
        stepStates: patch.stepStates ?? stepStatesRef.current,
        stepMessages: patch.stepMessages ?? stepMessagesRef.current,
        updatedAt: new Date().toISOString(),
      };
      activeRunRef.current = next;
      setRuns(upsertRun(userId, next));
      setActiveRunId(next.id);
      saveActiveRunId(userId, next.id);
      return next;
    },
    [userId],
  );

  const applyRunToView = useCallback((run: QuickEpisodeRun) => {
    const frozen = freezeInterruptedRun(run);
    if (frozen !== run) {
      setRuns(upsertRun(userId, frozen));
    }
    activeRunRef.current = frozen;
    setActiveRunId(frozen.id);
    setProjectId(frozen.projectId);
    setIdea(frozen.idea);
    setAspectRatio(frozen.aspectRatio);
    setStepStates(frozen.stepStates ?? { ...IDLE_STEP_STATES });
    setStepMessages(frozen.stepMessages ?? {});
    setError(frozen.errorMessage ?? null);
    setOutputUrl(frozen.outputUrl ?? null);
    setRunning(false);
    saveActiveRunId(userId, frozen.id);
  }, [userId]);

  // 刷新后恢复操作记录与对应项目任务
  useEffect(() => {
    const timer = window.setTimeout(() => {
      const list = loadRuns(userId);
      setRuns(list);
      const preferredId = loadActiveRunId(userId);
      const selected =
        list.find((r) => r.id === preferredId)
        ?? list.find((r) => r.status === "running")
        ?? list[0]
        ?? null;
      if (selected) {
        applyRunToView(selected);
      }
      setHydrated(true);
    }, 0);
    return () => window.clearTimeout(timer);
  }, [userId, applyRunToView]);

  const refreshJobs = useCallback(async (pid: string) => {
    try {
      const res = await fetchDramaForgeJobs(pid, 80);
      return res.data;
    } catch {
      return null;
    }
  }, []);

  // 刷新后恢复成片预览（若本地没存 URL）
  useEffect(() => {
    if (!projectId || !hydrated || outputUrl || running) return;
    let cancelled = false;
    void fetchDramaForgeCompositions(projectId)
      .then((res) => {
        if (cancelled) return;
        const completed = res.data
          .filter((c) => c.status === "completed" && c.outputUrl?.trim())
          .sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime())[0];
        if (completed?.outputUrl) {
          const url = resolveComposeOutputUrl(completed.outputUrl);
          setOutputUrl(url);
          if (activeRunRef.current?.projectId === projectId) {
            persistRunSnapshot({
              id: activeRunRef.current.id,
              projectId,
              outputUrl: url,
            });
          }
        }
      })
      .catch(() => {});
    return () => {
      cancelled = true;
    };
  }, [projectId, hydrated, outputUrl, running, persistRunSnapshot]);

  function setStep(key: StepKey, state: StepState, message?: string) {
    setStepStates((prev) => {
      const next = { ...prev, [key]: state };
      stepStatesRef.current = next;
      return next;
    });
    if (message !== undefined) {
      setStepMessages((prev) => {
        const next = { ...prev, [key]: message };
        stepMessagesRef.current = next;
        return next;
      });
    }
    const run = activeRunRef.current;
    if (run) {
      const stepStateMap = { ...stepStatesRef.current, [key]: state };
      const messages = message !== undefined
        ? { ...stepMessagesRef.current, [key]: message }
        : stepMessagesRef.current;
      const hasFailed = Object.values(stepStateMap).some((s) => s === "failed");
      const allDone = STEP_ORDER.every((k) => stepStateMap[k] === "done");
      persistRunSnapshot({
        id: run.id,
        projectId: run.projectId,
        currentStep: key,
        stepStates: stepStateMap,
        stepMessages: messages,
        status: hasFailed ? "failed" : allDone ? "completed" : "running",
        errorMessage: hasFailed ? (message ?? run.errorMessage ?? null) : run.errorMessage ?? null,
      });
    }
  }

  function throwIfCancelled() {
    if (cancelledRef.current) {
      throw new Error(t("quickEpisode.cancelled"));
    }
  }

  function selectRun(run: QuickEpisodeRun) {
    if (running) return;
    applyRunToView(run);
  }

  function removeRun(runId: string) {
    if (running) return;
    const next = loadRuns(userId).filter((r) => r.id !== runId);
    saveRuns(userId, next);
    setRuns(next);
    if (activeRunId === runId) {
      const fallback = next[0] ?? null;
      if (fallback) {
        selectRun(fallback);
      } else {
        activeRunRef.current = null;
        setActiveRunId(null);
        setProjectId(null);
        setStepStates({ ...IDLE_STEP_STATES });
        setStepMessages({});
        setError(null);
        setOutputUrl(null);
        saveActiveRunId(userId, null);
      }
    }
  }

  /** 轮询任务直至完成；任务行被清理时视为已完成 */
  async function waitJob(
    pid: string,
    jobId: string,
    onProgress: (job: DramaForgeJob) => void,
    timeoutMs = 20 * 60 * 1000,
  ) {
    const deadline = Date.now() + timeoutMs;
    for (;;) {
      throwIfCancelled();
      if (Date.now() > deadline) {
        throw new Error(t("quickEpisode.jobTimeout"));
      }
      await sleep(3000);
      const jobList = await refreshJobs(pid);
      if (!jobList) continue;
      const job = jobList.find((j) => j.id === jobId);
      if (!job) return;
      onProgress(job);
      if (job.status === "completed") return;
      if (job.status === "failed" || job.status === "cancelled") {
        throw new Error(job.errorMessage?.trim() || t("quickEpisode.jobFailed"));
      }
    }
  }

  /** 轮询镜头直至全部 video_done；无活跃任务且仍有未完成镜头时报错 */
  async function waitVideos(pid: string, episodeId: string, timeoutMs = 45 * 60 * 1000) {
    const deadline = Date.now() + timeoutMs;
    for (;;) {
      throwIfCancelled();
      if (Date.now() > deadline) {
        throw new Error(t("quickEpisode.jobTimeout"));
      }
      await sleep(5000);
      const shotsRes = await fetchDramaForgeShots(pid, episodeId).catch(() => null);
      if (!shotsRes) continue;
      const shots = shotsRes.data;
      const total = shots.length;
      const done = shots.filter((s) => s.status === "video_done").length;
      const failed = shots.filter((s) => s.status === "failed").length;
      setStep("video", "running", t("quickEpisode.videoProgress", { done, total }));
      if (total > 0 && done === total) return;
      const jobList = await refreshJobs(pid);
      const hasActive = jobList?.some(
        (j) =>
          (j.status === "queued" || j.status === "running")
          && ["video", "sync_videos", "shot_video"].includes(j.jobType),
      ) ?? true;
      if (!hasActive && done + failed >= total) {
        throw new Error(t("quickEpisode.videoPartialFailed", { failed, total }));
      }
    }
  }

  /** 轮询合成结果 */
  async function waitCompose(
    pid: string,
    episodeId: string,
    startedAt: number,
    timeoutMs = 15 * 60 * 1000,
  ): Promise<DramaForgeComposition> {
    const deadline = Date.now() + timeoutMs;
    for (;;) {
      throwIfCancelled();
      if (Date.now() > deadline) {
        throw new Error(t("quickEpisode.jobTimeout"));
      }
      await sleep(4000);
      const res = await fetchDramaForgeCompositions(pid).catch(() => null);
      if (!res) continue;
      const candidates = res.data
        .filter((c) => (!c.episodeId || c.episodeId === episodeId)
          && new Date(c.createdAt).getTime() >= startedAt - 60_000)
        .sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime());
      const latest = candidates[0];
      if (!latest) continue;
      if (latest.status === "completed" && latest.outputUrl?.trim()) return latest;
      if (latest.status === "failed") {
        throw new Error(latest.errorMessage?.trim() || t("quickEpisode.composeFailed"));
      }
    }
  }

  async function runPipeline(opts: { resume: boolean }) {
    if (running) return;
    const text = idea.trim();
    if (!opts.resume && !text) return;

    cancelledRef.current = false;
    setRunning(true);
    setError(null);

    let currentRunId: string | null = null;
    let currentProjectId: string | null = null;
    let currentStep: StepKey = "config";
    let episodeId: string | null = null;

    try {
      let pid: string;
      let probe: PipelineProbe | null = null;

      if (opts.resume) {
        const existing = activeRunRef.current;
        if (!existing?.projectId) {
          throw new Error(t("quickEpisode.continueNoProject"));
        }
        pid = existing.projectId;
        currentRunId = existing.id;
        currentProjectId = pid;
        setProjectId(pid);
        setOutputUrl(existing.outputUrl ?? null);
        persistRunSnapshot({
          id: existing.id,
          projectId: pid,
          status: "running",
          errorMessage: null,
          currentStep: existing.currentStep,
        });
        setStep("config", "running", t("quickEpisode.continueProbing"));
        probe = await probePipeline(pid);
        episodeId = probe.episodeId;
        if (probe.outputUrl) {
          setOutputUrl(probe.outputUrl);
        }
        // 把已完成步骤标绿，从第一处未完成继续
        if (probe.configDone) setStep("config", "done", t("quickEpisode.configDone"));
        if (probe.scriptDone) setStep("script", "done", t("quickEpisode.scriptDone"));
        if (probe.assetsDone) setStep("assets", "done", t("quickEpisode.assetsDone"));
        if (probe.videoDone) setStep("video", "done", t("quickEpisode.videoDone"));
        if (probe.composeDone && probe.outputUrl) {
          setStep("compose", "done", t("quickEpisode.composeDone"));
          persistRunSnapshot({
            id: existing.id,
            projectId: pid,
            status: "completed",
            outputUrl: probe.outputUrl,
            errorMessage: null,
            stepStates: {
              config: "done",
              script: "done",
              assets: "done",
              video: "done",
              compose: "done",
            },
          });
          return;
        }
        await refreshJobs(pid);
      } else {
        setOutputUrl(null);
        setProjectId(null);
        setStepMessages({});
        stepMessagesRef.current = {};
        setStepStates({ ...IDLE_STEP_STATES });
        stepStatesRef.current = { ...IDLE_STEP_STATES };

        currentStep = "config";
        setStep("config", "running", t("quickEpisode.configRunning"));
        const projectName = text.replace(/\s+/g, " ").slice(0, 20) || t("quickEpisode.defaultProjectName");
        const proj = await createProject({
          name: projectName,
          type: "SHORT_DRAMA",
          description: text.slice(0, 200),
        });
        pid = proj.data.id;
        const runId = crypto.randomUUID();
        currentRunId = runId;
        currentProjectId = pid;
        setProjectId(pid);
        persistRunSnapshot({
          id: runId,
          projectId: pid,
          projectName,
          idea: text,
          aspectRatio,
          status: "running",
          currentStep: "config",
          errorMessage: null,
          outputUrl: null,
          stepStates: { ...IDLE_STEP_STATES, config: "running" },
          stepMessages: { config: t("quickEpisode.configRunning") },
          createdAt: new Date().toISOString(),
        });
        await refreshJobs(pid);
        await updateDramaForgeConfig(pid, {
          sourceText: text,
          stylePrompt: "",
          contentMode: "drama",
          generationMode: "reference_to_video",
          aspectRatio,
          imageBackend: PRIMARY_IMAGE_BACKEND,
        });
        setStep("config", "done", t("quickEpisode.configDone"));
      }

      // 续跑时确保关键配置仍是「设计图直出视频」
      if (opts.resume) {
        currentStep = "config";
        const resumeIdea = text || activeRunRef.current?.idea || "";
        const resumeAspect = activeRunRef.current?.aspectRatio ?? aspectRatio;
        await updateDramaForgeConfig(pid, {
          sourceText: resumeIdea || undefined,
          contentMode: "drama",
          generationMode: "reference_to_video",
          aspectRatio: resumeAspect,
          imageBackend: PRIMARY_IMAGE_BACKEND,
        }).catch(() => {});
        if (!probe?.configDone) {
          setStep("config", "done", t("quickEpisode.configDone"));
        }
      }

      // ② 定剧本
      if (!(probe?.scriptDone)) {
        currentStep = "script";
        setStep("script", "running", t("quickEpisode.scriptRunning"));
        const scriptJob = await generateDramaForgeScript(pid, apiKey);
        await waitJob(pid, scriptJob.data.id, (job) => {
          if (job.progressMessage) setStep("script", "running", job.progressMessage);
        });
      } else {
        currentStep = "script";
      }
      {
        const eps = await fetchDramaForgeEpisodes(pid);
        const episode = eps.data[0];
        if (!episode) {
          throw new Error(t("quickEpisode.noEpisodeGenerated"));
        }
        episodeId = episode.id;
        if (!episode.scriptLockedAt) {
          await lockDramaForgeScript(pid, episode.id);
        }
        setStep("script", "done", t("quickEpisode.scriptDone"));
      }

      // ③ 定资产
      if (!(probe?.assetsDone)) {
        currentStep = "assets";
        let assets = (await fetchDramaForgeAssets(pid)).data;
        if (!(probe?.assetsExtracted) && assets.length === 0) {
          setStep("assets", "running", t("quickEpisode.assetsExtracting"));
          const extractJob = await extractDramaForgeAssets(pid, apiKey);
          await waitJob(pid, extractJob.data.id, (job) => {
            if (job.progressMessage) setStep("assets", "running", job.progressMessage);
          });
          assets = (await fetchDramaForgeAssets(pid)).data;
        }
        if (assets.length === 0) {
          throw new Error(t("quickEpisode.noAssetsExtracted"));
        }
        let imageBackendSwitched = false;
        const switchImageBackendIfNeeded = async (e: unknown) => {
          if (imageBackendSwitched || !isNoChannelError(e)) return;
          imageBackendSwitched = true;
          await updateDramaForgeConfig(pid, { imageBackend: FALLBACK_IMAGE_BACKEND }).catch(() => {});
        };
        if (!(probe?.assetsDesigned) || assets.some((a) => !a.referenceImageUrl?.trim())) {
          if (assets.some((a) => !a.referenceImageUrl?.trim())) {
            setStep("assets", "running", t("quickEpisode.assetsDesigning"));
            try {
              const designJob = await generateDramaForgeAssetDesigns(pid, apiKey);
              await waitJob(pid, designJob.data.id, (job) => {
                if (job.progressMessage) setStep("assets", "running", job.progressMessage);
              });
            } catch (e) {
              await switchImageBackendIfNeeded(e);
            }
          }
          for (let round = 0; round < 2; round++) {
            assets = (await fetchDramaForgeAssets(pid)).data;
            const missing = assets.filter((a) => !a.referenceImageUrl?.trim());
            if (missing.length === 0) break;
            for (const asset of missing) {
              throwIfCancelled();
              setStep("assets", "running", t("quickEpisode.assetRetrying", { name: asset.name }));
              try {
                const retryJob = await regenerateDramaForgeAssetDesign(pid, asset.id, apiKey);
                await waitJob(pid, retryJob.data.id, (job) => {
                  if (job.progressMessage) setStep("assets", "running", job.progressMessage);
                });
              } catch (e) {
                await switchImageBackendIfNeeded(e);
              }
            }
          }
        }
        assets = (await fetchDramaForgeAssets(pid)).data;
        const stillMissing = assets.filter((a) => !a.referenceImageUrl?.trim());
        if (stillMissing.length > 0) {
          throw new Error(
            t("quickEpisode.assetDesignFailed", {
              names: stillMissing.map((a) => a.name).join("、"),
            }),
          );
        }
        await lockDramaForgeAssets(pid).catch(() => {});
        setStep("assets", "done", t("quickEpisode.assetsDone"));
      }

      if (!episodeId) {
        throw new Error(t("quickEpisode.noEpisodeGenerated"));
      }

      // ④ 出成片（含音色补齐）
      if (!(probe?.videoDone)) {
        currentStep = "video";
        setStep("video", "running", t("quickEpisode.voicesPreparing"));
        const shotsForVoice = (await fetchDramaForgeShots(pid, episodeId)).data;
        const dialogueCharNames = new Set<string>();
        for (const shot of shotsForVoice) {
          const dialogue = shot.dialogue?.trim();
          if (!dialogue) continue;
          for (const name of shot.characterRefs ?? []) {
            if (name?.trim()) dialogueCharNames.add(name.trim());
          }
        }
        let assets = (await fetchDramaForgeAssets(pid)).data;
        const voiceTargets = assets.filter(
          (a) =>
            a.type === "character"
            && !a.voiceSampleUrl?.trim()
            && (dialogueCharNames.size === 0
              ? shotsForVoice.some((s) => s.dialogue?.trim())
              : dialogueCharNames.has(a.name)),
        );
        for (const asset of voiceTargets) {
          throwIfCancelled();
          setStep("video", "running", t("quickEpisode.voiceGenerating", { name: asset.name }));
          try {
            await generateDramaForgeCharacterVoice(pid, asset.id, apiKey);
          } catch (e) {
            const msg = e instanceof Error ? e.message : String(e);
            throw new Error(t("quickEpisode.voiceFailed", { name: asset.name, error: msg }));
          }
        }
        // 再确认一遍：有对白角色仍缺音色则中断，不进入出片
        assets = (await fetchDramaForgeAssets(pid)).data;
        const stillMissingVoice = voiceTargets
          .map((target) => assets.find((a) => a.id === target.id) ?? target)
          .filter((a) => !a.voiceSampleUrl?.trim());
        if (stillMissingVoice.length > 0) {
          throw new Error(
            t("quickEpisode.voiceMissing", {
              names: stillMissingVoice.map((a) => a.name).join("、"),
            }),
          );
        }

        const pendingShots = shotsForVoice.filter((s) => s.status !== "video_done");
        if (pendingShots.length > 0) {
          setStep("video", "running", t("quickEpisode.videoStarting"));
          try {
            await generateDramaForgeVideos(pid, episodeId, apiKey);
          } catch (e) {
            const msg = e instanceof Error ? e.message : String(e);
            // 续跑时可能刚好全部完成
            if (!msg.includes("没有待生成视频的镜头")) throw e;
          }
          await syncDramaForgeVideos(pid, episodeId, apiKey).catch(() => {});
          await waitVideos(pid, episodeId);
        }
        setStep("video", "done", t("quickEpisode.videoDone"));
      }

      // ⑤ 合成
      if (!(probe?.composeDone)) {
        currentStep = "compose";
        setStep("compose", "running", t("quickEpisode.composeRunning"));
        const composeStartedAt = Date.now();
        await composeDramaForgeEpisode(pid, episodeId);
        const composition = await waitCompose(pid, episodeId, composeStartedAt);
        const resolvedOutput = resolveComposeOutputUrl(composition.outputUrl);
        setOutputUrl(resolvedOutput);
        setStep("compose", "done", t("quickEpisode.composeDone"));
        if (currentRunId) {
          persistRunSnapshot({
            id: currentRunId,
            projectId: pid,
            status: "completed",
            outputUrl: resolvedOutput,
            errorMessage: null,
            stepStates: {
              config: "done",
              script: "done",
              assets: "done",
              video: "done",
              compose: "done",
            },
          });
        }
      }
    } catch (e) {
      const message = e instanceof Error ? e.message : String(e);
      setStep(currentStep, "failed", message);
      setError(message);
      const failedProjectId = currentProjectId;
      if (currentRunId && failedProjectId) {
        persistRunSnapshot({
          id: currentRunId,
          projectId: failedProjectId,
          status: "failed",
          errorMessage: message,
          currentStep,
        });
      }
    } finally {
      setRunning(false);
    }
  }

  function handleStart() {
    void runPipeline({ resume: false });
  }

  const stepLabels: Record<StepKey, string> = {
    config: t("quickEpisode.stepConfig"),
    script: t("quickEpisode.stepScript"),
    assets: t("quickEpisode.stepAssets"),
    video: t("quickEpisode.stepVideo"),
    compose: t("quickEpisode.stepCompose"),
  };

  const started = STEP_ORDER.some((k) => stepStates[k] !== "idle");
  const showProgress = started || Boolean(activeRunId && projectId);
  const activeRun = runs.find((r) => r.id === activeRunId) ?? activeRunRef.current;
  const canContinue = Boolean(
    !running
    && user
    && apiKey
    && projectId
    && activeRun
    && activeRun.status !== "completed"
    && STEP_ORDER.some((k) => (activeRun.stepStates?.[k] ?? stepStates[k]) !== "done"),
  );

  return (
    <div className="pf-shell-main max-w-3xl space-y-6">
      <header className="pf-page-head">
        <div className="mb-2 flex items-center gap-2">
          <span className="flex h-8 w-8 items-center justify-center rounded-lg bg-[#7c3aed]">
            <Sparkles className="h-4 w-4 text-[#17131f]" />
          </span>
          <h1 className="pf-page-title !mt-0 text-xl">{t("quickEpisode.title")}</h1>
        </div>
        <p className="pf-page-desc !mt-2">{t("quickEpisode.subtitle")}</p>
      </header>

      <div className="pf-panel pf-panel-pad">
        <label className="mb-2 block text-sm font-medium text-[#17131f]">
          {t("quickEpisode.ideaLabel")}
        </label>
        <textarea
          value={idea}
          onChange={(e) => setIdea(e.target.value)}
          disabled={running}
          rows={4}
          placeholder={t("quickEpisode.ideaPlaceholder")}
          className="w-full resize-y rounded-xl border border-[#dfe2e6] bg-[#f8f7fc] px-3 py-2.5 text-sm text-[#17131f] outline-none transition focus:border-[#7c3aed] focus:ring-2 focus:ring-[#7c3aed]/15 disabled:opacity-60"
        />
        <div className="mt-3 flex flex-wrap items-center justify-between gap-3">
          <div className="flex items-center gap-2">
            {ASPECT_OPTIONS.map((opt) => (
              <button
                key={opt.value}
                type="button"
                disabled={running}
                onClick={() => setAspectRatio(opt.value)}
                className={`rounded-full border px-3 py-1.5 text-xs transition disabled:opacity-60 ${
                  aspectRatio === opt.value
                    ? "border-[#7c3aed] bg-[#f4ffd6] text-[#17131f]"
                    : "border-[#e5e7eb] text-[#62666d] hover:text-[#17131f]"
                }`}
              >
                {t(opt.labelKey)}
              </button>
            ))}
          </div>
          <div className="flex flex-wrap items-center gap-2">
            <button
              type="button"
              disabled={running || !idea.trim() || !user || !apiKey}
              onClick={handleStart}
              className="inline-flex items-center gap-2 rounded-xl bg-[#7c3aed] px-5 py-2.5 text-sm font-semibold text-[#17131f] transition hover:brightness-95 disabled:cursor-not-allowed disabled:opacity-50"
            >
              {running ? <Loader2 className="h-4 w-4 animate-spin" /> : <Sparkles className="h-4 w-4" />}
              {running ? t("quickEpisode.generating") : t("quickEpisode.start")}
            </button>
          </div>
        </div>
        {!user ? (
          <p className="mt-3 text-xs text-[#e11d48]">
            {t("quickEpisode.loginRequired")}{" "}
            <Link href="/login" className="text-[#7c3aed] underline">
              {t("quickEpisode.goLogin")}
            </Link>
          </p>
        ) : !apiKey ? (
          <p className="mt-3 text-xs text-[#e11d48]">
            {t("quickEpisode.apiKeyRequired")}{" "}
            <Link href="/creator?mode=settings" className="text-[#7c3aed] underline">
              {t("quickEpisode.goSetApiKey")}
            </Link>
          </p>
        ) : null}
      </div>

      {/* 操作记录：localStorage 持久化，刷新仍可见 */}
      <div className="mt-5 rounded-2xl border border-[#e5e7eb] bg-white p-5">
        <div className="mb-3 flex items-center justify-between gap-2">
          <h2 className="text-sm font-semibold text-[#17131f]">{t("quickEpisode.runsTitle")}</h2>
        </div>
        {runs.length === 0 ? (
          <p className="text-xs text-[#9aa0a6]">{t("quickEpisode.runsEmpty")}</p>
        ) : (
          <div className="max-h-56 space-y-2 overflow-auto">
            {runs.map((run) => {
              const selected = run.id === activeRunId;
              const statusColor =
                run.status === "completed"
                  ? "text-[#7c3aed]"
                  : run.status === "failed"
                    ? "text-[#e11d48]"
                    : "text-[#7c3aed]";
              return (
                <div
                  key={run.id}
                  className={`rounded-xl border px-3 py-2.5 text-xs transition ${
                    selected
                      ? "border-[#7c3aed] bg-[#f4ffd6]"
                      : "border-[#e5e7eb] bg-[#f8f7fc] hover:border-[#cbd0d6]"
                  }`}
                >
                  <div className="flex items-start justify-between gap-2">
                    <button
                      type="button"
                      disabled={running}
                      onClick={() => selectRun(run)}
                      className="min-w-0 flex-1 text-left disabled:opacity-60"
                    >
                      <div className="flex flex-wrap items-center gap-1.5">
                        <span className="font-medium text-[#17131f]">
                          {run.projectName || t("quickEpisode.defaultProjectName")}
                        </span>
                        <span className={statusColor}>{runStatusLabel(run.status, t)}</span>
                      </div>
                      <p className="mt-0.5 line-clamp-2 text-[#62666d]">{run.idea}</p>
                      <p className="mt-1 text-[10px] text-[#9aa0a6]">
                        {formatJobTime(run.updatedAt)}
                        {run.projectId ? ` · ${run.projectId.slice(0, 8)}…` : ""}
                      </p>
                    </button>
                    <div className="flex shrink-0 flex-col items-end gap-1">
                      <Link
                        href={`/studio/${run.projectId}`}
                        className="text-[10px] text-[#7c3aed] underline"
                        onClick={(e) => e.stopPropagation()}
                      >
                        {t("quickEpisode.openStudio")}
                      </Link>
                      <button
                        type="button"
                        disabled={running}
                        onClick={() => removeRun(run.id)}
                        className="text-[10px] text-[#9aa0a6] underline hover:text-[#e11d48] disabled:opacity-50"
                      >
                        {t("quickEpisode.runRemove")}
                      </button>
                    </div>
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </div>

      {showProgress && (
        <div className="mt-5 rounded-2xl border border-[#e5e7eb] bg-white p-5">
          <h2 className="mb-4 text-sm font-semibold text-[#17131f]">{t("quickEpisode.progressTitle")}</h2>
          <ol className="space-y-3">
            {STEP_ORDER.map((key, idx) => {
              const state = stepStates[key];
              return (
                <li key={key} className="flex items-start gap-3">
                  <span className="mt-0.5 shrink-0">
                    {state === "done" ? (
                      <CheckCircle2 className="h-5 w-5 text-[#7c3aed]" />
                    ) : state === "running" ? (
                      <Loader2 className="h-5 w-5 animate-spin text-[#7c3aed]" />
                    ) : state === "failed" ? (
                      <XCircle className="h-5 w-5 text-[#e11d48]" />
                    ) : (
                      <CircleDashed className="h-5 w-5 text-[#cbd0d6]" />
                    )}
                  </span>
                  <div className="min-w-0 flex-1">
                    <p
                      className={`text-sm font-medium ${
                        state === "idle" ? "text-[#9aa0a6]" : "text-[#17131f]"
                      }`}
                    >
                      {idx + 1}. {stepLabels[key]}
                    </p>
                    {stepMessages[key] && (
                      <p
                        className={`mt-0.5 break-words text-xs ${
                          state === "failed" ? "text-[#e11d48]" : "text-[#62666d]"
                        }`}
                      >
                        {stepMessages[key]}
                      </p>
                    )}
                  </div>
                </li>
              );
            })}
          </ol>

          {(error || canContinue) && projectId && (
            <div className="mt-4 space-y-2 rounded-xl border border-[#e11d48]/30 bg-[#e11d48]/5 px-3 py-2.5 text-xs text-[#62666d]">
              <p>
                {canContinue ? t("quickEpisode.continueHint") : t("quickEpisode.failedHint")}{" "}
                <Link href={`/studio/${projectId}`} className="text-[#7c3aed] underline">
                  {t("quickEpisode.openStudio")}
                </Link>
              </p>
            </div>
          )}

          {outputUrl && (
            <div className="mt-5 space-y-3">
              <h3 className="text-sm font-semibold text-[#17131f]">{t("quickEpisode.resultTitle")}</h3>
              <video src={outputUrl} controls className="w-full rounded-xl bg-black" />
              <div className="flex flex-wrap items-center gap-4 text-xs">
                <a href={outputUrl} target="_blank" rel="noreferrer" className="text-[#7c3aed] underline">
                  {t("quickEpisode.openOrDownload")}
                </a>
                {projectId && (
                  <Link href={`/studio/${projectId}`} className="text-[#7c3aed] underline">
                    {t("quickEpisode.openStudio")}
                  </Link>
                )}
              </div>
            </div>
          )}
        </div>
      )}

      <div className="mt-5 rounded-2xl border border-[#e5e7eb] bg-[#f8f7fc] p-4 text-xs leading-relaxed text-[#62666d]">
        <p className="mb-1 font-medium text-[#17131f]">{t("quickEpisode.howItWorksTitle")}</p>
        <p>{t("quickEpisode.howItWorks")}</p>
      </div>
    </div>
  );
}
