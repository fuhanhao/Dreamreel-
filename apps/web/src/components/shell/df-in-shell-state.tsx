"use client";

import Link from "next/link";
import { useT } from "@/i18n/locale-provider";

/** 全屏加载（鉴权/bootstrap），保留暗色背景 */
export function DfPageLoading({
  message,
  variant = "shell",
}: {
  message?: string;
  variant?: "shell" | "fullscreen";
}) {
  const t = useT();
  const sizeClass =
    variant === "fullscreen"
      ? "min-h-svh w-full"
      : "h-full min-h-[calc(100svh-3.5rem)] w-full";

  return (
    <div
      className={`df-theme flex ${sizeClass} items-center justify-center bg-transparent text-sm text-[var(--df-text-3)]`}
      aria-live="polite"
      aria-busy="true"
    >
      <span className="inline-flex items-center gap-2">
        <span className="h-4 w-4 animate-spin rounded-full border-2 border-[var(--df-text-4)] border-t-[var(--df-accent-2)]" />
        {message ?? t("common.loading")}
      </span>
    </div>
  );
}

/** @deprecated alias */
export const DfInShellLoading = (props: { message?: string }) => (
  <DfPageLoading {...props} variant="shell" />
);

export function DfInShellError({
  message,
  backHref = "/projects",
  backLabel,
  onRetry,
}: {
  message: string;
  backHref?: string;
  backLabel?: string;
  onRetry?: () => void;
}) {
  const t = useT();
  return (
    <div className="df-theme flex h-full min-h-[calc(100svh-3.5rem)] flex-col items-center justify-center gap-4 bg-transparent px-4 text-center">
      <p className="text-sm text-[var(--df-danger)]">{message}</p>
      <div className="flex gap-3">
        <Link
          href={backHref}
          className="df-btn-ghost px-4 py-2 text-sm"
        >
          {backLabel ?? t("projects.backToList")}
        </Link>
        {onRetry && (
          <button type="button" onClick={onRetry} className="df-btn-accent px-4 py-2 text-sm">
            {t("common.retry")}
          </button>
        )}
      </div>
    </div>
  );
}
