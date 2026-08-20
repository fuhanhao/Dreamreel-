"use client";

import { useEffect, useRef, useState } from "react";
import { useT } from "@/i18n/locale-provider";

interface DfExpandableTextProps {
  text: string;
  className?: string;
  textClassName?: string;
  buttonClassName?: string;
  maxLines?: 2 | 3 | 4;
  expandLabel?: string;
  collapseLabel?: string;
}

const clampByLines = {
  2: "line-clamp-2",
  3: "line-clamp-3",
  4: "line-clamp-4",
} as const;

export function DfExpandableText({
  text,
  className = "",
  textClassName = "text-xs leading-relaxed text-[var(--ar-text-3)]",
  buttonClassName = "mt-1 text-[11px] text-[var(--ar-accent-2)] hover:underline",
  maxLines = 2,
  expandLabel,
  collapseLabel,
}: DfExpandableTextProps) {
  const t = useT();
  const resolvedExpandLabel = expandLabel ?? t("common.expand");
  const resolvedCollapseLabel = collapseLabel ?? t("common.collapse");
  const [expanded, setExpanded] = useState(false);
  const [canExpand, setCanExpand] = useState(false);
  const ref = useRef<HTMLParagraphElement>(null);
  const trimmed = text.trim();

  useEffect(() => {
    const el = ref.current;
    if (!el || !trimmed || expanded) return;
    const check = () => {
      setCanExpand(el.scrollHeight > el.clientHeight + 2);
    };
    const raf = requestAnimationFrame(check);
    window.addEventListener("resize", check);
    return () => {
      cancelAnimationFrame(raf);
      window.removeEventListener("resize", check);
    };
  }, [trimmed, expanded]);

  if (!trimmed) return null;

  return (
    <div className={className}>
      <p
        ref={ref}
        className={`whitespace-pre-wrap break-words ${textClassName} ${
          expanded ? "" : clampByLines[maxLines]
        }`}
      >
        {trimmed}
      </p>
      {(canExpand || expanded) && (
        <button
          type="button"
          onClick={() => setExpanded((v) => !v)}
          className={buttonClassName}
        >
          {expanded ? resolvedCollapseLabel : resolvedExpandLabel}
        </button>
      )}
    </div>
  );
}
