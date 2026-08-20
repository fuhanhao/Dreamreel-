import { Suspense } from "react";
import { CreatorPage } from "@/components/creator/creator-page";
import { DfPageLoading } from "@/components/shell/df-in-shell-state";

export default function CreatorHomePage() {
  return (
    <Suspense fallback={<DfPageLoading />}>
      <CreatorPage />
    </Suspense>
  );
}