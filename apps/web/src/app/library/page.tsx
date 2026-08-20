"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import Link from "next/link";
import type { DramaForgeAsset, DramaForgeAssetType } from "@dreamreel/shared-types";
import { useCurrentProject } from "@/components/shell/current-project";
import {
  fetchDramaForgeAssets,
  fetchDramaForgeAssetVersions,
  generateDramaForgeCharacterVoice,
  updateDramaForgeAsset,
} from "@/modules/dramaforge/api";
import { resolveTokenfreeApiKey } from "@/lib/api-key";
import { uploadMedia } from "@/lib/api";
import { useAuth } from "@/components/auth/auth-provider";
import { useT } from "@/i18n/locale-provider";
import { Boxes, Search, Upload } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Progress } from "@/components/ui/progress";

function consistencyScore(asset: DramaForgeAsset): number {
  let score = 60;
  if (asset.referenceImageUrl) score += 20;
  if (asset.type === "character" && asset.voiceSampleUrl) score += 12;
  if (asset.designPrompt) score += 8;
  return Math.min(99, score);
}

export default function LibraryPage() {
  const t = useT();
  const { user } = useAuth();
  const { currentProject, currentProjectId } = useCurrentProject();
  const [assets, setAssets] = useState<DramaForgeAsset[]>([]);
  const [filter, setFilter] = useState<DramaForgeAssetType | "all" | "favorites">("all");
  const [query, setQuery] = useState("");
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [versions, setVersions] = useState<{ id: string; versionNo: number; createdAt: string }[]>([]);
  const apiKey = resolveTokenfreeApiKey(user);

  const load = useCallback(async () => {
    if (!currentProjectId) {
      setAssets([]);
      return;
    }
    try {
      const res = await fetchDramaForgeAssets(currentProjectId);
      setAssets(res.data ?? []);
    } catch (e) {
      setError(e instanceof Error ? e.message : t("library.loadFailed"));
    }
  }, [currentProjectId, t]);

  useEffect(() => {
    const timer = window.setTimeout(() => {
      void load();
    }, 0);
    return () => window.clearTimeout(timer);
  }, [load]);

  const counts = useMemo(() => {
    return {
      character: assets.filter((a) => a.type === "character").length,
      scene: assets.filter((a) => a.type === "scene").length,
      prop: assets.filter((a) => a.type === "prop").length,
      all: assets.length,
    };
  }, [assets]);

  const filtered = useMemo(() => {
    return assets.filter((a) => {
      if (filter !== "all" && filter !== "favorites" && a.type !== filter) return false;
      if (query.trim()) {
        const q = query.trim().toLowerCase();
        return (
          a.name.toLowerCase().includes(q)
          || (a.description ?? "").toLowerCase().includes(q)
        );
      }
      return true;
    });
  }, [assets, filter, query]);

  const selected = assets.find((a) => a.id === selectedId) ?? null;

  useEffect(() => {
    if (!currentProjectId || !selectedId) {
      const timer = window.setTimeout(() => setVersions([]), 0);
      return () => window.clearTimeout(timer);
    }
    void fetchDramaForgeAssetVersions(currentProjectId, selectedId)
      .then((res) =>
        setVersions(
          (res.data ?? []).map((v) => ({
            id: v.id,
            versionNo: v.versionNo,
            createdAt: v.createdAt,
          })),
        ),
      )
      .catch(() => setVersions([]));
  }, [currentProjectId, selectedId]);

  if (!currentProjectId) {
    return (
      <div className="mx-auto max-w-lg px-6 py-24 text-center">
        <Boxes className="mx-auto size-12 text-[#cbd0d6]" />
        <h1 className="mt-4 text-2xl font-semibold">{t("library.title")}</h1>
        <p className="mt-2 text-sm text-muted-foreground">
          {t("library.noProjectHint")}
        </p>
        <Button asChild className="mt-6 bg-[#7c3aed] font-semibold text-[#17131f] hover:bg-[#6d28d9]"><Link href="/projects?entry=dramaforge">{t("library.goToProjects")}</Link></Button>
      </div>
    );
  }

  return (
    <div className="mx-auto grid min-h-[calc(100svh-var(--pf-nav-h))] max-w-[1440px] grid-cols-1 gap-4 p-4 md:p-6 lg:grid-cols-[280px_minmax(0,1fr)_280px] lg:p-6">
      <aside className="pf-create-col hidden h-fit lg:flex lg:flex-col">
        <Button asChild className="pf-btn-lime mb-3 w-full"><Link href={currentProjectId ? `/studio/${currentProjectId}` : "/projects"}><Upload />{t("library.uploadAsset")}</Link></Button>
        <h3>{t("library.allAssets")}</h3>
        <div className="space-y-1">
          {(
            [
              ["all", t("library.allAssets"), counts.all],
              ["character", t("library.character"), counts.character],
              ["scene", t("library.scene"), counts.scene],
              ["prop", t("library.prop"), counts.prop],
            ] as const
          ).map(([id, label, count]) => (
            <button
              key={id}
              type="button"
              onClick={() => setFilter(id)}
              className={`flex w-full items-center justify-between rounded-xl px-3 py-2 text-sm ${
                filter === id
                  ? "bg-[#f7ffe8] font-semibold text-[#17131f] ring-1 ring-[#7c3aed]"
                  : "text-muted-foreground hover:bg-[#f1f3f5]"
              }`}
            >
              <span>{label}</span>
              <Badge variant="outline">{count}</Badge>
            </button>
          ))}
        </div>
        <div className="mt-auto rounded-lg border bg-[#f8f7fc] p-3">
          <div className="mb-2 text-[10px] text-muted-foreground">{t("library.storagePlaceholder")}</div>
          <Progress value={43} className="h-1.5" />
          <div className="mt-2 text-[10px] text-muted-foreground">86.4 / 200 GB</div>
        </div>
      </aside>

      <section className="pf-create-col min-h-0 space-y-4">
        <div>
          <p className="pf-page-eyebrow">ASSET LIBRARY</p>
          <h1 className="pf-page-title">{t("library.pageTitle")}</h1>
          <p className="pf-page-desc">
            {t("library.currentProject", { name: currentProject?.name ?? "" })}
          </p>
        </div>
        <div className="relative"><Search className="absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" /><Input value={query} onChange={(e) => setQuery(e.target.value)} placeholder={t("library.searchPlaceholder")} className="bg-[#f8f7fc] pl-9" /></div>
        {error && <p className="text-xs text-destructive">{error}</p>}
        <div className="pf-card-grid cols-3">
          {filtered.map((asset) => {
            const score = consistencyScore(asset);
            const active = selectedId === asset.id;
            return (
              <button
                key={asset.id}
                type="button"
                onClick={() => setSelectedId(asset.id)}
                className={`pf-template-card ${active ? "selected" : ""}`}
              >
                <div className="relative aspect-[4/3] bg-[#17131f]">
                  {asset.referenceImageUrl ? (
                    // eslint-disable-next-line @next/next/no-img-element
                    <img
                      src={asset.referenceImageUrl}
                      alt={asset.name}
                      className="h-full w-full object-cover"
                    />
                  ) : (
                    <div className="flex h-full items-center justify-center text-xs text-muted-foreground">
                      {t("library.noDesignImage")}
                    </div>
                  )}
                  <Badge className="absolute left-2 top-2" variant="secondary">{t(`library.${asset.type}`)}</Badge>
                </div>
                <div className="space-y-1 p-3">
                  <div className="truncate text-sm font-medium">{asset.name}</div>
                  <div className="flex items-center justify-between text-[10px] text-muted-foreground">
                    <span>{t("library.consistency", { score })}</span>
                    <span className="h-1 w-16 overflow-hidden rounded-full bg-muted">
                      <span
                        className="block h-full rounded-full bg-primary"
                        style={{ width: `${score}%` }}
                      />
                    </span>
                  </div>
                </div>
              </button>
            );
          })}
        </div>
        {filtered.length === 0 && (
          <div className="py-16 text-center text-sm text-muted-foreground">
            {t("library.emptyAssets")}
          </div>
        )}
      </section>

      <aside className="pf-create-col hidden h-fit overflow-auto lg:block">
        {!selected ? (
          <p className="text-sm text-muted-foreground">{t("library.selectAsset")}</p>
        ) : (
          <div className="space-y-4">
            <div>
              <Badge variant="secondary">{t(`library.${selected.type}`)}</Badge>
              <h2 className="mt-2 text-lg font-semibold">{selected.name}</h2>
            </div>
            <div className="overflow-hidden rounded-xl border border-[var(--df-hairline)]">
              {selected.referenceImageUrl ? (
                // eslint-disable-next-line @next/next/no-img-element
                <img src={selected.referenceImageUrl} alt="" className="aspect-[3/4] w-full object-cover" />
              ) : (
                <div className="flex aspect-[3/4] items-center justify-center bg-muted text-xs text-muted-foreground">
                  {t("library.noPreview")}
                </div>
              )}
            </div>
            {selected.description && (
              <p className="text-xs leading-relaxed text-muted-foreground">{selected.description}</p>
            )}
            <div>
              <div className="mb-1 text-[10px] text-muted-foreground">{t("library.consistencyScore")}</div>
              <div className="h-2 overflow-hidden rounded-full bg-muted">
                <div
                  className="h-full rounded-full bg-primary"
                  style={{ width: `${consistencyScore(selected)}%` }}
                />
              </div>
            </div>
            {versions.length > 0 && (
              <div>
                <div className="mb-2 text-xs font-medium text-muted-foreground">{t("library.versions")}</div>
                <ul className="space-y-1 text-[11px] text-muted-foreground">
                  {versions.slice(0, 5).map((v) => (
                    <li key={v.id}>
                      V{v.versionNo} · {new Date(v.createdAt).toLocaleString()}
                    </li>
                  ))}
                </ul>
              </div>
            )}
            {selected.type === "character" && (
              <div className="space-y-2">
                <div className="text-xs text-muted-foreground">
                  {t("library.voice", { voice: selected.voiceLabel || t("library.notSet") })}
                  {selected.voiceSampleUrl ? t("library.hasReferenceAudio") : t("library.noReferenceAudio")}
                </div>
                <Button
                  type="button"
                  disabled={busy || !apiKey}
                  variant="outline"
                  className="w-full bg-white"
                  onClick={() => {
                    if (!currentProjectId || !apiKey) return;
                    setBusy(true);
                    void generateDramaForgeCharacterVoice(currentProjectId, selected.id, apiKey)
                      .then((res) => {
                        setAssets((prev) => prev.map((a) => (a.id === res.data.id ? res.data : a)));
                      })
                      .catch((e) => setError(e instanceof Error ? e.message : t("library.voiceGenerationFailed")))
                      .finally(() => setBusy(false));
                  }}
                >
                  {t("library.generateVoice")}
                </Button>
                <label className="block w-full cursor-pointer rounded-lg border bg-white px-3 py-2 text-center text-xs hover:bg-[#f1f3f5]">
                  {t("library.uploadReferenceAudio")}
                  <input
                    type="file"
                    accept="audio/*"
                    className="hidden"
                    onChange={(e) => {
                      const file = e.target.files?.[0];
                      if (!file || !currentProjectId) return;
                      setBusy(true);
                      void uploadMedia(file)
                        .then((up) =>
                          updateDramaForgeAsset(currentProjectId, selected.id, {
                            voiceSampleUrl: up.data.url,
                          }),
                        )
                        .then((res) => {
                          setAssets((prev) => prev.map((a) => (a.id === res.data.id ? res.data : a)));
                        })
                        .catch((err) => setError(err instanceof Error ? err.message : t("library.uploadFailed")))
                        .finally(() => setBusy(false));
                    }}
                  />
                </label>
              </div>
            )}
            <Link
              href={`/studio/${currentProjectId}`}
              className="block rounded-lg bg-[#7c3aed] px-3 py-2.5 text-center text-sm font-semibold text-[#17131f] hover:bg-[#6d28d9]"
            >
              {t("library.useInWorkflow")}
            </Link>
          </div>
        )}
      </aside>
    </div>
  );
}
