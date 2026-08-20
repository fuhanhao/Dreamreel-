"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import {
  ArrowRight,
  Clock3,
  Coins,
  Film,
  FolderKanban,
  Sparkles,
  Upload,
  WandSparkles,
} from "lucide-react";
import {
  createImageGeneration,
  createTextGeneration,
  createVideoGeneration,
  deleteGeneration,
  fetchCreationStats,
  fetchGeneration,
  fetchGenerations,
  fetchImageModels,
  fetchTextModels,
  fetchVideoGeneration,
  fetchVideoModels,
  uploadMedia,
} from "@/lib/api";
import { getArkApiKey, getTokenfreeApiKey, resolveArkApiKey, resolveTokenfreeApiKey } from "@/lib/api-key";
import { getAuthToken, getAuthUser } from "@/lib/auth";
import { loginPath } from "@/lib/auth-redirect";
import { pickDefaultModel } from "@/lib/model-picker";
import { useAuth } from "@/components/auth/auth-provider";
import { useT } from "@/i18n/locale-provider";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { ApiKeySettings } from "./api-key-settings";
import { QuickStartPanel } from "./quick-start-panel";
import type { QuickStartGuideId } from "./quick-start-content";
import { type GenerationFormValues } from "./generation-form";
import { CreationCard } from "./creation-card";
import { CreationDetailModal } from "./creation-detail-modal";
import { CreationsGalleryModal } from "./creations-gallery-modal";
import { mapCreationRecord, type CreationItem, type CreationMode, getCreationTitle } from "./creation-types";
import { useConfirmDialog } from "@/hooks/use-confirm-dialog";
import { useCurrentProject } from "@/components/shell/current-project";
import type { AspectRatio, CreationStats, MediaQuality } from "@dreamreel/shared-types";

type CatalogTab = "all" | "video" | "image" | "prompt";

const HOME_PREVIEW_COUNT = 8;

const RATIO_OPTIONS: { value: AspectRatio; label: string }[] = [
  { value: "16:9", label: "16:9" },
  { value: "9:16", label: "9:16" },
  { value: "1:1", label: "1:1" },
  { value: "4:3", label: "4:3" },
  { value: "3:4", label: "3:4" },
];

const QUALITY_OPTIONS: { value: MediaQuality; label: string }[] = [
  { value: "480p", label: "480p" },
  { value: "720p", label: "720p" },
  { value: "1080p", label: "1080p" },
];

const DURATION_OPTIONS = [2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15];

type CreationFilter = "all" | "running" | "done" | "draft";

export function CreatorPage() {
  const { user, loading: authLoading } = useAuth();
  const t = useT();
  const { confirm, alert, ConfirmDialog } = useConfirmDialog("dark");
  const router = useRouter();
  const searchParams = useSearchParams();
  const { currentProjectId } = useCurrentProject();
  const [apiKey, setApiKey] = useState<string | null>(null);
  const [arkApiKey, setArkApiKey] = useState<string | null>(null);
  const [settingsOpen, setSettingsOpen] = useState(false);
  const [form, setForm] = useState<GenerationFormValues>({
    mode: "prompt",
    videoSubMode: "text-to-video",
    imageSubMode: "text-to-image",
    prompt: "",
    model: "",
    ratio: "16:9",
    quality: "720p",
    seconds: 5,
    strength: 0.65,
    referenceUrl: "",
  });
  const [models, setModels] = useState<string[]>([]);
  const [loading, setLoading] = useState(false);
  const [loadingModels, setLoadingModels] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [results, setResults] = useState<CreationItem[]>([]);
  const [selectedItem, setSelectedItem] = useState<CreationItem | null>(null);
  const [galleryOpen, setGalleryOpen] = useState(false);
  const [creationFilter, setCreationFilter] = useState<CreationFilter>("all");
  const [catalogTab, setCatalogTab] = useState<CatalogTab>("all");
  const [uploading, setUploading] = useState(false);
  const [creationStats, setCreationStats] = useState<CreationStats | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const pollTimers = useRef<Map<string, ReturnType<typeof setInterval>>>(new Map());
  const loadCreationStatsRef = useRef<() => Promise<void>>(async () => {});

  function actionLabel(form: GenerationFormValues) {
    if (form.mode === "video") {
      if (form.videoSubMode === "image-to-video") return t("creator.startImageToVideo");
      if (form.videoSubMode === "video-to-video") return t("creator.startVideoToVideo");
      return t("creator.startTextToVideo");
    }
    if (form.mode === "image") {
      return form.imageSubMode === "image-to-image"
        ? t("creator.startImageToImage")
        : t("creator.startTextToImage");
    }
    return t("creator.startPromptOptimize");
  }

  const handleCreationUpdated = useCallback((fresh: CreationItem) => {
    setResults((prev) => prev.map((r) => (r.id === fresh.id ? { ...r, ...fresh } : r)));
    setSelectedItem((prev) => (prev?.id === fresh.id ? { ...prev, ...fresh } : prev));
  }, []);

  const patchCreation = useCallback((jobId: string, patch: Partial<CreationItem>) => {
    const apply = (prev: CreationItem): CreationItem => {
      if (
        prev.status === patch.status &&
        prev.progress === patch.progress &&
        prev.outputUrl === patch.outputUrl &&
        prev.outputText === patch.outputText &&
        prev.errorMessage === patch.errorMessage
      ) {
        return prev;
      }
      return { ...prev, ...patch };
    };
    setResults((prev) =>
      prev.map((r) => (r.id === jobId ? apply(r) : r)),
    );
    setSelectedItem((prev) => (prev?.id === jobId ? apply(prev) : prev));
  }, []);

  const startJobPolling = useCallback((jobId: string, mode: CreationMode) => {
    if (pollTimers.current.has(jobId)) return;

    const stop = () => {
      const existing = pollTimers.current.get(jobId);
      if (existing) {
        clearInterval(existing);
        pollTimers.current.delete(jobId);
      }
    };

    const tick = async () => {
      try {
        if (mode === "video") {
          const key = resolveArkApiKey(getAuthUser()) ?? getArkApiKey();
          const res = await fetchVideoGeneration(jobId, key);
          const job = res.data;
          patchCreation(jobId, {
            status: job.status,
            progress: job.progress,
            outputUrl: job.outputUrl,
            errorMessage: job.errorMessage,
          });
          if ((job.status === "COMPLETED" && job.outputUrl) || job.status === "FAILED") {
            stop();
            if (job.status === "COMPLETED") {
              void loadCreationStatsRef.current();
            }
          }
          return;
        }

        const res = await fetchGeneration(jobId);
        const fresh = mapCreationRecord(res.data);
        patchCreation(jobId, {
          status: fresh.status,
          progress: fresh.progress,
          outputUrl: fresh.outputUrl,
          outputText: fresh.outputText,
          errorMessage: fresh.errorMessage,
        });
        if (fresh.status === "COMPLETED" || fresh.status === "FAILED") {
          stop();
          if (fresh.status === "COMPLETED") {
            void loadCreationStatsRef.current();
          }
        }
      } catch {
        stop();
      }
    };

    void tick();
    const timer = setInterval(() => void tick(), 5000);
    pollTimers.current.set(jobId, timer);
  }, [patchCreation]);

  const startJobPollingRef = useRef(startJobPolling);
  useEffect(() => {
    startJobPollingRef.current = startJobPolling;
  }, [startJobPolling]);

  const loadHistory = useCallback(async () => {
    try {
      const res = await fetchGenerations(0, 30);
      const items = res.data.items.map(mapCreationRecord);
      setResults(items);
      for (const entry of items) {
        if (entry.status === "QUEUED" || entry.status === "IN_PROGRESS") {
          startJobPollingRef.current(entry.id, entry.mode);
        }
      }
    } catch {
      // ignore
    }
  }, []);

  const loadCreationStats = useCallback(async () => {
    try {
      const res = await fetchCreationStats();
      setCreationStats(res.data);
    } catch {
      // ignore
    }
  }, []);
  useEffect(() => {
    loadCreationStatsRef.current = loadCreationStats;
  }, [loadCreationStats]);

  useEffect(() => {
    const timer = window.setTimeout(() => {
      const tokenfree = resolveTokenfreeApiKey(user);
      const ark = resolveArkApiKey(user);
      setApiKey(tokenfree);
      setArkApiKey(ark);
      if (user && !tokenfree && !ark) {
        setSettingsOpen(true);
      }
    }, 0);
    return () => window.clearTimeout(timer);
  }, [user]);

  useEffect(() => {
    if (!user?.id) {
      const timer = window.setTimeout(() => {
        setResults([]);
        setCreationStats(null);
      }, 0);
      return () => window.clearTimeout(timer);
    }
    const timer = window.setTimeout(() => {
      void loadHistory();
      void loadCreationStats();
    }, 0);
    return () => window.clearTimeout(timer);
  }, [user?.id, loadHistory, loadCreationStats]);

  useEffect(() => {
    if (searchParams.get("openApiKey") === "1") {
      const timer = window.setTimeout(() => setSettingsOpen(true), 0);
      return () => window.clearTimeout(timer);
    }
  }, [searchParams]);

  useEffect(() => {
    const requestedMode = searchParams.get("mode");
    if (requestedMode !== "video" && requestedMode !== "image" && requestedMode !== "prompt") return;

    const requestedVideoSubMode = searchParams.get("videoSubMode");
    const videoSubMode =
      requestedVideoSubMode === "image-to-video" || requestedVideoSubMode === "video-to-video"
        ? requestedVideoSubMode
        : "text-to-video";
    const imageSubMode =
      searchParams.get("imageSubMode") === "image-to-image" ? "image-to-image" : "text-to-image";

    const timer = window.setTimeout(() => {
      setForm((current) => {
        if (
          current.mode === requestedMode
          && current.videoSubMode === videoSubMode
          && current.imageSubMode === imageSubMode
        ) {
          return current;
        }
        return {
          ...current,
          mode: requestedMode,
          videoSubMode,
          imageSubMode,
          referenceUrl: "",
        };
      });
    }, 0);
    return () => window.clearTimeout(timer);
  }, [searchParams]);

  useEffect(() => {
    if (window.location.hash !== "#creation-workbench") return;
    const frame = window.requestAnimationFrame(() => {
      document.getElementById("creation-workbench")?.scrollIntoView({ block: "start" });
    });
    return () => window.cancelAnimationFrame(frame);
  }, [searchParams]);

  const initialGuideId = useMemo((): QuickStartGuideId | null => {
    const raw = searchParams.get("guide");
    if (raw === "tutorial" || raw === "guide" || raw === "promptTips") return raw;
    return null;
  }, [searchParams]);

  const handleDeleteCreation = useCallback(async (item: CreationItem) => {
    const title = getCreationTitle(item.prompt);
    const ok = await confirm({
      title: t("creator.deleteCreationTitle"),
      message: t("creator.deleteCreationMessage", { title }),
      confirmLabel: t("common.delete"),
      variant: "danger",
    });
    if (!ok) return;

    try {
      await deleteGeneration(item.id);
      const timer = pollTimers.current.get(item.id);
      if (timer) {
        clearInterval(timer);
        pollTimers.current.delete(item.id);
      }
      setResults((prev) => prev.filter((r) => r.id !== item.id));
      setSelectedItem((current) => (current?.id === item.id ? null : current));
    } catch (e) {
      await alert(e instanceof Error ? e.message : t("creator.deleteFailed"), t("creator.deleteFailed"));
    }
  }, [confirm, alert, t]);

  const updateForm = useCallback((patch: Partial<GenerationFormValues>) => {
    setForm((prev) => ({ ...prev, ...patch }));
  }, []);

  const loadModels = useCallback(
    async (currentMode: CreationMode, key: string) => {
      setLoadingModels(true);
      setError(null);
      try {
        let list: string[] = [];
        if (currentMode === "video") {
          const res = await fetchVideoModels(key);
          list = res.data.map((m) => m.id);
        } else if (currentMode === "image") {
          const res = await fetchImageModels(key);
          list = res.data.map((m) => m.id);
        } else {
          const res = await fetchTextModels(key);
          list = res.data.map((m) => m.id);
        }
        setModels(list);
        setForm((prev) => ({
          ...prev,
          model: list.includes(prev.model) ? prev.model : pickDefaultModel(list, currentMode),
        }));
      } catch (e) {
        setModels([]);
        setForm((prev) => ({ ...prev, model: "" }));
        setError(e instanceof Error ? e.message : t("creator.loadModelsFailed"));
      } finally {
        setLoadingModels(false);
      }
    },
    [t],
  );

  useEffect(() => {
    if (!user) return;
    const timer = window.setTimeout(() => {
      if (form.mode === "video") {
        if (arkApiKey) void loadModels(form.mode, arkApiKey);
      } else if (apiKey) {
        void loadModels(form.mode, apiKey);
      }
    }, 0);
    return () => window.clearTimeout(timer);
  }, [user, apiKey, arkApiKey, form.mode, loadModels]);

  useEffect(() => {
    const timers = pollTimers.current;
    return () => {
      timers.forEach((timer) => clearInterval(timer));
      timers.clear();
    };
  }, []);

  async function handleGenerate() {
    if (authLoading) return;

    if (!user || !getAuthToken()) {
      router.push(loginPath("/creator"));
      return;
    }
    if (form.mode === "video") {
      if (!arkApiKey) {
        setError(t("creator.configureArkFirst"));
        setSettingsOpen(true);
        return;
      }
    } else if (!apiKey) {
      setError(t("creator.configureTokenfreeFirst"));
      setSettingsOpen(true);
      return;
    }
    if (!form.prompt.trim()) {
      setError(t("creator.enterPrompt"));
      return;
    }
    if (!form.model) {
      setError(t("creator.selectModelRequired"));
      return;
    }
    if (form.mode === "video" && form.videoSubMode === "image-to-video" && !form.referenceUrl) {
      setError(t("creator.imageRefRequired"));
      return;
    }
    if (form.mode === "video" && form.videoSubMode === "video-to-video" && !form.referenceUrl) {
      setError(t("creator.videoRefRequired"));
      return;
    }
    if (form.mode === "image" && form.imageSubMode === "image-to-image" && !form.referenceUrl) {
      setError(t("creator.imageToImageRefRequired"));
      return;
    }

    setLoading(true);
    setError(null);

    try {
      if (form.mode === "video") {
        const res = await createVideoGeneration(
          {
            model: form.model,
            prompt: form.prompt.trim(),
            seconds: form.seconds,
            ratio: form.ratio,
            quality: form.quality,
            mode: form.videoSubMode,
            imageUrl: form.videoSubMode === "image-to-video" ? form.referenceUrl : undefined,
            videoUrl: form.videoSubMode === "video-to-video" ? form.referenceUrl : undefined,
          },
          arkApiKey
        );
        const job = res.data;
        setResults((prev) => [
          {
            id: job.id,
            mode: form.mode,
            prompt: form.prompt.trim(),
            model: form.model,
            status: job.status,
            progress: job.progress,
            outputUrl: job.outputUrl,
            errorMessage: job.errorMessage,
            generationMode: form.videoSubMode,
            referenceImageUrl: form.videoSubMode === "image-to-video" ? form.referenceUrl : undefined,
            referenceVideoUrl: form.videoSubMode === "video-to-video" ? form.referenceUrl : undefined,
            ratio: form.ratio,
            createdAt: job.createdAt,
          },
          ...prev,
        ]);
        if (job.status !== "COMPLETED" && job.status !== "FAILED") {
          startJobPolling(job.id, "video");
        }
      } else if (form.mode === "image") {
        const res = await createImageGeneration(
          {
            model: form.model,
            prompt: form.prompt.trim(),
            ratio: form.ratio,
            quality: form.quality,
            mode: form.imageSubMode,
            imageUrl: form.imageSubMode === "image-to-image" ? form.referenceUrl : undefined,
            strength: form.imageSubMode === "image-to-image" ? form.strength : undefined,
          },
          apiKey
        );
        const job = res.data;
        setResults((prev) => [
          {
            id: job.id,
            mode: form.mode,
            prompt: form.prompt.trim(),
            model: form.model,
            status: job.status,
            outputUrl: job.outputUrl,
            errorMessage: job.errorMessage,
            generationMode: form.imageSubMode,
            referenceImageUrl: form.imageSubMode === "image-to-image" ? form.referenceUrl : undefined,
            ratio: form.ratio,
            strength: form.imageSubMode === "image-to-image" ? form.strength : undefined,
            createdAt: job.createdAt,
          },
          ...prev,
        ]);
        if (job.status !== "COMPLETED" && job.status !== "FAILED") {
          startJobPolling(job.id, "image");
        }
      } else {
        const res = await createTextGeneration(
          { model: form.model, prompt: form.prompt.trim(), nodeType: "prompt" },
          apiKey
        );
        const job = res.data;
        setResults((prev) => [
          {
            id: job.id,
            mode: form.mode,
            prompt: form.prompt.trim(),
            model: form.model,
            status: job.status,
            outputText: job.outputText,
            errorMessage: job.errorMessage,
            createdAt: job.createdAt,
          },
          ...prev,
        ]);
        if (job.status !== "COMPLETED" && job.status !== "FAILED") {
          startJobPolling(job.id, "prompt");
        }
      }
    } catch (e) {
      const message = e instanceof Error ? e.message : t("creator.generateFailed");
      setError(message);
    } finally {
      setLoading(false);
    }
  }

  const displayName = user?.displayName ?? t("common.director");

  function closeApiKeySettings() {
    setSettingsOpen(false);
    if (searchParams.get("openApiKey") === "1") {
      const params = new URLSearchParams(searchParams.toString());
      params.delete("openApiKey");
      router.replace(params.size > 0 ? `/creator?${params.toString()}` : "/creator", { scroll: false });
    }
  }

  function requireAuthFor(href: string) {
    if (user) {
      router.push(href);
      return;
    }
    router.push(loginPath(href));
  }
  const filteredResults = results.filter((item) => {
    if (creationFilter === "running") return item.status === "IN_PROGRESS" || item.status === "QUEUED";
    if (creationFilter === "done") return item.status === "COMPLETED";
    if (creationFilter === "draft") return item.status === "FAILED";
    return true;
  });
  const preview = filteredResults.slice(0, HOME_PREVIEW_COUNT);

  const formatDelta = useCallback((percent: number) => {
    const abs = Math.abs(percent);
    return percent >= 0
      ? t("creator.statDeltaUp", { value: `${abs}%` })
      : t("creator.statDeltaDown", { value: `${abs}%` });
  }, [t]);

  const todayStats = useMemo(() => {
    const stats = creationStats ?? {
      projectCount: 0,
      projectDeltaPercent: 0,
      renderHours: 0,
      renderDeltaPercent: 0,
      videoCount: 0,
      videoDeltaPercent: 0,
      credits: 0,
      creditsDeltaPercent: 0,
    };
    return [
      {
        label: t("creator.statProjects"),
        value: String(stats.projectCount),
        delta: formatDelta(stats.projectDeltaPercent),
        up: stats.projectDeltaPercent >= 0,
      },
      {
        label: t("creator.statRender"),
        value: `${Number(stats.renderHours).toFixed(1)} h`,
        delta: formatDelta(stats.renderDeltaPercent),
        up: stats.renderDeltaPercent >= 0,
      },
      {
        label: t("creator.statVideos"),
        value: String(stats.videoCount),
        delta: formatDelta(stats.videoDeltaPercent),
        up: stats.videoDeltaPercent >= 0,
      },
      {
        label: t("creator.statCredits"),
        value: Number(stats.credits).toLocaleString(),
        delta: formatDelta(stats.creditsDeltaPercent),
        up: stats.creditsDeltaPercent >= 0,
      },
    ];
  }, [creationStats, t, formatDelta]);

  const needsImageReference =
    form.mode === "image"
      ? form.imageSubMode === "image-to-image"
      : form.videoSubMode === "image-to-video";
  const needsVideoReference = form.mode === "video" && form.videoSubMode === "video-to-video";
  const needsReference = needsImageReference || needsVideoReference;

  async function handleUpload(file: File) {
    setUploading(true);
    setError(null);
    try {
      const res = await uploadMedia(file);
      updateForm({ referenceUrl: res.data.url });
    } catch (e) {
      setError(e instanceof Error ? e.message : t("creator.uploadFailed"));
    } finally {
      setUploading(false);
    }
  }

  function scrollToWorkbench() {
    document.getElementById("creation-workbench")?.scrollIntoView({ behavior: "smooth", block: "start" });
  }

  function selectGenerationFeature(
    mode: GenerationFormValues["mode"],
    patch: Partial<GenerationFormValues> = {},
  ) {
    updateForm({ mode, referenceUrl: "", ...patch });
    setCatalogTab(mode === "prompt" ? "prompt" : mode);
    scrollToWorkbench();
  }

  const catalogPills: { id: CatalogTab; label: string }[] = [
    { id: "all", label: t("creator.filterAll") },
    { id: "video", label: t("creator.modeVideo") },
    { id: "image", label: t("creator.modeImage") },
    { id: "prompt", label: t("creator.modePrompt") },
  ];

  const featureCards = [
    {
      id: "text-to-video",
      catalog: "video" as CatalogTab,
      title: t("creator.featureTextToVideo"),
      desc: t("creator.featureTextToVideoDesc"),
      selected: form.mode === "video" && form.videoSubMode === "text-to-video",
      onClick: () => selectGenerationFeature("video", { videoSubMode: "text-to-video" }),
    },
    {
      id: "image-to-video",
      catalog: "video" as CatalogTab,
      title: t("creator.featureImageToVideo"),
      desc: t("creator.featureImageToVideoDesc"),
      selected: form.mode === "video" && form.videoSubMode === "image-to-video",
      onClick: () => selectGenerationFeature("video", { videoSubMode: "image-to-video" }),
    },
    {
      id: "video-to-video",
      catalog: "video" as CatalogTab,
      title: t("creator.featureVideoToVideo"),
      desc: t("creator.featureVideoToVideoDesc"),
      selected: form.mode === "video" && form.videoSubMode === "video-to-video",
      onClick: () => selectGenerationFeature("video", { videoSubMode: "video-to-video" }),
    },
    {
      id: "image",
      catalog: "image" as CatalogTab,
      title: t("creator.featureImage"),
      desc: t("creator.featureImageDesc"),
      selected: form.mode === "image",
      onClick: () => selectGenerationFeature("image", { imageSubMode: "text-to-image" }),
    },
    {
      id: "prompt",
      catalog: "prompt" as CatalogTab,
      title: t("creator.featurePrompt"),
      desc: t("creator.featurePromptDesc"),
      selected: form.mode === "prompt",
      onClick: () => selectGenerationFeature("prompt"),
    },
    {
      id: "drama",
      catalog: "all" as CatalogTab,
      title: t("creator.featureDrama"),
      desc: t("creator.featureDramaDesc"),
      selected: false,
      onClick: () => requireAuthFor("/projects?entry=dramaforge"),
    },
    {
      id: "canvas",
      catalog: "all" as CatalogTab,
      title: t("creator.featureCanvas"),
      desc: t("creator.featureCanvasDesc"),
      selected: false,
      onClick: () => requireAuthFor(currentProjectId ? `/studio/${currentProjectId}` : "/projects"),
    },
  ].filter((card) => catalogTab === "all" || card.catalog === catalogTab);

  const pillClass = (active: boolean) =>
    active
      ? "rounded-full border border-[#7c3aed] bg-[#f7ffe8] px-3.5 py-1.5 text-sm font-medium text-[#17131f]"
      : "rounded-full border border-[#e5e7eb] bg-white px-3.5 py-1.5 text-sm font-medium text-[#17131f] transition hover:border-[#cbd0d6] hover:bg-[#f8f7fc]";

  return (
    <div className="pf-shell-main space-y-8">
      <header className="pf-page-head">
        <div className="pf-page-head-row">
          <div>
            <p className="pf-page-eyebrow">
              AI CREATIVE COMMAND CENTER
            </p>
            <h1 className="pf-page-title">
              {user ? t("creator.welcomeBack", { name: displayName }) : t("creator.welcomeGuest")}
            </h1>
            <p className="pf-page-desc">
              {user ? t("creator.subtitleAuthed") : t("creator.subtitleGuest")}
            </p>
          </div>
          <div className="flex flex-wrap gap-2">
            <Button
              className="bg-[#7c3aed] text-[#17131f] shadow-none hover:bg-[#6d28d9]"
              onClick={() => requireAuthFor("/projects?entry=dramaforge")}
            >
              <Sparkles />
              {t("creator.newProject")}
            </Button>
            <Button variant="outline" className="bg-white" asChild>
              <a href="#creation-workbench">
                {actionLabel(form)}
                <ArrowRight />
              </a>
            </Button>
          </div>
        </div>
      </header>

      <section>
        <div className="mb-3 flex items-end justify-between gap-3">
          <h2 className="text-base font-semibold text-[#17131f]">{t("creator.statsTitle")}</h2>
        </div>
        <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
          {todayStats.map((stat, index) => {
            const icons = [FolderKanban, Clock3, Film, Coins];
            const Icon = icons[index];
            return (
              <div key={stat.label} className="rounded-xl border border-[#e5e7eb] bg-white p-4">
                <div className="flex items-start justify-between">
                  <p className="text-sm font-medium text-[#62666d]">{stat.label}</p>
                  <span className="grid size-9 place-items-center rounded-lg bg-[#f3e8ff] text-[#5b21b6]">
                    <Icon className="size-4" />
                  </span>
                </div>
                <p className="mt-2 text-2xl font-bold tracking-tight text-[#17131f]">{stat.value}</p>
                <p className={`mt-1 text-xs font-medium ${stat.up ? "text-[#7c3aed]" : "text-rose-600"}`}>
                  {stat.delta}
                </p>
              </div>
            );
          })}
        </div>
      </section>

      <section className="space-y-4">
        <div className="flex flex-wrap items-end justify-between gap-3">
          <div>
            <h2 className="text-xl font-bold tracking-tight text-[#17131f]">{t("creator.quickAccess")}</h2>
            <p className="mt-1 text-sm text-[#62666d]">{t("creator.workbenchHint")}</p>
          </div>
        </div>

        <div className="flex flex-wrap gap-2" role="tablist" aria-label={t("creator.quickAccess")}>
          {catalogPills.map((pill) => (
            <button
              key={pill.id}
              type="button"
              role="tab"
              aria-selected={catalogTab === pill.id}
              className={pillClass(catalogTab === pill.id)}
              onClick={() => setCatalogTab(pill.id)}
            >
              {pill.label}
            </button>
          ))}
        </div>

        <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
          {featureCards.map((card) => (
            <button
              key={card.id}
              type="button"
              onClick={card.onClick}
              className={`overflow-hidden rounded-xl border bg-white text-left transition hover:-translate-y-0.5 hover:border-[#cbd0d6] hover:shadow-sm ${
                card.selected
                  ? "border-[#7c3aed] shadow-[0_0_0_2px_rgba(182,255,0,0.35)]"
                  : "border-[#e5e7eb]"
              }`}
            >
              <div className="flex aspect-[16/9] items-end bg-[#17131f] p-4">
                <span className="rounded-md bg-[#7c3aed] px-2 py-0.5 text-[11px] font-semibold text-[#17131f]">
                  {card.title}
                </span>
              </div>
              <div className="space-y-1.5 p-4">
                <h3 className="text-sm font-semibold text-[#17131f]">{card.title}</h3>
                <p className="text-xs leading-relaxed text-[#62666d]">{card.desc}</p>
              </div>
            </button>
          ))}
        </div>
      </section>

      <section id="creation-workbench" className="scroll-mt-20 overflow-hidden rounded-xl border border-[#e5e7eb] bg-white">
        <div className="flex flex-wrap items-center justify-between gap-3 border-b border-[#e5e7eb] px-5 py-4 md:px-6">
          <div>
            <h2 className="flex items-center gap-2 text-xl font-bold text-[#17131f]">
              <WandSparkles className="size-5 text-[#7c3aed]" />
              {t("creator.workbench")}
            </h2>
            <p className="mt-1 text-sm text-[#62666d]">{t("creator.workbenchHint")}</p>
          </div>
          <div className="flex flex-wrap gap-2">
            {(
              [
                { id: "video" as const, label: t("creator.modeVideo") },
                { id: "image" as const, label: t("creator.modeImage") },
                { id: "prompt" as const, label: t("creator.modePrompt") },
              ]
            ).map((mode) => (
              <button
                key={mode.id}
                type="button"
                className={pillClass(form.mode === mode.id)}
                onClick={() => updateForm({ mode: mode.id, referenceUrl: "" })}
              >
                {mode.label}
              </button>
            ))}
          </div>
        </div>

        <div className="space-y-4 p-5 md:p-6">
          {form.mode === "video" && (
            <div className="flex flex-wrap gap-2">
              {(
                [
                  { id: "text-to-video" as const, label: t("creator.featureTextToVideo") },
                  { id: "image-to-video" as const, label: t("creator.featureImageToVideo") },
                  { id: "video-to-video" as const, label: t("creator.featureVideoToVideo") },
                ]
              ).map((item) => (
                <button
                  key={item.id}
                  type="button"
                  className={pillClass(form.videoSubMode === item.id)}
                  onClick={() => updateForm({ videoSubMode: item.id, referenceUrl: "" })}
                >
                  {item.label}
                </button>
              ))}
            </div>
          )}

          {form.mode === "image" && (
            <div className="flex flex-wrap gap-2">
              {(
                [
                  { id: "text-to-image" as const, label: t("creator.typeTextToImage") },
                  { id: "image-to-image" as const, label: t("creator.typeImageToImage") },
                ]
              ).map((item) => (
                <button
                  key={item.id}
                  type="button"
                  className={pillClass(form.imageSubMode === item.id)}
                  onClick={() => updateForm({ imageSubMode: item.id, referenceUrl: "" })}
                >
                  {item.label}
                </button>
              ))}
            </div>
          )}

          <div className="flex flex-wrap items-center gap-2">
            <Select value={form.model} onValueChange={(model) => updateForm({ model })} disabled={loadingModels || models.length === 0}>
              <SelectTrigger className="min-w-[190px] bg-[#f8f7fc]">
                <SelectValue placeholder={loadingModels ? t("creator.loadingModels") : t("creator.selectModel")} />
              </SelectTrigger>
              <SelectContent>
                {models.map((model) => (
                  <SelectItem key={model} value={model}>
                    {model}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
            {(form.mode === "video" || form.mode === "image") && (
              <>
                <Select value={form.ratio} onValueChange={(ratio) => updateForm({ ratio: ratio as AspectRatio })}>
                  <SelectTrigger className="w-[120px] bg-[#f8f7fc]">
                    <SelectValue placeholder={t("creator.aspectRatio")} />
                  </SelectTrigger>
                  <SelectContent>
                    {RATIO_OPTIONS.map((option) => (
                      <SelectItem key={option.value} value={option.value}>
                        {option.label}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
                <Select value={form.quality} onValueChange={(quality) => updateForm({ quality: quality as MediaQuality })}>
                  <SelectTrigger className="w-[120px] bg-[#f8f7fc]">
                    <SelectValue placeholder={t("creator.resolution")} />
                  </SelectTrigger>
                  <SelectContent>
                    {QUALITY_OPTIONS.map((option) => (
                      <SelectItem key={option.value} value={option.value}>
                        {option.label}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </>
            )}
            {form.mode === "video" && (
              <Select value={String(form.seconds)} onValueChange={(seconds) => updateForm({ seconds: Number(seconds) })}>
                <SelectTrigger className="w-[120px] bg-[#f8f7fc]">
                  <SelectValue placeholder={t("creator.duration")} />
                </SelectTrigger>
                <SelectContent>
                  {DURATION_OPTIONS.map((seconds) => (
                    <SelectItem key={seconds} value={String(seconds)}>
                      {t("creator.secondsUnit", { value: seconds })}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            )}
          </div>

          <textarea
            value={form.prompt}
            onChange={(event) => updateForm({ prompt: event.target.value.slice(0, 2000) })}
            rows={5}
            placeholder={t("creator.promptPlaceholder")}
            className="w-full resize-none rounded-xl border border-[#e5e7eb] bg-[#f8f7fc] p-4 text-sm leading-relaxed outline-none transition placeholder:text-[#858a92] focus:border-[#8b5cf6] focus:ring-4 focus:ring-[#7c3aed]/20"
          />

          {needsReference && (
            <div>
              <input
                ref={fileInputRef}
                type="file"
                accept={needsVideoReference ? "video/mp4,video/webm,video/quicktime" : "image/*"}
                className="hidden"
                onChange={(event) => {
                  const file = event.target.files?.[0];
                  if (file) void handleUpload(file);
                  event.target.value = "";
                }}
              />
              <Button
                type="button"
                variant="outline"
                onClick={() => fileInputRef.current?.click()}
                className="h-20 w-full border-dashed bg-[#f8f7fc] text-[#62666d]"
              >
                <Upload />
                {form.referenceUrl
                  ? t("creator.uploadedReference")
                  : uploading
                    ? t("creator.uploading")
                    : needsVideoReference
                      ? t("creator.uploadVideo")
                      : t("creator.uploadImage")}
              </Button>
            </div>
          )}

          <div className="flex flex-wrap items-center justify-between gap-3">
            <span className="text-xs text-[#858a92]">{form.prompt.length} / 2000</span>
            <div className="flex gap-2">
              <Button variant="ghost" onClick={() => updateForm({ mode: "prompt" })}>
                <Sparkles />
                {t("creator.smartExpand")}
              </Button>
              <Button
                disabled={loading}
                onClick={() => void handleGenerate()}
                className="min-w-44 bg-[#7c3aed] font-semibold text-[#17131f] shadow-none hover:bg-[#6d28d9]"
              >
                <WandSparkles />
                {loading ? t("creator.generating") : actionLabel(form)}
              </Button>
            </div>
          </div>
          {error && <p className="text-sm text-destructive">{error}</p>}
        </div>
      </section>

      <section className="space-y-4">
        <div className="flex flex-wrap items-end justify-between gap-3">
          <div>
            <h2 className="text-xl font-bold tracking-tight text-[#17131f]">{t("creator.myCreations")}</h2>
          </div>
          <Button variant="ghost" size="sm" onClick={() => setGalleryOpen(true)}>
            {t("creator.viewAll")}
            <ArrowRight />
          </Button>
        </div>

        <div className="flex flex-wrap gap-2">
          {(
            [
              { id: "all" as const, label: t("creator.filterAll") },
              { id: "running" as const, label: t("creator.filterRunning") },
              { id: "done" as const, label: t("creator.filterDone") },
              { id: "draft" as const, label: t("creator.filterDraft") },
            ]
          ).map((tab) => (
            <button
              key={tab.id}
              type="button"
              className={pillClass(creationFilter === tab.id)}
              onClick={() => setCreationFilter(tab.id)}
            >
              {tab.label}
            </button>
          ))}
        </div>

        {preview.length === 0 ? (
          <div className="rounded-xl border border-dashed border-[#e5e7eb] bg-white py-14 text-center text-sm text-[#62666d]">
            {t("creator.emptyCreations")}
          </div>
        ) : (
          <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3 2xl:grid-cols-4">
            {preview.map((item) => (
              <CreationCard
                key={item.id}
                item={item}
                onClick={() => setSelectedItem(item)}
                onDelete={handleDeleteCreation}
              />
            ))}
          </div>
        )}
      </section>

      <section className="grid gap-4 lg:grid-cols-2">
        <div className="rounded-xl border border-[#e5e7eb] bg-white p-1">
          <QuickStartPanel initialGuideId={initialGuideId} onRequestApiKey={() => setSettingsOpen(true)} />
        </div>
        <div className="rounded-xl border border-[#e5e7eb] bg-white p-5">
          <div className="mb-4 flex items-center justify-between gap-2">
            <h2 className="text-base font-semibold text-[#17131f]">{t("creator.announcements")}</h2>
            <Badge variant="outline">{t("common.more")}</Badge>
          </div>
          <div className="space-y-3">
            {[
              { tag: t("creator.annTagNew"), title: t("creator.ann1"), date: "05-20" },
              { tag: t("creator.annTagImprove"), title: t("creator.ann2"), date: "05-18" },
              { tag: t("creator.annTagEvent"), title: t("creator.ann3"), date: "05-15" },
            ].map((announcement) => (
              <div
                key={announcement.title}
                className="rounded-lg border border-transparent p-2 transition hover:border-[#e5e7eb] hover:bg-[#f8f7fc]"
              >
                <div className="flex items-center justify-between gap-2">
                  <Badge variant="secondary">{announcement.tag}</Badge>
                  <span className="text-[10px] text-[#858a92]">{announcement.date}</span>
                </div>
                <p className="mt-2 text-sm text-[#17131f]">{announcement.title}</p>
              </div>
            ))}
          </div>
        </div>
      </section>

      <ApiKeySettings
        open={settingsOpen}
        onClose={closeApiKeySettings}
        onSaved={(tokenfreeKey) => {
          const tokenfree = tokenfreeKey || getTokenfreeApiKey();
          const ark = getArkApiKey() || resolveArkApiKey(user);
          setApiKey(tokenfree);
          setArkApiKey(ark);
          if (form.mode === "video") {
            if (ark) void loadModels(form.mode, ark);
          } else if (tokenfree) {
            void loadModels(form.mode, tokenfree);
          }
        }}
      />
      <CreationDetailModal
        item={selectedItem}
        onClose={() => setSelectedItem(null)}
        onUpdated={handleCreationUpdated}
        onDelete={handleDeleteCreation}
      />
      <CreationsGalleryModal
        open={galleryOpen}
        initialItems={results}
        onClose={() => setGalleryOpen(false)}
        onDelete={handleDeleteCreation}
      />
      {ConfirmDialog}
    </div>
  );
}
