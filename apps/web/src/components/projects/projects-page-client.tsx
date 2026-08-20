"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { useEffect, useMemo, useState } from "react";
import type { Project } from "@dreamreel/shared-types";
import { useAuth } from "@/components/auth/auth-provider";
import { useT } from "@/i18n/locale-provider";
import { useCurrentProject } from "@/components/shell/current-project";
import { PfMain, PfPageHead, PfPill } from "@/components/shell/pf-layout";
import { CreateProjectForm } from "@/components/projects/create-project-form";
import { fetchProjects } from "@/lib/api";
import { ArrowRight, FolderKanban, Plus } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Progress } from "@/components/ui/progress";

const PROJECT_MENU = [
  { id: "overview", labelKey: "projects.overview" as const, href: (id: string) => `/projects?view=${id}` },
  { id: "script", labelKey: "projects.script" as const, href: (id: string) => `/studio/${id}` },
  { id: "assets", labelKey: "projects.assets" as const, href: () => `/library` },
  { id: "shots", labelKey: "projects.shots" as const, href: (id: string) => `/studio/${id}` },
  { id: "workflow", labelKey: "projects.workflow" as const, href: (id: string) => `/studio/${id}` },
  { id: "canvas", labelKey: "projects.canvas" as const, href: (id: string) => `/studio/${id}?mode=canvas` },
] as const;

type ProjectLifecycle = "inProgress" | "completed" | "draft";
type ProjectFilter = "all" | ProjectLifecycle;

const PROJECT_FILTERS: Array<{ id: ProjectFilter; labelKey: string }> = [
  { id: "all", labelKey: "projects.filterAll" },
  { id: "inProgress", labelKey: "projects.filterInProgress" },
  { id: "completed", labelKey: "projects.filterCompleted" },
  { id: "draft", labelKey: "projects.filterDraft" },
];

function resolveProjectLifecycle(project: Project): ProjectLifecycle {
  const raw = project.canvasData?.trim();
  if (!raw) {
    return project.description?.trim() ? "inProgress" : "draft";
  }
  try {
    const parsed = JSON.parse(raw) as {
      nodes?: Array<{ data?: { status?: string; nodeType?: string } }>;
    };
    const nodes = parsed.nodes ?? [];
    if (nodes.length === 0) {
      return project.description?.trim() ? "inProgress" : "draft";
    }
    const statuses = nodes
      .map((n) => n.data?.status)
      .filter((s): s is string => Boolean(s));
    if (statuses.some((s) => s === "running" || s === "queued")) {
      return "inProgress";
    }
    const mediaNodes = nodes.filter((n) => {
      const t = n.data?.nodeType;
      return t === "image" || t === "video" || t === "compose";
    });
    if (
      mediaNodes.length > 0 &&
      mediaNodes.every((n) => n.data?.status === "success")
    ) {
      return "completed";
    }
    if (statuses.length > 0 && statuses.every((s) => s === "idle")) {
      return "draft";
    }
    return "inProgress";
  } catch {
    return "inProgress";
  }
}

export function ProjectsPageClient() {
  const { user, loading } = useAuth();
  const t = useT();
  const router = useRouter();
  const searchParams = useSearchParams();
  const entryDramaForge = searchParams.get("entry") === "dramaforge";
  const viewId = searchParams.get("view");
  const { setCurrentProjectId, refreshProjects } = useCurrentProject();
  const [projects, setProjects] = useState<Project[]>([]);
  const [error, setError] = useState("");
  const [showCreate, setShowCreate] = useState(entryDramaForge);
  const [filter, setFilter] = useState<ProjectFilter>("all");

  useEffect(() => {
    if (!loading && !user) {
      router.replace(`/login?redirect=${encodeURIComponent("/projects")}`);
    }
  }, [loading, user, router]);

  useEffect(() => {
    if (!user) return;
    fetchProjects()
      .then((res) => setProjects(res.data ?? []))
      .catch((err) => setError(err instanceof Error ? err.message : t("projects.loadFailed")));
  }, [user, t]);

  const viewed = useMemo(
    () => (viewId ? projects.find((p) => p.id === viewId) : null),
    [projects, viewId],
  );

  const filteredProjects = useMemo(() => {
    if (filter === "all") return projects;
    return projects.filter((p) => resolveProjectLifecycle(p) === filter);
  }, [projects, filter]);

  if (loading || !user) {
    return (
      <div className="flex min-h-[50vh] items-center justify-center text-muted-foreground">
        {t("projects.loading")}
      </div>
    );
  }

  if (viewed) {
    return (
      <div className="mx-auto grid max-w-[1440px] gap-4 p-4 md:p-6 lg:grid-cols-[280px_minmax(0,1fr)_280px]">
        <aside className="pf-create-col space-y-3">
          <Link href="/projects" className="text-xs text-[var(--pf-muted)] hover:text-[#17131f]">
            {t("projects.backToList")}
          </Link>
          <div>
            <div className="aspect-video overflow-hidden rounded-xl bg-[#17131f]" />
            <h2 className="mt-3 text-sm font-semibold">{viewed.name}</h2>
            <p className="mt-1 text-[10px] text-[var(--pf-muted)]">{t("projects.dramaType")}</p>
          </div>
          <nav className="space-y-0.5">
            {PROJECT_MENU.map((item) => (
              <Link
                key={item.id}
                href={item.href(viewed.id)}
                onClick={() => setCurrentProjectId(viewed.id)}
                className="block rounded-xl px-3 py-2 text-sm text-[var(--pf-muted)] hover:bg-[#f7ffe8] hover:text-[#17131f]"
              >
                {t(item.labelKey)}
              </Link>
            ))}
          </nav>
          <div className="rounded-xl border border-[var(--pf-line)] bg-[#f8f7fc] p-3">
            <div className="text-[10px] text-[var(--pf-muted)]">{t("projects.storagePlaceholder")}</div>
            <div className="mt-2 h-1.5 overflow-hidden rounded-full bg-muted">
              <div className="h-full w-[43%] rounded-full bg-[#7c3aed]" />
            </div>
            <div className="mt-1 text-[10px] text-[var(--pf-muted)]">86.4 / 200 GB</div>
          </div>
        </aside>

        <section className="pf-create-col space-y-4">
          <div className="text-xs text-[var(--pf-muted)]">
            {t("projects.breadcrumb", { name: viewed.name })}
          </div>
          <div className="grid gap-4 md:grid-cols-[200px_1fr]">
            <div className="aspect-video rounded-xl bg-[#17131f]" />
            <div>
              <h1 className="pf-page-title">{viewed.name}</h1>
              <div className="mt-2 flex flex-wrap gap-1.5">
                {["projects.tagStory", "projects.tagUrban", "projects.tagShortDrama"].map((key) => (
                  <Badge key={key} variant="secondary">{t(key)}</Badge>
                ))}
              </div>
              <p className="mt-3 text-xs leading-relaxed text-[var(--pf-muted)]">
                {viewed.description || t("projects.emptyDescriptionHint")}
              </p>
              <div className="mt-4 flex flex-wrap gap-2">
                <Link
                  href={`/studio/${viewed.id}`}
                  onClick={() => setCurrentProjectId(viewed.id)}
                  className="inline-flex rounded-lg bg-[#7c3aed] px-4 py-2 text-sm font-semibold text-[#17131f] hover:bg-[#6d28d9]"
                >
                  {t("projects.openWorkflow")}
                </Link>
                <Link
                  href={`/studio/${viewed.id}?mode=canvas`}
                  className="inline-flex rounded-lg border bg-white px-4 py-2 text-sm font-medium"
                >
                  {t("projects.openCanvas")}
                </Link>
              </div>
            </div>
          </div>

          <div className="grid gap-3 sm:grid-cols-3">
            <div className="rounded-xl border border-[var(--pf-line)] bg-[#f8f7fc] p-4">
              <div className="text-xs text-[var(--pf-muted)]">{t("projects.projectProgress")}</div>
              <div className="mt-2 text-2xl font-semibold">—</div>
              <div className="mt-2 h-2 overflow-hidden rounded-full bg-muted">
                <div className="h-full w-3/4 rounded-full bg-[#7c3aed]" />
              </div>
              <p className="mt-2 text-[10px] text-[var(--pf-muted)]">{t("projects.progressHint")}</p>
            </div>
            <div className="rounded-xl border border-[var(--pf-line)] bg-[#f8f7fc] p-4">
              <div className="text-xs text-[var(--pf-muted)]">{t("projects.collaboratorsPlaceholder")}</div>
              <div className="mt-3 flex -space-x-2">
                {[
                  t("projects.collaboratorDirector"),
                  t("projects.collaboratorWriter"),
                  t("projects.collaboratorCamera"),
                ].map((c) => (
                  <span
                    key={c}
                    className="flex h-8 w-8 items-center justify-center rounded-full border border-white bg-secondary text-[10px] text-[#17131f]"
                  >
                    {c}
                  </span>
                ))}
              </div>
            </div>
            <div className="rounded-xl border border-[var(--pf-line)] bg-[#f8f7fc] p-4">
              <div className="text-xs text-[var(--pf-muted)]">{t("projects.recentlyUpdated")}</div>
              <p className="mt-3 text-sm">{t("projects.continueInStudio")}</p>
              <p className="mt-1 text-[10px] text-[var(--pf-muted)]">
                {viewed.updatedAt
                  ? new Date(viewed.updatedAt).toLocaleString()
                  : "—"}
              </p>
            </div>
          </div>

          <div className="rounded-xl border border-[var(--pf-line)] p-4">
            <div className="mb-3 flex items-center justify-between">
              <h3 className="text-sm font-medium">{t("projects.storyboard")}</h3>
              <Link
                href={`/studio/${viewed.id}`}
                className="text-xs font-medium text-[#7c3aed] hover:underline"
              >
                {t("projects.editInWorkflow")}
              </Link>
            </div>
            <p className="text-xs text-[var(--pf-muted)]">
              {t("projects.storyboardHint")}
            </p>
          </div>
        </section>

        <aside className="pf-create-col hidden space-y-3 xl:block">
          <h3>{t("projects.productionChecklist")}</h3>
          {[
            "projects.checklistScript",
            "projects.checklistStoryboard",
            "projects.checklistCharacter",
            "projects.checklistScene",
            "projects.checklistVideo",
            "projects.checklistExport",
          ].map((key) => (
            <div key={key} className="flex items-center gap-2 text-xs">
              <span className="h-3 w-3 rounded-full border border-border" />
              {t(key)}
            </div>
          ))}
          <Link
            href={`/studio/${viewed.id}`}
            className="mt-2 block rounded-lg bg-[#7c3aed] px-3 py-2.5 text-center text-sm font-semibold text-[#17131f] hover:bg-[#6d28d9]"
          >
            {t("projects.nextGenerateVideo")}
          </Link>
        </aside>
      </div>
    );
  }

  return (
    <PfMain className="space-y-6">
      <PfPageHead
        eyebrow="PROJECT WORKSPACE"
        title={t("projects.pageTitle")}
        description={t("projects.pageSubtitle")}
        actions={(
          <Button
            onClick={() => setShowCreate((value) => !value)}
            className="bg-[#7c3aed] font-semibold text-[#17131f] shadow-none hover:bg-[#6d28d9]"
          >
            <Plus />{showCreate ? t("projects.collapseCreate") : t("projects.newProject")}
          </Button>
        )}
      />

      {error && <p className="text-sm text-destructive">{error}</p>}

      {showCreate && (
        <div className="pf-panel pf-panel-pad">
          <CreateProjectForm
            studioMode={entryDramaForge ? "dramaforge" : "canvas"}
            onCreated={() => {
              void refreshProjects();
              void fetchProjects().then((res) => setProjects(res.data ?? []));
            }}
          />
        </div>
      )}

      <div className="pf-pill-tabs">
        {PROJECT_FILTERS.map((tab) => {
          const active = filter === tab.id;
          return (
            <PfPill
              key={tab.id}
              active={active}
              onClick={() => setFilter(tab.id)}
              aria-pressed={active}
            >
              {t(tab.labelKey)}
            </PfPill>
          );
        })}
      </div>

      {projects.length === 0 ? (
        <div className="pf-panel flex flex-col items-center border-dashed py-20 text-center">
          <FolderKanban className="size-10 text-[#cbd0d6]" />
          <p className="mt-4 text-sm text-[var(--pf-muted)]">{t("projects.emptyProjects")}</p>
        </div>
      ) : filteredProjects.length === 0 ? (
        <div className="pf-panel rounded-xl border-dashed py-16 text-center text-sm text-[var(--pf-muted)]">
          {t("projects.emptyFilter", {
            filter: t(PROJECT_FILTERS.find((tab) => tab.id === filter)?.labelKey ?? "projects.filterAll"),
          })}
        </div>
      ) : (
        <div className="pf-card-grid cols-3">
          {filteredProjects.map((project) => {
            const lifecycle = resolveProjectLifecycle(project);
            return (
              <Card key={project.id} className="pf-template-card overflow-hidden border-0 py-0 shadow-none">
                <Link
                  href={`/projects?view=${project.id}`}
                  onClick={() => setCurrentProjectId(project.id)}
                  className="relative block aspect-video bg-[#17131f]"
                >
                  <div className="absolute inset-0 grid place-items-center"><FolderKanban className="size-12 text-[#7c3aed]/60" /></div>
                  <Badge className="absolute left-3 top-3" variant={lifecycle === "inProgress" ? "default" : "secondary"}>
                    {t(`projects.filter${lifecycle === "inProgress" ? "InProgress" : lifecycle === "completed" ? "Completed" : "Draft"}`)}
                  </Badge>
                </Link>
                <CardContent className="space-y-3 p-5">
                  <Link
                    href={`/projects?view=${project.id}`}
                    onClick={() => setCurrentProjectId(project.id)}
                    className="block truncate font-semibold hover:text-[#7c3aed]"
                  >
                    {project.name}
                  </Link>
                  <p className="line-clamp-2 min-h-10 text-sm text-[var(--pf-muted)]">
                    {project.description || t("projects.noDescription")}
                  </p>
                  <Progress value={lifecycle === "completed" ? 100 : lifecycle === "inProgress" ? 58 : 12} className="h-1.5" />
                  <div className="grid grid-cols-2 gap-2 pt-1">
                    <Link
                      href={`/projects?view=${project.id}`}
                      onClick={() => setCurrentProjectId(project.id)}
                      className="inline-flex h-8 items-center justify-center rounded-md border border-input bg-white px-3 text-sm font-medium shadow-none transition-colors hover:bg-accent"
                    >
                      {t("projects.overview")}
                    </Link>
                    <Link
                      href={`/studio/${project.id}`}
                      onClick={() => setCurrentProjectId(project.id)}
                      className="inline-flex h-8 items-center justify-center gap-1.5 rounded-md bg-[#7c3aed] px-3 text-sm font-semibold text-[#17131f] shadow-none transition-colors hover:bg-[#6d28d9]"
                    >
                      {t("projects.workflow")}
                      <ArrowRight className="size-4" />
                    </Link>
                  </div>
                </CardContent>
              </Card>
            );
          })}
        </div>
      )}
    </PfMain>
  );
}
