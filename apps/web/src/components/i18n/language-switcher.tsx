"use client";

import type { Locale } from "@/i18n/translate";
import { useLocale } from "@/i18n/locale-provider";

const OPTIONS: { id: Locale; label: string }[] = [
  { id: "zh", label: "中" },
  { id: "en", label: "EN" },
];

export function LanguageSwitcher({ className = "" }: { className?: string }) {
  const { locale, setLocale } = useLocale();

  return (
    <div
      className={`inline-flex items-center rounded-lg border border-border bg-white/55 p-0.5 shadow-sm backdrop-blur ${className}`}
      role="group"
      aria-label="Language"
    >
      {OPTIONS.map((opt) => {
        const active = locale === opt.id;
        return (
          <button
            key={opt.id}
            type="button"
            onClick={() => setLocale(opt.id)}
            className={`min-w-[2rem] rounded-md px-2 py-1 text-[11px] font-semibold transition ${
              active
                ? "bg-white text-[#17131f] shadow-sm"
                : "text-muted-foreground hover:text-foreground"
            }`}
          >
            {opt.label}
          </button>
        );
      })}
    </div>
  );
}
