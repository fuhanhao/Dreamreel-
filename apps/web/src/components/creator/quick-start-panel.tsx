"use client";

import { useEffect, useState } from "react";
import { createPortal } from "react-dom";
import { useRouter } from "next/navigation";
import { useLocale, useT } from "@/i18n/locale-provider";
import { DfScrollArea } from "@/components/ui/df-scroll-area";
import { DOCS_SOP_URL } from "@/lib/brand";
import {
  getQuickStartGuide,
  QUICK_START_GUIDES,
  type QuickStartGuideId,
} from "./quick-start-content";

interface QuickStartPanelProps {
  /** 外部指定打开某一篇（如 URL ?guide=guide） */
  initialGuideId?: QuickStartGuideId | null;
  onRequestApiKey?: () => void;
}

export function QuickStartPanel({ initialGuideId = null, onRequestApiKey }: QuickStartPanelProps) {
  const t = useT();
  const { locale } = useLocale();
  const router = useRouter();
  const [activeId, setActiveId] = useState<QuickStartGuideId | null>(null);
  const [openId, setOpenId] = useState<QuickStartGuideId | null>(null);
  const [mounted, setMounted] = useState(false);

  useEffect(() => {
    const timer = window.setTimeout(() => setMounted(true), 0);
    return () => window.clearTimeout(timer);
  }, []);

  const [prevGuideId, setPrevGuideId] = useState(initialGuideId);
  if (prevGuideId !== initialGuideId) {
    setPrevGuideId(initialGuideId);
    if (initialGuideId) {
      setActiveId(initialGuideId);
      setOpenId(initialGuideId);
    }
  }

  function openGuide(id: QuickStartGuideId) {
    setActiveId(id);
    setOpenId(id);
  }

  function closeGuide() {
    setOpenId(null);
  }

  function handleCta(href: string) {
    closeGuide();
    if (href.includes("openApiKey=1")) {
      onRequestApiKey?.();
      if (href.startsWith("/creator?") || href === "/creator?openApiKey=1") {
        router.push("/creator");
      }
      return;
    }
    if (/^https?:\/\//i.test(href)) {
      window.open(href, "_blank", "noopener,noreferrer");
      return;
    }
    router.push(href);
  }

  const isZh = locale !== "en";

  return (
    <>
      <div className="space-y-1 rounded-2xl bg-white/70 p-4">
        <div className="mb-2 flex items-center justify-between gap-2">
          <h3 className="text-xs font-medium">{t("creator.quickStart")}</h3>
          <div className="flex items-center gap-2">
            <a
              href={DOCS_SOP_URL}
              target="_blank"
              rel="noreferrer"
              className="rounded-lg px-2 py-1 text-[10px] font-medium text-[#7c3aed] transition hover:bg-secondary"
            >
              {isZh ? "查看文档" : "Docs"}
            </a>
            <button
              type="button"
              onClick={() => openGuide(activeId ?? "guide")}
              className="text-[10px] text-muted-foreground transition hover:text-foreground"
            >
              {t("common.more")}
            </button>
          </div>
        </div>
        {QUICK_START_GUIDES.map((item) => {
          const selected = activeId === item.id || openId === item.id;
          return (
            <button
              key={item.id}
              type="button"
              onClick={() => openGuide(item.id)}
              onMouseEnter={() => setActiveId(item.id)}
              className={`block w-full rounded-xl px-3 py-2.5 text-left transition ${
                selected
                  ? "bg-secondary ring-1 ring-[#7c3aed]/30"
                  : "hover:bg-white/70"
              }`}
            >
              <div className="text-[13px] font-medium">
                {isZh ? item.titleZh : item.titleEn}
              </div>
              <div className="mt-0.5 text-[11px] leading-snug text-muted-foreground">
                {isZh ? item.descZh : item.descEn}
              </div>
            </button>
          );
        })}
      </div>

      {mounted &&
        openId &&
        createPortal(
          <QuickStartModal
            guideId={openId}
            isZh={isZh}
            onClose={closeGuide}
            onSelect={setOpenId}
            onCta={handleCta}
          />,
          document.body,
        )}
    </>
  );
}

function QuickStartModal({
  guideId,
  isZh,
  onClose,
  onSelect,
  onCta,
}: {
  guideId: QuickStartGuideId;
  isZh: boolean;
  onClose: () => void;
  onSelect: (id: QuickStartGuideId) => void;
  onCta: (href: string) => void;
}) {
  const guide = getQuickStartGuide(guideId);
  const steps = isZh ? guide.stepsZh : guide.stepsEn;
  const cta = isZh ? guide.ctaZh : guide.ctaEn;
  const title = isZh ? guide.titleZh : guide.titleEn;
  const desc = isZh ? guide.descZh : guide.descEn;

  useEffect(() => {
    function onKey(e: KeyboardEvent) {
      if (e.key === "Escape") onClose();
    }
    const prev = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    window.addEventListener("keydown", onKey);
    return () => {
      document.body.style.overflow = prev;
      window.removeEventListener("keydown", onKey);
    };
  }, [onClose]);

  return (
    <div
      className="fixed inset-0 z-[100] flex items-center justify-center bg-black/65 p-4 backdrop-blur-sm"
      onClick={onClose}
      role="presentation"
    >
      <div
        className="geo-glass relative z-[101] flex max-h-[min(88svh,720px)] w-full max-w-2xl flex-col overflow-hidden rounded-3xl shadow-2xl"
        onClick={(e) => e.stopPropagation()}
        role="dialog"
        aria-modal="true"
        aria-labelledby="quick-start-title"
      >
        <div className="flex items-start justify-between gap-3 border-b border-border px-5 py-4">
          <div>
            <p className="text-[11px] tracking-wide text-muted-foreground">
              {isZh ? "快速上手" : "Quick start"}
            </p>
            <h2 id="quick-start-title" className="mt-0.5 text-lg font-semibold">
              {title}
            </h2>
            <p className="mt-1 text-sm text-muted-foreground">{desc}</p>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="rounded-lg p-1.5 text-muted-foreground hover:bg-secondary hover:text-foreground"
            aria-label={isZh ? "关闭" : "Close"}
          >
            <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
        </div>

        <div className="flex min-h-0 flex-1 flex-col sm:flex-row">
          <nav className="flex shrink-0 gap-1 overflow-x-auto border-b border-border p-3 sm:w-44 sm:flex-col sm:overflow-visible sm:border-b-0 sm:border-r">
            {QUICK_START_GUIDES.map((item) => {
              const selected = item.id === guideId;
              return (
                <button
                  key={item.id}
                  type="button"
                  onClick={() => onSelect(item.id)}
                  className={`rounded-lg px-3 py-2 text-left text-xs transition whitespace-nowrap sm:whitespace-normal ${
                    selected
                      ? "bg-secondary font-medium text-[#17131f] ring-1 ring-[#7c3aed]/30"
                      : "text-muted-foreground hover:bg-white/70 hover:text-foreground"
                  }`}
                >
                  {isZh ? item.titleZh : item.titleEn}
                </button>
              );
            })}
          </nav>

          <DfScrollArea className="min-h-0 flex-1 px-5 py-4">
            <ol className="space-y-4">
              {steps.map((step, index) => (
                <li key={step.title} className="flex gap-3">
                  <span className="mt-0.5 flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-secondary text-[11px] font-semibold text-[#17131f]">
                    {index + 1}
                  </span>
                  <div>
                    <h3 className="text-sm font-medium">{step.title}</h3>
                    <p className="mt-1 text-[13px] leading-relaxed text-muted-foreground">{step.body}</p>
                  </div>
                </li>
              ))}
            </ol>
          </DfScrollArea>
        </div>

        <div className="flex flex-wrap items-center justify-end gap-2 border-t border-border px-5 py-3">
          <button type="button" onClick={onClose} className="rounded-xl border bg-white/55 px-4 py-2 text-sm">
            {isZh ? "关闭" : "Close"}
          </button>
          {cta && (
            <button
              type="button"
              onClick={() => onCta(cta.href)}
              className="rounded-xl bg-[#7c3aed] px-4 py-2 text-sm font-semibold text-[#17131f] hover:bg-[#6d28d9]"
            >
              {cta.label}
            </button>
          )}
        </div>
      </div>
    </div>
  );
}
