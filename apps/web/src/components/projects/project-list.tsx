"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import type { Project, ProjectType } from "@dreamreel/shared-types";
import { deleteProject } from "@/lib/api";
import { useConfirmDialog } from "@/hooks/use-confirm-dialog";
import { useLocale, useT } from "@/i18n/locale-provider";

const PROJECT_TYPE_KEYS: Record<ProjectType, string> = {
  SHORT_DRAMA: "projects.typeShortDrama",
  COMIC_DRAMA: "projects.typeComicDrama",
  AD: "projects.typeAd",
  CUSTOM: "projects.typeCustom",
};

interface ProjectListProps {
  projects: Project[];
  studioMode?: "dramaforge" | "canvas";
}

export function ProjectList({ projects, studioMode = "canvas" }: ProjectListProps) {
  const router = useRouter();
  const { locale } = useLocale();
  const t = useT();
  const [deletingId, setDeletingId] = useState<string | null>(null);
  const { confirm, alert, ConfirmDialog } = useConfirmDialog("dark");

  async function handleDelete(id: string, name: string) {
    const ok = await confirm({
      title: t("projects.deleteTitle"),
      message: t("projects.deleteMessage", { name }),
      confirmLabel: t("common.delete"),
      variant: "danger",
    });
    if (!ok) return;

    setDeletingId(id);
    try {
      await deleteProject(id);
      router.refresh();
    } catch (err) {
      await alert(err instanceof Error ? err.message : t("projects.deleteFailed"), t("projects.deleteFailed"));
    } finally {
      setDeletingId(null);
    }
  }

  if (projects.length === 0) {
    return (
      <div className="rounded-xl border border-dashed border-[#d8dce1] bg-white p-10 text-center">
        <p className="text-[#62666d]">{t("projects.emptyList")}</p>
      </div>
    );
  }

  return (
    <div className="space-y-3">
      {projects.map((project) => (
        <div
          key={project.id}
          className="group flex items-center justify-between rounded-xl border border-[#e5e7eb] bg-white p-4 transition-colors hover:border-[#cbd0d6]"
        >
          <div>
            <div className="flex items-center gap-2">
              <h3 className="font-medium">{project.name}</h3>
              <span className="rounded-full bg-[#f3e8ff] px-2 py-0.5 text-xs text-[#5b21b6]">
                {t(PROJECT_TYPE_KEYS[project.type])}
              </span>
            </div>
            {project.description && (
              <p className="mt-1 line-clamp-1 text-sm text-[#62666d]">{project.description}</p>
            )}
            <p className="mt-2 text-xs text-[#858a92]">
              {t("projects.updatedAt", {
                date: new Date(project.updatedAt).toLocaleString(locale === "zh" ? "zh-CN" : "en-US"),
              })}
            </p>
          </div>

          <div className="flex items-center gap-2 opacity-100 sm:opacity-0 sm:group-hover:opacity-100 transition-opacity">
            <Link
              href={`/studio/${project.id}${studioMode === "dramaforge" ? "?mode=dramaforge" : ""}`}
              className="rounded-lg bg-[#7c3aed] px-3 py-1.5 text-sm font-semibold text-[#17131f] transition-colors hover:bg-[#6d28d9]"
            >
              {studioMode === "dramaforge" ? t("projects.openPipeline") : t("projects.openCanvas")}
            </Link>
            <button
              type="button"
              onClick={() => handleDelete(project.id, project.name)}
              disabled={deletingId === project.id}
              className="rounded-lg border border-[#d8dce1] px-3 py-1.5 text-sm text-[#62666d] transition-colors hover:border-red-500/40 hover:text-red-600 disabled:opacity-50"
            >
              {deletingId === project.id ? t("projects.deleting") : t("common.delete")}
            </button>
          </div>
        </div>
      ))}
      {ConfirmDialog}
    </div>
  );
}
