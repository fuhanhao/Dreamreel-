"use client";

import Image from "next/image";
import type { DramaForgeAssetVersion } from "@dreamreel/shared-types";
import { useT } from "@/i18n/locale-provider";

export interface AssetCandidatePickerProps {
  assetName: string;
  candidates: DramaForgeAssetVersion[];
  activeVersionId?: string | null;
  loading?: boolean;
  disabled?: boolean;
  onSelect: (versionId: string) => void;
  onRegenerate?: () => void;
}

export function AssetCandidatePicker({
  assetName,
  candidates,
  activeVersionId,
  loading,
  disabled,
  onSelect,
  onRegenerate,
}: AssetCandidatePickerProps) {
  const t = useT();
  if (loading) {
    return (
      <div className="mt-3 rounded-xl border border-dashed border-[var(--ar-hairline)] p-4 text-center text-xs text-[var(--ar-text-4)]">
        {t("dramaforge.assetCandidates.generating", { count: 3 })}
      </div>
    );
  }

  if (candidates.length === 0) {
    return null;
  }

  return (
    <div className="mt-3 space-y-2">
      <div className="flex items-center justify-between gap-2">
        <p className="text-xs font-medium text-[var(--ar-text-3)]">
          {t("dramaforge.assetCandidates.pickOneOfThree", { assetName })}
        </p>
        {onRegenerate && (
          <button
            type="button"
            disabled={disabled}
            onClick={onRegenerate}
            className="text-[10px] text-[var(--ar-accent-2)] disabled:opacity-40"
          >
            {t("dramaforge.assetCandidates.regenerate")}
          </button>
        )}
      </div>
      <div className="grid grid-cols-3 gap-2">
        {candidates.slice(0, 3).map((c, i) => {
          const selected = activeVersionId === c.id || (c.active && !activeVersionId);
          return (
            <button
              key={c.id}
              type="button"
              disabled={disabled}
              onClick={() => onSelect(c.id)}
              className={`group relative overflow-hidden rounded-xl border-2 transition ${
                selected
                  ? "border-[var(--ar-accent)] ring-2 ring-[var(--ar-accent)]/30"
                  : "border-[var(--ar-hairline)] hover:border-[var(--ar-accent-soft)]"
              }`}
            >
              {c.referenceImageUrl ? (
                <Image
                  src={c.referenceImageUrl}
                  alt={t("dramaforge.assetCandidates.candidateLabel", { number: i + 1 })}
                  width={600}
                  height={800}
                  className="aspect-[3/4] w-full object-cover"
                  unoptimized
                />
              ) : (
                <div className="flex aspect-[3/4] items-center justify-center bg-white/5 text-[10px] text-[var(--ar-text-4)]">
                  {t("dramaforge.assetCandidates.noImage")}
                </div>
              )}
              <span
                className={`absolute bottom-0 inset-x-0 py-1 text-center text-[10px] font-medium ${
                  selected ? "bg-[var(--ar-accent)] text-white" : "bg-black/60 text-white/90"
                }`}
              >
                {selected ? t("dramaforge.assetCandidates.selected") : t("dramaforge.assetCandidates.candidateLabel", { number: i + 1 })}
              </span>
            </button>
          );
        })}
      </div>
    </div>
  );
}
