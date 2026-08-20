import type {
  DramaForgeAgentChatInput,
  DramaForgeAgentChatResult,
  DramaForgeAsset,
  DramaForgeAssetVersion,
  DramaForgeComposition,
  DramaForgeConfig,
  DramaForgeEpisode,
  DramaForgeJob,
  DramaForgePipelineOverview,
  DramaForgeShot,
  DramaForgeShotVersion,
  CreateDramaForgeAssetInput,
  CreateDramaForgeEpisodeInput,
  CreateDramaForgeShotInput,
  OptimizeDramaForgePromptInput,
  OptimizeDramaForgePromptResult,
  UpdateDramaForgeAssetInput,
  UpdateDramaForgeConfigInput,
  UpdateDramaForgeEpisodeInput,
  UpdateDramaForgeShotInput,
} from "@dreamreel/shared-types";
import { getAuthToken } from "@/lib/auth";
import { getArkApiKey, getTokenfreeApiKey, resolveApiKeyHeader } from "@/lib/api-key";
import { request } from "@/lib/api";
import { getApiBase } from "@/lib/api-base";

const base = (projectId: string) => `/api/v1/dramaforge/projects/${projectId}`;

export function fetchDramaForgeOverview(projectId: string) {
  return request<DramaForgePipelineOverview>(`${base(projectId)}/overview`, { cache: "no-store" });
}

export function fetchDramaForgeConfig(projectId: string) {
  return request<DramaForgeConfig>(`${base(projectId)}/config`, { cache: "no-store" });
}

export function updateDramaForgeConfig(projectId: string, input: UpdateDramaForgeConfigInput) {
  return request<DramaForgeConfig>(`${base(projectId)}/config`, {
    method: "PUT",
    body: JSON.stringify(input),
  });
}

export function fetchDramaForgeAssets(projectId: string) {
  return request<DramaForgeAsset[]>(`${base(projectId)}/assets`, { cache: "no-store" });
}

export function createDramaForgeAsset(projectId: string, input: CreateDramaForgeAssetInput) {
  return request<DramaForgeAsset>(`${base(projectId)}/assets`, {
    method: "POST",
    body: JSON.stringify(input),
  });
}

export function updateDramaForgeAsset(projectId: string, assetId: string, input: UpdateDramaForgeAssetInput) {
  return request<DramaForgeAsset>(`${base(projectId)}/assets/${assetId}`, {
    method: "PATCH",
    body: JSON.stringify(input),
  });
}

export function deleteDramaForgeAsset(projectId: string, assetId: string) {
  return request<void>(`${base(projectId)}/assets/${assetId}`, { method: "DELETE" });
}

export function generateDramaForgeAssetDesigns(projectId: string, apiKey?: string | null) {
  return request<DramaForgeJob>(`${base(projectId)}/assets/generate-designs`, {
    method: "POST",
    apiKey,
  });
}

export function regenerateDramaForgeAssetDesign(
  projectId: string,
  assetId: string,
  apiKey?: string | null,
  options?: { privacySafe?: boolean },
) {
  const qs = options?.privacySafe ? "?privacySafe=true" : "";
  return request<DramaForgeJob>(`${base(projectId)}/assets/${assetId}/generate-design${qs}`, {
    method: "POST",
    apiKey,
  });
}

export function generateDramaForgeCharacterVoice(
  projectId: string,
  assetId: string,
  apiKey?: string | null,
) {
  return request<DramaForgeAsset>(`${base(projectId)}/assets/${assetId}/generate-voice`, {
    method: "POST",
    apiKey,
  });
}

export function fetchDramaForgeAssetVersions(projectId: string, assetId: string) {
  return request<DramaForgeAssetVersion[]>(`${base(projectId)}/assets/${assetId}/versions`, {
    cache: "no-store",
  });
}

export function activateDramaForgeAssetVersion(
  projectId: string,
  assetId: string,
  versionId: string,
) {
  return request<DramaForgeAssetVersion>(
    `${base(projectId)}/assets/${assetId}/versions/${versionId}/activate`,
    { method: "POST" },
  );
}

export function fetchDramaForgeEpisodes(projectId: string) {
  return request<DramaForgeEpisode[]>(`${base(projectId)}/episodes`, { cache: "no-store" });
}

export function createDramaForgeEpisode(projectId: string, input: CreateDramaForgeEpisodeInput) {
  return request<DramaForgeEpisode>(`${base(projectId)}/episodes`, {
    method: "POST",
    body: JSON.stringify(input),
  });
}

export function updateDramaForgeEpisode(projectId: string, episodeId: string, input: UpdateDramaForgeEpisodeInput) {
  return request<DramaForgeEpisode>(`${base(projectId)}/episodes/${episodeId}`, {
    method: "PATCH",
    body: JSON.stringify(input),
  });
}

export function deleteDramaForgeEpisode(projectId: string, episodeId: string) {
  return request<void>(`${base(projectId)}/episodes/${episodeId}`, { method: "DELETE" });
}

export function fetchDramaForgeShots(projectId: string, episodeId: string) {
  return request<DramaForgeShot[]>(`${base(projectId)}/episodes/${episodeId}/shots`, { cache: "no-store" });
}

export function fetchDramaForgeShot(projectId: string, episodeId: string, shotId: string) {
  return request<DramaForgeShot>(
    `${base(projectId)}/episodes/${episodeId}/shots/${shotId}`,
    { cache: "no-store" },
  );
}

export function createDramaForgeShot(projectId: string, episodeId: string, input: CreateDramaForgeShotInput) {
  return request<DramaForgeShot>(`${base(projectId)}/episodes/${episodeId}/shots`, {
    method: "POST",
    body: JSON.stringify(input),
  });
}

export function updateDramaForgeShot(
  projectId: string,
  episodeId: string,
  shotId: string,
  input: UpdateDramaForgeShotInput,
) {
  return request<DramaForgeShot>(`${base(projectId)}/episodes/${episodeId}/shots/${shotId}`, {
    method: "PATCH",
    body: JSON.stringify(input),
  });
}

export function parseDramaForgeShotsFromScript(
  projectId: string,
  episodeId: string,
  apiKey?: string | null,
) {
  return request<DramaForgeShot[]>(`${base(projectId)}/episodes/${episodeId}/shots/parse-script`, {
    method: "POST",
    apiKey,
  });
}

export function generateDramaForgeStoryboards(projectId: string, episodeId: string, apiKey?: string | null) {
  return request<DramaForgeJob>(`${base(projectId)}/episodes/${episodeId}/generate-storyboards`, {
    method: "POST",
    apiKey,
  });
}

export function regenerateDramaForgeShotStoryboard(
  projectId: string,
  episodeId: string,
  shotId: string,
  apiKey?: string | null,
) {
  return request<DramaForgeJob>(
    `${base(projectId)}/episodes/${episodeId}/shots/${shotId}/generate-storyboard`,
    {
      method: "POST",
      apiKey,
    },
  );
}

export function expandDramaForgeShotMultiCam(
  projectId: string,
  episodeId: string,
  shotId: string,
  template: string,
  modelMultiShot?: boolean,
  removeSource?: boolean,
) {
  return request<DramaForgeShot[]>(
    `${base(projectId)}/episodes/${episodeId}/shots/${shotId}/expand-multicam`,
    {
      method: "POST",
      body: JSON.stringify({
        template,
        modelMultiShot: modelMultiShot ?? false,
        removeSource: removeSource ?? false,
      }),
    },
  );
}

export type DramaForgeMultiCamPreset = {
  id: string;
  label: string;
  cameraNote: string;
};

export type DramaForgeMultiCamTemplate = {
  id: string;
  label: string;
  presets: DramaForgeMultiCamPreset[];
};

export function fetchDramaForgeMultiCamTemplates(projectId: string) {
  return request<{ templates: DramaForgeMultiCamTemplate[] }>(`${base(projectId)}/multicam-templates`);
}

export function fetchDramaForgeComposeReadiness(projectId: string, episodeId: string) {
  return request<{
    totalShots: number;
    videoDoneShots: number;
    shotsWithDialogue: number;
    missingDialogueAudio: number;
    lipSyncEnabled: boolean;
    lipSyncEndpointConfigured: boolean;
    mixDialogueAudio: boolean;
    warnings: string[];
    blockers: string[];
  }>(`${base(projectId)}/episodes/${episodeId}/compose-readiness`);
}

export function expandDramaForgeEpisodeMultiCam(
  projectId: string,
  episodeId: string,
  template: string,
  options?: {
    modelMultiShot?: boolean;
    sceneRef?: string;
    firstPerSceneOnly?: boolean;
    removeSource?: boolean;
  },
) {
  return request<{ attempted: number; expanded: number; removedSources: number; errors: string[] }>(
    `${base(projectId)}/episodes/${episodeId}/expand-multicam`,
    {
      method: "POST",
      body: JSON.stringify({
        template,
        modelMultiShot: options?.modelMultiShot ?? false,
        sceneRef: options?.sceneRef || undefined,
        firstPerSceneOnly: options?.firstPerSceneOnly ?? false,
        removeSource: options?.removeSource ?? false,
      }),
    },
  );
}

export function generateDramaForgeShotDialogueAudio(
  projectId: string,
  episodeId: string,
  shotId: string,
  apiKey?: string | null,
) {
  return request<DramaForgeShot>(
    `${base(projectId)}/episodes/${episodeId}/shots/${shotId}/generate-dialogue-audio`,
    {
      method: "POST",
      apiKey,
    },
  );
}

export function generateDramaForgeEpisodeDialogueAudio(
  projectId: string,
  episodeId: string,
  apiKey?: string | null,
) {
  return request<{ attempted: number; succeeded: number; errors: string[] }>(
    `${base(projectId)}/episodes/${episodeId}/generate-dialogue-audio`,
    {
      method: "POST",
      apiKey,
    },
  );
}

export function generateDramaForgeVideos(projectId: string, episodeId: string, apiKey?: string | null) {
  return request<DramaForgeJob>(`${base(projectId)}/episodes/${episodeId}/generate-videos`, {
    method: "POST",
    apiKey,
  });
}

export function promoteDramaForgeShotAssets(
  projectId: string,
  episodeId: string,
  shotId: string,
) {
  return request<DramaForgeAsset[]>(
    `${base(projectId)}/episodes/${episodeId}/shots/${shotId}/promote-assets`,
    { method: "POST" },
  );
}

export function promotePreviousDramaForgeShotAssets(
  projectId: string,
  episodeId: string,
  shotId: string,
) {
  return request<DramaForgeAsset[]>(
    `${base(projectId)}/episodes/${episodeId}/shots/${shotId}/promote-previous-assets`,
    { method: "POST" },
  );
}

export function regenerateDramaForgeShotVideo(
  projectId: string,
  episodeId: string,
  shotId: string,
  apiKey?: string | null,
) {
  return request<DramaForgeJob>(
    `${base(projectId)}/episodes/${episodeId}/shots/${shotId}/generate-video`,
    {
      method: "POST",
      apiKey,
    },
  );
}

/** 强化 Seedance 原声人声（不叠 TTS；字幕由生成时模型自绘） */
export function remasterDramaForgeShotDialogueAudio(
  projectId: string,
  episodeId: string,
  shotId: string,
) {
  return request<DramaForgeShot>(
    `${base(projectId)}/episodes/${episodeId}/shots/${shotId}/remaster-dialogue-audio`,
    { method: "POST" },
  );
}

export function runDramaForgeWorkflow(projectId: string, apiKey?: string | null) {
  return request<DramaForgePipelineOverview>(`${base(projectId)}/workflow/run`, {
    method: "POST",
    apiKey,
  });
}

export function extractDramaForgeAssets(projectId: string, apiKey?: string | null) {
  return request<DramaForgeJob>(`${base(projectId)}/import/extract-assets`, {
    method: "POST",
    apiKey,
  });
}

export function generateDramaForgeScript(projectId: string, apiKey?: string | null) {
  return request<DramaForgeJob>(`${base(projectId)}/import/generate-script`, {
    method: "POST",
    apiKey,
  });
}

export function composeDramaForgeEpisode(projectId: string, episodeId: string) {
  return request<DramaForgeJob>(`${base(projectId)}/episodes/${episodeId}/compose`, {
    method: "POST",
  });
}

export function syncDramaForgeVideos(projectId: string, episodeId: string, apiKey?: string | null) {
  return request<DramaForgeJob>(`${base(projectId)}/episodes/${episodeId}/sync-videos`, {
    method: "POST",
    apiKey,
  });
}

export function fetchDramaForgeJobs(projectId: string, limit = 50) {
  const params = new URLSearchParams({ limit: String(limit) });
  return request<DramaForgeJob[]>(`${base(projectId)}/jobs?${params}`, { cache: "no-store" });
}

export function cancelDramaForgeJob(projectId: string, jobId: string) {
  return request<DramaForgeJob>(`${base(projectId)}/jobs/${jobId}`, { method: "DELETE" });
}

export function retryDramaForgeJob(projectId: string, jobId: string) {
  return request<DramaForgeJob>(`${base(projectId)}/jobs/${jobId}/retry`, { method: "POST" });
}

export function clearFinishedDramaForgeJobs(projectId: string) {
  return request<number>(`${base(projectId)}/jobs/finished`, { method: "DELETE" });
}

export function fetchDramaForgeCompositions(projectId: string) {
  return request<DramaForgeComposition[]>(`${base(projectId)}/compositions`, { cache: "no-store" });
}

export function exportDramaForgeProject(projectId: string) {
  return request<DramaForgeJob>(`${base(projectId)}/export/project`, { method: "POST" });
}

export function exportDramaForgeJianying(projectId: string, episodeId: string) {
  return request<DramaForgeJob>(`${base(projectId)}/episodes/${episodeId}/export/jianying`, { method: "POST" });
}

export function fetchDramaForgeShotVersions(projectId: string, episodeId: string, shotId: string) {
  return request<DramaForgeShotVersion[]>(
    `${base(projectId)}/episodes/${episodeId}/shots/${shotId}/versions`,
    { cache: "no-store" },
  );
}

export function activateDramaForgeShotVersion(
  projectId: string,
  episodeId: string,
  shotId: string,
  versionId: string,
) {
  return request<DramaForgeShotVersion>(
    `${base(projectId)}/episodes/${episodeId}/shots/${shotId}/versions/${versionId}/activate`,
    { method: "POST" },
  );
}

export function sendDramaForgeAgentMessage(
  projectId: string,
  input: DramaForgeAgentChatInput,
  apiKey?: string | null,
) {
  return request<DramaForgeAgentChatResult>(`${base(projectId)}/agent/chat`, {
    method: "POST",
    body: JSON.stringify(input),
    apiKey,
  });
}

export function optimizeDramaForgePrompt(
  projectId: string,
  input: OptimizeDramaForgePromptInput,
  apiKey?: string | null,
) {
  return request<OptimizeDramaForgePromptResult>(`${base(projectId)}/prompts/optimize`, {
    method: "POST",
    body: JSON.stringify(input),
    apiKey,
  });
}

export function optimizeDramaForgeAssetDesignPrompts(projectId: string, apiKey?: string | null) {
  return request<DramaForgeAsset[]>(`${base(projectId)}/assets/optimize-design-prompts`, {
    method: "POST",
    apiKey,
  });
}

export function planDramaForgeEpisodes(projectId: string, apiKey?: string | null) {
  return request<import("@dreamreel/shared-types").DramaForgePlanEpisodesResult>(
    `${base(projectId)}/import/plan-episodes`,
    { method: "POST", apiKey },
  );
}

export function structureDramaForgeEpisodeScript(
  projectId: string,
  episodeId: string,
  apiKey?: string | null,
) {
  return request<DramaForgeEpisode>(`${base(projectId)}/episodes/${episodeId}/structure-script`, {
    method: "POST",
    apiKey,
  });
}

export function structureDramaForgeEpisodeShots(
  projectId: string,
  episodeId: string,
  apiKey?: string | null,
) {
  return request<DramaForgeEpisode>(`${base(projectId)}/episodes/${episodeId}/structure-shots`, {
    method: "POST",
    apiKey,
  });
}

export function lockDramaForgeScript(projectId: string, episodeId: string) {
  return request<DramaForgeEpisode>(`${base(projectId)}/episodes/${episodeId}/workflow/lock-script`, {
    method: "POST",
  });
}

export function lockDramaForgeAssets(projectId: string) {
  return request<DramaForgePipelineOverview>(`${base(projectId)}/workflow/lock-assets`, {
    method: "POST",
  });
}

export function lockDramaForgeStoryboard(projectId: string, episodeId: string) {
  return request<DramaForgeEpisode>(`${base(projectId)}/episodes/${episodeId}/workflow/lock-storyboard`, {
    method: "POST",
  });
}

export function generateDramaForgeAssetDesignCandidates(
  projectId: string,
  assetId: string,
  apiKey?: string | null,
) {
  return request<import("@dreamreel/shared-types").DramaForgeAssetDesignCandidates>(
    `${base(projectId)}/assets/${assetId}/generate-design-candidates`,
    { method: "POST", apiKey },
  );
}

export function selectDramaForgeAssetCandidate(
  projectId: string,
  assetId: string,
  versionId: string,
) {
  return request<DramaForgeAssetVersion>(
    `${base(projectId)}/assets/${assetId}/select-candidate/${versionId}`,
    { method: "POST" },
  );
}

export function subscribeDramaForgeEvents(
  projectId: string,
  onEvent: (event: string, data: unknown) => void,
): () => void {
  let stopped = false;
  let controller: AbortController | null = null;
  let reconnectTimer: ReturnType<typeof setTimeout> | null = null;
  let attempt = 0;

  const clearReconnect = () => {
    if (reconnectTimer) {
      clearTimeout(reconnectTimer);
      reconnectTimer = null;
    }
  };

  const connect = () => {
    if (stopped) return;
    clearReconnect();
    controller?.abort();
    controller = new AbortController();

    const token = getAuthToken();
    const apiKey = resolveApiKeyHeader(getTokenfreeApiKey());
    const arkApiKey = resolveApiKeyHeader(getArkApiKey());
    const headers: Record<string, string> = {
      Accept: "text/event-stream",
      "Cache-Control": "no-cache",
    };
    if (token) headers.Authorization = `Bearer ${token}`;
    if (apiKey) headers["X-Tokenfree-Api-Key"] = apiKey;
    if (arkApiKey) headers["X-Ark-Api-Key"] = arkApiKey;

    void (async () => {
      try {
        const res = await fetch(`${getApiBase()}${base(projectId)}/events`, {
          headers,
          signal: controller!.signal,
          cache: "no-store",
        });
        if (!res.ok || !res.body) {
          throw new Error(`SSE HTTP ${res.status}`);
        }
        attempt = 0;
        const reader = res.body.getReader();
        const decoder = new TextDecoder();
        let buffer = "";
        while (!stopped) {
          const { done, value } = await reader.read();
          if (done) break;
          buffer += decoder.decode(value, { stream: true });
          const chunks = buffer.split("\n\n");
          buffer = chunks.pop() ?? "";
          for (const chunk of chunks) {
            const lines = chunk.split("\n");
            let event = "message";
            let data = "";
            for (const line of lines) {
              if (line.startsWith("event:")) event = line.slice(6).trim();
              if (line.startsWith("data:")) data += line.slice(5).trim();
            }
            if (data) {
              try {
                onEvent(event, JSON.parse(data));
              } catch {
                onEvent(event, data);
              }
            }
          }
        }
      } catch (err) {
        if (stopped || (err instanceof DOMException && err.name === "AbortError")) {
          return;
        }
      }
      if (stopped) return;
      attempt += 1;
      const delay = Math.min(30_000, 1000 * 2 ** Math.min(attempt - 1, 4));
      reconnectTimer = setTimeout(connect, delay);
    })();
  };

  connect();

  return () => {
    stopped = true;
    clearReconnect();
    controller?.abort();
  };
}
