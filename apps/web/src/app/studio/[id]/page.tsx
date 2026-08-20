import { Suspense } from "react";
import { DfPageLoading } from "@/components/shell/df-in-shell-state";
import { StudioPageClient } from "@/components/studio/studio-page-client";

interface StudioPageProps {
  params: Promise<{ id: string }>;
}

export default async function StudioPage({ params }: StudioPageProps) {
  const { id } = await params;

  return (
    <Suspense fallback={<DfPageLoading variant="shell" />}>
      <StudioPageClient projectId={id} />
    </Suspense>
  );
}
