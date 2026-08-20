"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import type { ProjectType } from "@dreamreel/shared-types";
import { createProject } from "@/lib/api";
import { useT } from "@/i18n/locale-provider";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Textarea } from "@/components/ui/textarea";

const PROJECT_TYPES: Array<{ value: ProjectType; labelKey: string }> = [
  { value: "SHORT_DRAMA", labelKey: "projects.typeShortDrama" },
  { value: "COMIC_DRAMA", labelKey: "projects.typeComicDrama" },
  { value: "AD", labelKey: "projects.typeAd" },
  { value: "CUSTOM", labelKey: "projects.typeCustom" },
];

export function CreateProjectForm({
  onCreated,
  studioMode = "canvas",
}: {
  onCreated?: () => void;
  studioMode?: "dramaforge" | "canvas";
}) {
  const router = useRouter();
  const t = useT();
  const [name, setName] = useState("");
  const [type, setType] = useState<ProjectType>("SHORT_DRAMA");
  const [description, setDescription] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!name.trim()) {
      setError(t("projects.nameRequired"));
      return;
    }

    setLoading(true);
    setError("");
    try {
      const res = await createProject({
        name: name.trim(),
        type,
        description: description.trim() || undefined,
      });
      router.push(`/studio/${res.data.id}${studioMode === "dramaforge" ? "" : "?mode=canvas"}`);
      onCreated?.();
      router.refresh();
    } catch (err) {
      setError(err instanceof Error ? err.message : t("projects.createFailed"));
    } finally {
      setLoading(false);
    }
  }

  return (
    <form onSubmit={handleSubmit} className="space-y-4">
      <div>
        <h2 className="text-lg font-semibold">{t("projects.createTitle")}</h2>
        <p className="mt-1 text-sm text-muted-foreground">
          {studioMode === "dramaforge"
            ? t("projects.createPipelineHint")
            : t("projects.createCanvasExtra")}
        </p>
      </div>

      <div className="space-y-2">
        <Label htmlFor="project-name">{t("projects.projectName")}</Label>
        <Input
          id="project-name"
          value={name}
          onChange={(e) => setName(e.target.value)}
          placeholder={t("projects.namePlaceholder")}
          className="bg-white/65"
        />
      </div>

      <div className="space-y-2">
        <Label>{t("projects.projectType")}</Label>
        <Select value={type} onValueChange={(value) => setType(value as ProjectType)}>
          <SelectTrigger className="w-full bg-white/65"><SelectValue /></SelectTrigger>
          <SelectContent>
            {PROJECT_TYPES.map((projectType) => (
              <SelectItem key={projectType.value} value={projectType.value}>
                {t(projectType.labelKey)}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>

      <div className="space-y-2">
        <Label htmlFor="project-description">{t("projects.projectDesc")}</Label>
        <Textarea
          id="project-description"
          value={description}
          onChange={(e) => setDescription(e.target.value)}
          rows={3}
          placeholder={t("projects.descPlaceholder")}
          className="resize-none bg-white/65"
        />
      </div>

      {error && <p className="text-sm text-[var(--df-danger)]">{error}</p>}

      <Button type="submit" disabled={loading} className="geo-cta w-full">
        {loading
          ? t("projects.creating")
          : studioMode === "dramaforge"
            ? t("projects.createAndPipeline")
            : t("projects.createAndCanvas")}
      </Button>
    </form>
  );
}
