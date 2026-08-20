"use client";

import { createPortal } from "react-dom";
import { useEffect, useState } from "react";
import { useT } from "@/i18n/locale-provider";

export type DfConfirmVariant = "default" | "danger" | "success";
export type DfConfirmTheme = "dark" | "light";

export interface DfConfirmDialogProps {
  open: boolean;
  mode: "confirm" | "alert";
  title?: string;
  message: string;
  confirmLabel?: string;
  cancelLabel?: string;
  variant?: DfConfirmVariant;
  theme?: DfConfirmTheme;
  onConfirm: () => void;
  onCancel: () => void;
}

function Icon({ variant }: { variant: DfConfirmVariant }) {
  if (variant === "danger") {
    return (
      <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" aria-hidden>
        <path
          strokeLinecap="round"
          strokeLinejoin="round"
          strokeWidth={2}
          d="M12 9v2m0 4h.01M10.29 3.86L1.82 18a2 2 0 001.71 3h16.94a2 2 0 001.71-3L13.71 3.86a2 2 0 00-3.42 0z"
        />
      </svg>
    );
  }
  if (variant === "success") {
    return (
      <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" aria-hidden>
        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 13l4 4L19 7" />
      </svg>
    );
  }
  return (
    <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" aria-hidden>
      <path
        strokeLinecap="round"
        strokeLinejoin="round"
        strokeWidth={2}
        d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"
      />
    </svg>
  );
}

export function DfConfirmDialog({
  open,
  mode,
  title,
  message,
  confirmLabel,
  cancelLabel,
  variant = "default",
  theme = "dark",
  onConfirm,
  onCancel,
}: DfConfirmDialogProps) {
  const t = useT();
  const resolvedConfirmLabel = confirmLabel ?? t("common.confirm");
  const resolvedCancelLabel = cancelLabel ?? t("common.cancel");
  const [mounted, setMounted] = useState(false);

  useEffect(() => {
    const timer = window.setTimeout(() => setMounted(true), 0);
    return () => window.clearTimeout(timer);
  }, []);

  if (!open || !mounted) return null;

  const isDark = theme === "dark";
  const iconStyles: Record<DfConfirmVariant, string> = isDark
    ? {
        default: "bg-[#7c3aed]/15 text-[#d4ff66] ring-1 ring-[#7c3aed]/25",
        danger: "bg-red-500/15 text-red-300 ring-1 ring-red-500/25",
        success: "bg-[#7c3aed]/15 text-[#d4ff66] ring-1 ring-[#7c3aed]/25",
      }
    : {
        default: "bg-[#f4ffd6] text-[#7c3aed] ring-1 ring-[#e3ffa3]",
        danger: "bg-red-50 text-red-500 ring-1 ring-red-100",
        success: "bg-[#f4ffd6] text-[#7c3aed] ring-1 ring-[#e3ffa3]",
      };

  const panelClass = isDark
    ? "border border-white/10 bg-[#161b28]/95 text-zinc-100 shadow-[0_24px_80px_-12px_rgba(0,0,0,0.75)] backdrop-blur-xl"
    : "border border-zinc-200 bg-white text-zinc-900 shadow-2xl";

  const titleClass = isDark ? "text-zinc-50" : "text-zinc-900";
  const messageClass = isDark ? "text-zinc-400" : "text-zinc-500";
  const cancelClass = isDark
    ? "border border-white/12 bg-white/5 text-zinc-300 hover:bg-white/10"
    : "border border-zinc-200 bg-white text-zinc-600 hover:bg-zinc-50";

  const confirmClass =
    variant === "danger"
      ? "bg-gradient-to-r from-red-500 to-rose-600 text-white shadow-lg shadow-red-500/25 hover:brightness-110"
      : variant === "success"
        ? "bg-[#7c3aed] text-[#17131f] shadow-sm hover:bg-[#6d28d9]"
        : "bg-[#7c3aed] text-[#17131f] shadow-sm hover:bg-[#6d28d9]";

  const handleBackdrop = () => {
    if (mode === "alert") {
      onConfirm();
    } else {
      onCancel();
    }
  };

  return createPortal(
    <div className="fixed inset-0 z-[200] flex items-center justify-center p-4">
      <button
        type="button"
        className="absolute inset-0 bg-black/55 backdrop-blur-[6px]"
        onClick={handleBackdrop}
        aria-label={t("common.close")}
      />
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby="df-dialog-title"
        className={`relative z-10 w-full max-w-md overflow-hidden rounded-2xl ${panelClass}`}
      >
        <div className="pointer-events-none absolute inset-x-0 top-0 h-px bg-gradient-to-r from-transparent via-white/20 to-transparent" />
        <div className="px-5 pt-5 pb-1">
          <div className="flex items-start gap-3.5">
            <div
              className={`flex h-11 w-11 shrink-0 items-center justify-center rounded-xl ${iconStyles[variant]}`}
            >
              <Icon variant={variant} />
            </div>
            <div className="min-w-0 flex-1 pt-0.5">
              <h3 id="df-dialog-title" className={`text-base font-semibold leading-snug ${titleClass}`}>
                {title}
              </h3>
              <p className={`mt-2.5 whitespace-pre-wrap text-sm leading-relaxed ${messageClass}`}>
                {message}
              </p>
            </div>
          </div>
        </div>

        <div className={`mt-4 flex gap-2.5 px-5 py-4 ${isDark ? "border-t border-white/8" : "border-t border-zinc-100"}`}>
          {mode === "confirm" && (
            <button
              type="button"
              onClick={onCancel}
              className={`flex-1 rounded-xl px-4 py-2.5 text-sm font-medium transition ${cancelClass}`}
            >
              {resolvedCancelLabel}
            </button>
          )}
          <button
            type="button"
            onClick={onConfirm}
            className={`flex-1 rounded-xl px-4 py-2.5 text-sm font-medium transition ${confirmClass} ${
              mode === "alert" ? "w-full" : ""
            }`}
          >
            {resolvedConfirmLabel}
          </button>
        </div>
      </div>
    </div>,
    document.body,
  );
}
