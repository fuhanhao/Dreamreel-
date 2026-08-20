import { Suspense } from "react";
import { ProjectsPageClient } from "@/components/projects/projects-page-client";

export default function ProjectsPage() {
  return (
    <Suspense fallback={<div className="flex min-h-[40vh] items-center justify-center text-muted-foreground">加载中...</div>}>
      <ProjectsPageClient />
    </Suspense>
  );
}
