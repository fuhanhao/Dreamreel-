"use client";

import {
  useCallback,
  useEffect,
  useId,
  useMemo,
  useRef,
  useState,
  type CSSProperties,
  type KeyboardEvent,
  type ReactNode,
} from "react";
import { createPortal } from "react-dom";
import { useT } from "@/i18n/locale-provider";

export interface DfSelectOption {
  value: string;
  label: string;
  disabled?: boolean;
}

export interface DfSelectProps {
  value: string;
  onChange: (value: string) => void;
  options: DfSelectOption[];
  placeholder?: string;
  disabled?: boolean;
  className?: string;
  /** sm: 紧凑条；md: 表单行 */
  size?: "sm" | "md";
  /** 选项较多时自动开启；也可强制开关 */
  searchable?: boolean;
  /** 默认 light（geo 浅色）；dark 仅用于深色面板 */
  variant?: "dark" | "light";
  emptyText?: string;
  /** 触发器前缀，如「当前项目」 */
  prefix?: ReactNode;
}

function Chevron({ open }: { open: boolean }) {
  return (
    <svg
      viewBox="0 0 20 20"
      fill="none"
      aria-hidden
      className={`h-3.5 w-3.5 shrink-0 transition-transform duration-150 ${open ? "rotate-180" : ""}`}
    >
      <path
        d="M5 7.5L10 12.5L15 7.5"
        stroke="currentColor"
        strokeWidth="1.6"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  );
}

function CheckIcon() {
  return (
    <svg viewBox="0 0 20 20" fill="none" aria-hidden className="h-3.5 w-3.5 shrink-0">
      <path
        d="M4.5 10.5L8 14L15.5 6"
        stroke="currentColor"
        strokeWidth="1.8"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  );
}

export function DfSelect({
  value,
  onChange,
  options,
  placeholder,
  disabled = false,
  className = "",
  size = "md",
  searchable,
  variant = "light",
  emptyText,
  prefix,
}: DfSelectProps) {
  const t = useT();
  const resolvedPlaceholder = placeholder ?? t("common.select");
  const resolvedEmptyText = emptyText ?? t("common.noMatches");
  const listId = useId();
  const rootRef = useRef<HTMLDivElement>(null);
  const triggerRef = useRef<HTMLButtonElement>(null);
  const searchRef = useRef<HTMLInputElement>(null);
  const listRef = useRef<HTMLDivElement>(null);
  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState("");
  const [activeIndex, setActiveIndex] = useState(0);
  const [menuStyle, setMenuStyle] = useState<CSSProperties>({});

  const enableSearch = searchable ?? options.length > 8;

  const selected = useMemo(
    () => options.find((o) => o.value === value) ?? null,
    [options, value],
  );

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    if (!q) return options;
    return options.filter(
      (o) => o.label.toLowerCase().includes(q) || o.value.toLowerCase().includes(q),
    );
  }, [options, query]);

  const updateMenuPosition = useCallback(() => {
    const el = triggerRef.current;
    if (!el) return;
    const rect = el.getBoundingClientRect();
    const viewportH = window.innerHeight;
    const spaceBelow = viewportH - rect.bottom;
    const spaceAbove = rect.top;
    // 紧凑高度：约 5～6 项 + 搜索框，超出滚动，不铺满屏幕
    const preferUp = spaceBelow < 200 && spaceAbove > spaceBelow;
    const available = preferUp ? spaceAbove - 12 : spaceBelow - 12;
    const maxH = Math.min(200, Math.max(140, available));
    setMenuStyle({
      position: "fixed",
      left: rect.left,
      width: Math.max(rect.width, 180),
      zIndex: 120,
      maxHeight: maxH,
      ...(preferUp
        ? { bottom: viewportH - rect.top + 6 }
        : { top: rect.bottom + 6 }),
    });
  }, []);

  useEffect(() => {
    if (!open) return;
    updateMenuPosition();
    const onScroll = () => updateMenuPosition();
    const onResize = () => updateMenuPosition();
    window.addEventListener("scroll", onScroll, true);
    window.addEventListener("resize", onResize);
    return () => {
      window.removeEventListener("scroll", onScroll, true);
      window.removeEventListener("resize", onResize);
    };
  }, [open, updateMenuPosition]);

  useEffect(() => {
    if (!open) return;
    const onDoc = (e: MouseEvent) => {
      const t = e.target as Node;
      if (rootRef.current?.contains(t)) return;
      if (listRef.current?.contains(t)) return;
      setOpen(false);
      setQuery("");
    };
    document.addEventListener("mousedown", onDoc);
    return () => document.removeEventListener("mousedown", onDoc);
  }, [open]);

  useEffect(() => {
    if (!open) return;
    const timer = window.setTimeout(() => setActiveIndex(0), 0);
    if (enableSearch) {
      requestAnimationFrame(() => searchRef.current?.focus());
    }
    return () => window.clearTimeout(timer);
  }, [open, enableSearch, query]);

  function pick(next: string) {
    onChange(next);
    setOpen(false);
    setQuery("");
    triggerRef.current?.focus();
  }

  function onTriggerKeyDown(e: KeyboardEvent<HTMLButtonElement>) {
    if (disabled) return;
    if (e.key === "ArrowDown" || e.key === "Enter" || e.key === " ") {
      e.preventDefault();
      setOpen(true);
    }
  }

  function onListKeyDown(e: KeyboardEvent) {
    if (e.key === "Escape") {
      e.preventDefault();
      setOpen(false);
      setQuery("");
      triggerRef.current?.focus();
      return;
    }
    if (e.key === "ArrowDown") {
      e.preventDefault();
      setActiveIndex((i) => Math.min(i + 1, Math.max(0, filtered.length - 1)));
      return;
    }
    if (e.key === "ArrowUp") {
      e.preventDefault();
      setActiveIndex((i) => Math.max(i - 1, 0));
      return;
    }
    if (e.key === "Enter") {
      e.preventDefault();
      const opt = filtered[activeIndex];
      if (opt && !opt.disabled) pick(opt.value);
    }
  }

  useEffect(() => {
    if (!open) return;
    const el = listRef.current?.querySelector<HTMLElement>(`[data-index="${activeIndex}"]`);
    el?.scrollIntoView({ block: "nearest" });
  }, [activeIndex, open]);

  const tone = variant === "light" ? "df-select--light" : "df-select--dark";
  const sizeClass = size === "sm" ? "df-select--sm" : "df-select--md";

  const menu =
    open &&
    typeof document !== "undefined" &&
    createPortal(
      <div
        ref={listRef}
        id={listId}
        role="listbox"
        style={menuStyle}
        className={`df-theme df-select-menu ${tone}`}
        onKeyDown={onListKeyDown}
      >
        {enableSearch && (
          <div className="df-select-search">
            <input
              ref={searchRef}
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              onKeyDown={onListKeyDown}
              placeholder={t("common.search")}
              className="df-select-search-input"
            />
          </div>
        )}
        <div className="df-select-options df-scroll-area df-scrollbar">
          {filtered.length === 0 ? (
            <div className="df-select-empty">{resolvedEmptyText}</div>
          ) : (
            filtered.map((opt, index) => {
              const active = opt.value === value;
              const highlighted = index === activeIndex;
              return (
                <button
                  key={opt.value}
                  type="button"
                  role="option"
                  data-index={index}
                  aria-selected={active}
                  disabled={opt.disabled}
                  className={`df-select-option ${active ? "is-selected" : ""} ${
                    highlighted ? "is-active" : ""
                  }`}
                  onMouseEnter={() => setActiveIndex(index)}
                  onClick={() => {
                    if (!opt.disabled) pick(opt.value);
                  }}
                >
                  <span className="df-select-option-label">{opt.label}</span>
                  {active && (
                    <span className="df-select-check">
                      <CheckIcon />
                    </span>
                  )}
                </button>
              );
            })
          )}
        </div>
      </div>,
      document.body,
    );

  return (
    <div ref={rootRef} className={`df-select ${tone} ${sizeClass} ${className}`}>
      {prefix && <span className="df-select-prefix">{prefix}</span>}
      <button
        ref={triggerRef}
        type="button"
        disabled={disabled}
        aria-haspopup="listbox"
        aria-expanded={open}
        aria-controls={open ? listId : undefined}
        className={`df-select-trigger ${open ? "is-open" : ""}`}
        onClick={() => {
          if (disabled) return;
          setOpen((v) => !v);
          if (open) setQuery("");
        }}
        onKeyDown={onTriggerKeyDown}
      >
        <span className={`df-select-value ${selected ? "" : "is-placeholder"}`}>
          {selected?.label ?? resolvedPlaceholder}
        </span>
        <span className="df-select-chevron">
          <Chevron open={open} />
        </span>
      </button>
      {menu}
    </div>
  );
}
