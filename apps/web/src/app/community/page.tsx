"use client";

import { PlaceholderPage } from "@/components/shell/placeholder-page";
import { useT } from "@/i18n/locale-provider";

export default function CommunityPage() {
  const t = useT();
  return (
    <PlaceholderPage
      title={t("placeholders.communityTitle")}
      description={t("placeholders.communityDescription")}
    />
  );
}
