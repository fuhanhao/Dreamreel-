"use client";

import { useEffect, useState } from "react";
import type { User } from "@dreamreel/shared-types";
import {
  ACCOUNT_STORED_KEY,
  clearArkApiKey,
  clearTokenfreeApiKey,
  getArkApiKey,
  getTokenfreeApiKey,
  maskApiKey,
  setArkApiKey,
  setTokenfreeApiKey,
} from "@/lib/api-key";
import { updateArkKey, updateTokenfreeKey } from "@/lib/api";
import { useAuth } from "@/components/auth/auth-provider";
import { useT } from "@/i18n/locale-provider";

interface ApiKeySettingsProps {
  open: boolean;
  onClose: () => void;
  onSaved: (tokenfreeKey: string) => void;
}

export function ApiKeySettings({ open, onClose, onSaved }: ApiKeySettingsProps) {
  const { user, refreshUser } = useAuth();
  const t = useT();
  const [tokenfreeValue, setTokenfreeValue] = useState("");
  const [arkValue, setArkValue] = useState("");
  const [savedTokenfree, setSavedTokenfree] = useState<string | null>(null);
  const [savedArk, setSavedArk] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!open) return;
    const timer = window.setTimeout(() => {
      setError(null);
      const tokenfree = resolveDisplayKey(getTokenfreeApiKey(), user?.hasTokenfreeApiKey);
      const ark = resolveDisplayKey(getArkApiKey(), user?.hasArkApiKey);
      setSavedTokenfree(tokenfree);
      setSavedArk(ark);
      setTokenfreeValue(tokenfree && tokenfree !== ACCOUNT_STORED_KEY ? tokenfree : "");
      setArkValue(ark && ark !== ACCOUNT_STORED_KEY ? ark : "");
    }, 0);
    return () => window.clearTimeout(timer);
  }, [open, user?.hasTokenfreeApiKey, user?.hasArkApiKey]);

  if (!open) return null;

  async function handleSave() {
    const tokenfree = tokenfreeValue.trim();
    const ark = arkValue.trim();
    if (!tokenfree && !ark) return;

    setSaving(true);
    setError(null);
    try {
      let latestUser: User | null = user;
      if (tokenfree) {
        const res = await updateTokenfreeKey(tokenfree);
        latestUser = res.data;
        setTokenfreeApiKey(tokenfree);
        setSavedTokenfree(tokenfree);
      }
      if (ark) {
        const res = await updateArkKey(ark);
        latestUser = res.data;
        setArkApiKey(ark);
        setSavedArk(ark);
      }
      await refreshUser();
      onSaved(tokenfree || getTokenfreeApiKey() || (latestUser?.hasTokenfreeApiKey ? ACCOUNT_STORED_KEY : ""));
      onClose();
    } catch (e) {
      setError(e instanceof Error ? e.message : t("creator.saveApiKeyFailed"));
    } finally {
      setSaving(false);
    }
  }

  async function handleClearTokenfree() {
    setSaving(true);
    setError(null);
    try {
      await updateTokenfreeKey("");
      clearTokenfreeApiKey();
      setTokenfreeValue("");
      setSavedTokenfree(null);
      await refreshUser();
      onSaved("");
    } catch (e) {
      setError(e instanceof Error ? e.message : t("creator.clearApiKeyFailed"));
    } finally {
      setSaving(false);
    }
  }

  async function handleClearArk() {
    setSaving(true);
    setError(null);
    try {
      await updateArkKey("");
      clearArkApiKey();
      setArkValue("");
      setSavedArk(null);
      await refreshUser();
    } catch (e) {
      setError(e instanceof Error ? e.message : t("creator.clearApiKeyFailed"));
    } finally {
      setSaving(false);
    }
  }

  const canSave = Boolean(tokenfreeValue.trim() || arkValue.trim()) && !saving;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4 backdrop-blur-sm">
      <div className="df-panel w-full max-w-md p-6">
        <div className="mb-1 flex items-center justify-between">
          <h2 className="text-lg font-semibold text-[var(--df-text)]">{t("creator.apiKeyTitle")}</h2>
          <button
            type="button"
            onClick={onClose}
            disabled={saving}
            className="rounded-lg p-1.5 text-[var(--df-text-4)] hover:bg-white/5 hover:text-[var(--df-text-2)]"
          >
            <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
        </div>
        <p className="mb-4 text-sm text-[var(--df-text-3)]">
          {t("creator.apiKeyDescription")}
        </p>

        {error && (
          <div className="mb-3 rounded-xl bg-[rgba(248,113,113,0.12)] px-3 py-2 text-xs text-[var(--df-danger)]">
            {error}
          </div>
        )}

        <div className="space-y-4">
          <div>
            <div className="mb-1.5 flex items-center justify-between gap-2">
              <label className="text-sm font-medium text-[var(--df-text-2)]">TokenFree API Key</label>
              <a
                href="https://www.tokenfree.com"
                target="_blank"
                rel="noopener noreferrer"
                className="text-xs text-[var(--df-teal)] hover:underline"
              >
                {t("creator.apiKeyGet")}
              </a>
            </div>
            <p className="mb-2 text-[11px] text-[var(--df-text-4)]">{t("creator.tokenfreeHint")}</p>
            {savedTokenfree && (
              <div className="mb-2 rounded-xl bg-[rgba(182,255,0,0.16)] px-3 py-2 text-xs text-[var(--df-good)]">
                {t("creator.apiKeyConfigured", { key: maskApiKey(savedTokenfree) })}
              </div>
            )}
            <input
              type="password"
              value={tokenfreeValue}
              onChange={(e) => setTokenfreeValue(e.target.value)}
              placeholder={savedTokenfree === ACCOUNT_STORED_KEY ? t("creator.apiKeyAccountPlaceholder") : "sk-..."}
              disabled={saving}
              className="df-input w-full px-4 py-2.5 text-sm"
            />
            {savedTokenfree && (
              <button
                type="button"
                onClick={() => void handleClearTokenfree()}
                disabled={saving}
                className="mt-1.5 text-xs text-[var(--df-text-4)] hover:text-[var(--df-danger)] disabled:opacity-40"
              >
                {t("creator.clearTokenfreeKey")}
              </button>
            )}
          </div>

          <div>
            <div className="mb-1.5 flex items-center justify-between gap-2">
              <label className="text-sm font-medium text-[var(--df-text-2)]">{t("creator.arkLabel")}</label>
              <a
                href="https://console.volcengine.com/ark"
                target="_blank"
                rel="noopener noreferrer"
                className="text-xs text-[var(--df-teal)] hover:underline"
              >
                {t("creator.apiKeyGet")}
              </a>
            </div>
            <p className="mb-2 text-[11px] text-[var(--df-text-4)]">{t("creator.arkHint")}</p>
            {savedArk && (
              <div className="mb-2 rounded-xl bg-[rgba(182,255,0,0.16)] px-3 py-2 text-xs text-[var(--df-good)]">
                {t("creator.apiKeyConfigured", { key: maskApiKey(savedArk) })}
              </div>
            )}
            <input
              type="password"
              value={arkValue}
              onChange={(e) => setArkValue(e.target.value)}
              placeholder={savedArk === ACCOUNT_STORED_KEY ? t("creator.apiKeyAccountPlaceholder") : "uuid or ak-..."}
              disabled={saving}
              className="df-input w-full px-4 py-2.5 text-sm"
            />
            {savedArk && (
              <button
                type="button"
                onClick={() => void handleClearArk()}
                disabled={saving}
                className="mt-1.5 text-xs text-[var(--df-text-4)] hover:text-[var(--df-danger)] disabled:opacity-40"
              >
                {t("creator.clearArkKey")}
              </button>
            )}
          </div>
        </div>

        <div className="mt-5 flex gap-2">
          <button
            type="button"
            onClick={() => void handleSave()}
            disabled={!canSave}
            className="df-btn-accent flex-1 py-2.5 text-sm disabled:opacity-40"
          >
            {saving ? t("creator.savingApiKey") : t("creator.saveApiKey")}
          </button>
          <button
            type="button"
            onClick={onClose}
            disabled={saving}
            className="df-btn-ghost px-4 py-2.5 text-sm"
          >
            {t("common.cancel")}
          </button>
        </div>
      </div>
    </div>
  );
}

function resolveDisplayKey(local: string | null, hasAccount?: boolean): string | null {
  if (local) return local;
  if (hasAccount) return ACCOUNT_STORED_KEY;
  return null;
}
