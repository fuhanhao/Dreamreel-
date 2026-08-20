"use client";

import Link from "next/link";
import { ArrowRight, Construction, Sparkles } from "lucide-react";
import { useCurrentProject } from "@/components/shell/current-project";
import { PfMain, PfPageHead, PfPanel } from "@/components/shell/pf-layout";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { useT } from "@/i18n/locale-provider";

export function PlaceholderPage({
  title,
  description,
}: {
  title: string;
  description: string;
}) {
  const { currentProject } = useCurrentProject();
  const t = useT();

  return (
    <PfMain>
      <PfPageHead title={title} description={description} />
      <PfPanel className="flex min-h-[420px] flex-col items-center justify-center text-center">
        <span className="grid size-14 place-items-center rounded-xl bg-[#7c3aed] text-[#17131f]">
          <Construction className="size-6" />
        </span>
        <Badge className="mt-5 bg-[#f3e8ff] text-[#5b21b6] hover:bg-[#f3e8ff]" variant="secondary">
          <Sparkles className="mr-1 size-3" />
          {t("shell.comingSoon")}
        </Badge>
        <h2 className="mt-4 text-2xl font-bold tracking-tight">{title}</h2>
        <p className="mt-3 max-w-xl text-sm leading-relaxed text-[var(--pf-muted)]">{description}</p>
        {currentProject && (
          <p className="mt-3 rounded-full border border-[#ddd6fe] bg-[#f3e8ff] px-3 py-1 text-xs text-[#5b21b6]">
            {t("shell.currentProjectNamed", { name: currentProject.name })}
          </p>
        )}
        <div className="mt-8 flex flex-wrap justify-center gap-3">
          <Button asChild className="bg-[#7c3aed] font-semibold text-[#17131f] hover:bg-[#6d28d9]">
            <Link href="/creator">
              {t("shell.backToCreator")}
              <ArrowRight />
            </Link>
          </Button>
          <Button asChild variant="outline" className="bg-white">
            <Link href="/projects">{t("nav.projects")}</Link>
          </Button>
        </div>
      </PfPanel>
    </PfMain>
  );
}
