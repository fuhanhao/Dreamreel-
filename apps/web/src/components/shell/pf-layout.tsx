"use client";

import Link from "next/link";
import type { ButtonHTMLAttributes, ReactNode } from "react";
import { cn } from "@/lib/utils";

export function PfMain({
  children,
  className,
  wide,
  flush,
}: {
  children: ReactNode;
  className?: string;
  wide?: boolean;
  flush?: boolean;
}) {
  return (
    <div className={cn("pf-shell-main", wide && "wide", flush && "flush", className)}>
      {children}
    </div>
  );
}

export function PfPageHead({
  eyebrow,
  title,
  description,
  actions,
  backHref,
  backLabel,
  className,
}: {
  eyebrow?: string;
  title: ReactNode;
  description?: ReactNode;
  actions?: ReactNode;
  backHref?: string;
  backLabel?: string;
  className?: string;
}) {
  return (
    <header className={cn("pf-page-head", className)}>
      <div className="pf-page-head-row">
        <div className="min-w-0">
          {backHref && backLabel ? (
            <Link
              href={backHref}
              className="mb-1 inline-flex items-center gap-1 text-sm font-medium text-[var(--pf-muted)] hover:text-[var(--pf-ink)]"
            >
              ← {backLabel}
            </Link>
          ) : null}
          {eyebrow ? <p className="pf-page-eyebrow">{eyebrow}</p> : null}
          <h1 className="pf-page-title">{title}</h1>
          {description ? <p className="pf-page-desc">{description}</p> : null}
        </div>
        {actions ? <div className="flex flex-wrap items-center gap-2">{actions}</div> : null}
      </div>
    </header>
  );
}

export function PfSectionHead({
  title,
  description,
  action,
  className,
}: {
  title: ReactNode;
  description?: ReactNode;
  action?: ReactNode;
  className?: string;
}) {
  return (
    <div className={cn("pf-section-head", className)}>
      <div className="min-w-0">
        <h2 className="text-xl font-bold tracking-tight text-[var(--pf-ink)]">{title}</h2>
        {description ? <p className="mt-1 text-sm text-[var(--pf-muted)]">{description}</p> : null}
      </div>
      {action}
    </div>
  );
}

export function PfPanel({
  children,
  className,
  padded = true,
}: {
  children: ReactNode;
  className?: string;
  padded?: boolean;
}) {
  return <div className={cn("pf-panel", padded && "pf-panel-pad", className)}>{children}</div>;
}

export function PfPill({
  active,
  children,
  className,
  ...props
}: ButtonHTMLAttributes<HTMLButtonElement> & { active?: boolean }) {
  return (
    <button
      type="button"
      className={cn("pf-pill", active && "active", className)}
      {...props}
    >
      {children}
    </button>
  );
}

export function PfCardGrid({
  children,
  cols = 3,
  className,
}: {
  children: ReactNode;
  cols?: 3 | 4;
  className?: string;
}) {
  return <div className={cn("pf-card-grid", cols === 4 ? "cols-4" : "cols-3", className)}>{children}</div>;
}
