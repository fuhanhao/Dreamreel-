"use client";

import { useCallback, useEffect, useState } from "react";
import { fetchGenerations } from "@/lib/api";
import { CreationCard } from "./creation-card";
import { CreationDetailModal } from "./creation-detail-modal";
import type { CreationItem } from "./creation-types";
import { mapCreationRecord } from "./creation-types";
import { useT } from "@/i18n/locale-provider";

interface CreationsGalleryModalProps {
  open: boolean;
  initialItems: CreationItem[];
  onClose: () => void;
  onDelete?: (item: CreationItem) => Promise<void> | void;
}

const PAGE_SIZE = 24;

export function CreationsGalleryModal({
  open,
  initialItems,
  onClose,
  onDelete,
}: CreationsGalleryModalProps) {
  const t = useT();
  const [items, setItems] = useState<CreationItem[]>(initialItems);
  const [detailItem, setDetailItem] = useState<CreationItem | null>(null);
  const [page, setPage] = useState(0);
  const [total, setTotal] = useState(initialItems.length);
  const [loading, setLoading] = useState(false);

  const loadPage = useCallback(async (pageIndex: number, replace: boolean) => {
    setLoading(true);
    try {
      const res = await fetchGenerations(pageIndex, PAGE_SIZE);
      const mapped = res.data.items.map(mapCreationRecord);
      setTotal(res.data.total);
      setItems((prev) => (replace ? mapped : [...prev, ...mapped]));
      setPage(pageIndex);
    } catch {
      // ignore
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    if (!open) {
      const timer = window.setTimeout(() => setDetailItem(null), 0);
      return () => window.clearTimeout(timer);
    }
    document.body.style.overflow = "hidden";
    const timer = window.setTimeout(() => {
      void loadPage(0, true);
    }, 0);
    return () => {
      document.body.style.overflow = "";
      window.clearTimeout(timer);
    };
  }, [open, loadPage]);

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") {
        if (detailItem) {
          setDetailItem(null);
        } else {
          onClose();
        }
      }
    };
    if (open) document.addEventListener("keydown", onKey);
    return () => document.removeEventListener("keydown", onKey);
  }, [open, onClose, detailItem]);

  if (!open) return null;

  const hasMore = items.length < total;

  function handleDetailUpdated(item: CreationItem) {
    setItems((prev) => prev.map((i) => (i.id === item.id ? item : i)));
  }

  async function handleDelete(item: CreationItem) {
    if (!onDelete) return;
    await onDelete(item);
    setItems((prev) => prev.filter((i) => i.id !== item.id));
    setTotal((t) => Math.max(0, t - 1));
    if (detailItem?.id === item.id) {
      setDetailItem(null);
    }
  }

  return (
    <>
      <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
        <button
          type="button"
          className="absolute inset-0 bg-black/65 backdrop-blur-sm"
          onClick={onClose}
          aria-label={t("common.close")}
        />
        <div className="relative z-10 flex max-h-[90vh] w-full max-w-6xl flex-col overflow-hidden rounded-2xl border border-[var(--df-hairline)] bg-[var(--df-surface)] text-[var(--df-text)] shadow-2xl">
          <div className="flex items-center justify-between border-b border-[var(--df-hairline)] bg-[var(--df-surface-2)] px-5 py-4">
            <div>
              <h2 className="text-base font-semibold text-[var(--df-text)]">{t("creator.allCreations")}</h2>
              <p className="mt-0.5 text-xs text-[var(--df-text-4)]">{t("creator.recordCount", { count: total })}</p>
            </div>
            <button
              type="button"
              onClick={onClose}
              aria-label={t("common.close")}
              className="rounded-lg p-2 text-[var(--df-text-4)] hover:bg-white/5 hover:text-[var(--df-text-2)]"
            >
              <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
              </svg>
            </button>
          </div>

          <div className="flex-1 overflow-y-auto bg-[var(--df-surface)] p-5">
            {items.length === 0 && !loading ? (
              <p className="py-16 text-center text-sm text-[var(--df-text-4)]">{t("creator.noCreationRecords")}</p>
            ) : (
              <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
                {items.map((item) => (
                  <CreationCard
                    key={item.id}
                    item={item}
                    onClick={() => setDetailItem(item)}
                    onDelete={onDelete ? handleDelete : undefined}
                  />
                ))}
              </div>
            )}

            {hasMore && (
              <div className="mt-6 flex justify-center">
                <button
                  type="button"
                  disabled={loading}
                  onClick={() => loadPage(page + 1, false)}
                  className="rounded-xl border border-[var(--df-hairline)] px-6 py-2.5 text-sm text-[var(--df-text-2)] hover:bg-white/5 disabled:opacity-50"
                >
                  {loading
                    ? t("common.loading")
                    : t("creator.loadMore", { loaded: items.length, total })}
                </button>
              </div>
            )}
          </div>
        </div>
      </div>

      <CreationDetailModal
        item={detailItem}
        stacked
        onClose={() => setDetailItem(null)}
        onUpdated={handleDetailUpdated}
        onDelete={onDelete ? handleDelete : undefined}
      />
    </>
  );
}
