import { useEffect, useState, type ReactNode } from "react";
import { useT } from "@/i18n/locale-provider";

export function GeneratingText({ label }: { label?: string }) {
  const t = useT();
  const displayLabel = label ?? t("dramaforge.ui.generating");
  const [dots, setDots] = useState(1);
  useEffect(() => {
    const timer = setInterval(() => setDots((d) => (d >= 3 ? 1 : d + 1)), 500);
    return () => clearInterval(timer);
  }, []);
  return (
    <span className="inline-flex items-baseline">
      <span>{displayLabel}</span>
      <span className="inline-block w-[1.2em] text-left">{dots === 1 ? "." : dots === 2 ? ".." : "..."}</span>
    </span>
  );
}

export function DramaForgePanel({
  title,
  children,
  className = "",
}: {
  title?: string;
  children: ReactNode;
  className?: string;
}) {
  return (
    <section className={`dramaforge-glass-panel relative overflow-hidden rounded-2xl p-5 ${className}`}>
      <div
        className="pointer-events-none absolute inset-x-0 top-0 h-px"
        style={{ background: "linear-gradient(90deg, transparent, var(--ar-accent-soft), transparent)" }}
      />
      {title && <h2 className="mb-4 text-sm font-semibold tracking-wide text-[var(--ar-text)]">{title}</h2>}
      {children}
    </section>
  );
}

export function DramaForgePrimaryButton({
  children,
  className = "",
  ...props
}: React.ButtonHTMLAttributes<HTMLButtonElement>) {
  return (
    <button
      type="button"
      className={`dramaforge-btn-primary rounded-xl px-4 py-2 text-sm font-medium transition ${className}`}
      {...props}
    >
      {children}
    </button>
  );
}

export function DramaForgeSecondaryButton({
  children,
  className = "",
  ...props
}: React.ButtonHTMLAttributes<HTMLButtonElement>) {
  return (
    <button
      type="button"
      className={`dramaforge-btn-secondary rounded-xl px-4 py-2 text-sm transition ${className}`}
      {...props}
    >
      {children}
    </button>
  );
}

export function DramaForgeStat({ label, value }: { label: string; value: number }) {
  return (
    <div className="rounded-xl border border-[var(--ar-hairline)] bg-white/55 px-3 py-2">
      <div className="text-[10px] uppercase tracking-wider text-[var(--ar-text-4)]">{label}</div>
      <div className="num mt-1 text-lg font-semibold text-[var(--ar-text)]">{value}</div>
    </div>
  );
}

export function DramaForgeBadge({
  children,
  tone = "default",
}: {
  children: ReactNode;
  tone?: "default" | "good" | "accent";
}) {
  const styles =
    tone === "good"
      ? "border-[#7c3aed]/35 bg-[#7c3aed]/15 text-[#5c8200]"
      : tone === "accent"
        ? "border-[var(--ar-accent-soft)] bg-[var(--ar-accent-dim)] text-[var(--ar-accent-2)]"
        : "border-[var(--ar-hairline)] bg-white/55 text-[var(--ar-text-3)]";
  return (
    <span className={`inline-flex rounded-full border px-2 py-0.5 text-[10px] font-medium uppercase tracking-wide ${styles}`}>
      {children}
    </span>
  );
}

export function DramaForgeJobProgressBar({
  label,
  current,
  total,
  message,
  className = "",
}: {
  label: string;
  current: number;
  total: number;
  message?: string | null;
  className?: string;
}) {
  const t = useT();
  const pct = total > 0 ? Math.min(100, Math.round((current / total) * 100)) : 0;
  const active = total > 0 && current < total;
  return (
    <div className={`rounded-xl border border-[var(--ar-accent-soft)]/30 bg-[var(--ar-accent-dim)]/40 p-3 ${className}`}>
      <div className="mb-2 flex items-center justify-between gap-2 text-xs">
        <span className="font-medium text-[var(--ar-accent-2)]">{label}</span>
        <span className="num text-[var(--ar-text-3)]">
          {total > 0 ? `${current}/${total} · ${pct}%` : t("dramaforge.ui.processing")}
        </span>
      </div>
      <div className="h-2 overflow-hidden rounded-full bg-slate-200/75">
        <div
          className={`h-full rounded-full transition-all duration-500 ${active && current === 0 ? "animate-pulse" : ""}`}
          style={{
            width: total > 0 ? `${Math.max(pct, active ? 8 : 0)}%` : "30%",
            background: "linear-gradient(90deg, var(--ar-accent), var(--ar-accent-2))",
          }}
        />
      </div>
      {message ? <p className="mt-2 text-[11px] text-[var(--ar-text-2)]">{message}</p> : null}
    </div>
  );
}
