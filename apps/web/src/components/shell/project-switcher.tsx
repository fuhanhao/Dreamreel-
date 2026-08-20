"use client";

import { useCurrentProject } from "./current-project";
import { useT } from "@/i18n/locale-provider";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";

export function ProjectSwitcher({ className = "" }: { className?: string }) {
  const { projects, currentProjectId, setCurrentProjectId, loading } = useCurrentProject();
  const t = useT();

  if (loading && projects.length === 0) {
    return (
      <span className={`text-xs text-muted-foreground ${className}`}>
        {t("shell.loadingProjects")}
      </span>
    );
  }

  if (projects.length === 0) {
    return (
      <span className={`text-xs text-muted-foreground ${className}`}>
        {t("shell.noProjects")}
      </span>
    );
  }

  return (
    <Select
      value={currentProjectId ?? projects[0]?.id ?? ""}
      onValueChange={(id) => setCurrentProjectId(id || null)}
    >
      <SelectTrigger className={`hidden h-8 w-[170px] rounded-lg border-[#dfe2e6] bg-white text-xs shadow-none sm:flex ${className}`}>
        <SelectValue placeholder={t("shell.currentProject")} />
      </SelectTrigger>
      <SelectContent>
        {projects.map((project) => (
          <SelectItem key={project.id} value={project.id}>
            《{project.name}》
          </SelectItem>
        ))}
      </SelectContent>
    </Select>
  );
}
