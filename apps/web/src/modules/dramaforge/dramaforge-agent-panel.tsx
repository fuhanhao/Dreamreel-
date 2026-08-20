"use client";

import { useRef, useState } from "react";
import type { DramaForgeAgentMessage } from "@dreamreel/shared-types";
import { sendDramaForgeAgentMessage } from "./api";
import { DfScrollArea } from "@/components/ui/df-scroll-area";
import { DramaForgePrimaryButton } from "./dramaforge-ui";
import { useT } from "@/i18n/locale-provider";

interface DramaForgeAgentPanelProps {
  projectId: string;
  selectedEpisodeId: string | null;
  apiKey: string | null;
  disabled?: boolean;
  progress?: number;
  shotCount?: number;
  videoDoneCount?: number;
  onRunWorkflow?: () => void;
  onBatchGenerate?: () => void;
  onExport?: () => void;
  onExecuted?: () => void;
}

export function DramaForgeAgentPanel({
  projectId,
  selectedEpisodeId,
  apiKey,
  disabled,
  progress = 0,
  shotCount = 0,
  videoDoneCount = 0,
  onRunWorkflow,
  onBatchGenerate,
  onExport,
  onExecuted,
}: DramaForgeAgentPanelProps) {
  const t = useT();
  const [messages, setMessages] = useState<DramaForgeAgentMessage[]>([
    {
      role: "assistant",
      content:
        t("dramaforge.agent.greeting"),
    },
  ]);
  const [input, setInput] = useState("");
  const [sending, setSending] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const scrollRef = useRef<HTMLDivElement>(null);

  async function sendMessage(text: string) {
    const trimmed = text.trim();
    if (!trimmed || sending || disabled) return;
    if (!apiKey) {
      setError(t("dramaforge.agent.apiKeyRequired"));
      return;
    }

    setError(null);
    setSending(true);
    setInput("");
    const userMessage: DramaForgeAgentMessage = { role: "user", content: trimmed };
    setMessages((prev) => [...prev, userMessage]);

    try {
      const history = [...messages, userMessage]
        .filter((m) => m.role === "user" || m.role === "assistant")
        .slice(-10)
        .map((m) => ({ role: m.role, content: m.content }));

      const res = await sendDramaForgeAgentMessage(
        projectId,
        {
          message: trimmed,
          selectedEpisodeId,
          history: history.slice(0, -1),
        },
        apiKey,
      );

      setMessages((prev) => [
        ...prev,
        {
          role: "assistant",
          content: res.data.reply,
          actions: res.data.actions,
        },
      ]);
      if (res.data.actions.length > 0) {
        onExecuted?.();
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : t("dramaforge.agent.dialogFailed"));
    } finally {
      setSending(false);
      requestAnimationFrame(() => {
        scrollRef.current?.scrollTo({ top: scrollRef.current.scrollHeight, behavior: "smooth" });
      });
    }
  }

  const videoPct = shotCount > 0 ? Math.round((videoDoneCount / shotCount) * 100) : 0;
  const summaryPct = shotCount > 0 ? Math.min(100, videoPct) : progress;

  const quickPrompts = [
    { key: t("dramaforge.agent.quickExtract") },
    { key: t("dramaforge.agent.quickScript") },
    { key: t("dramaforge.agent.quickDesign") },
    { key: t("dramaforge.agent.quickShotVideo") },
    { key: t("dramaforge.agent.advancePipeline") },
  ];

  const actionButtons = [
    { label: t("dramaforge.agent.batchGenerate"), onClick: onBatchGenerate },
    { label: t("dramaforge.agent.advancePipeline"), onClick: onRunWorkflow },
    { label: t("dramaforge.agent.exportAssets"), onClick: onExport },
    {
      label: t("dramaforge.agent.generateReport"),
      onClick: () => void sendMessage(t("dramaforge.agent.reportPrompt")),
    },
  ];

  return (
    <div className="flex h-full min-h-0 flex-col">
      <div className="space-y-3 border-b border-[var(--ar-hairline)] p-4">
        <div className="rounded-xl border border-[var(--ar-hairline)] bg-white/55 p-3">
          <div className="mb-2 text-xs font-medium text-[var(--ar-text)]">{t("dramaforge.agent.projectSummary")}</div>
          <p className="text-[11px] leading-relaxed text-[var(--ar-text-3)]">
            {t("dramaforge.agent.progressSummary", { pct: summaryPct, shots: shotCount, videos: videoDoneCount })}
          </p>
          <div className="mt-2 h-1.5 overflow-hidden rounded-full bg-slate-200/75">
            <div
              className="h-full rounded-full"
              style={{
                width: `${summaryPct}%`,
                background: "#7c3aed",
              }}
            />
          </div>
        </div>

        <div className="rounded-xl border border-[var(--ar-hairline)] bg-white/55 p-3">
          <div className="mb-2 flex items-center justify-between">
            <span className="text-xs font-medium text-[var(--ar-text)]">{t("dramaforge.agent.currentTask")}</span>
            <span className="text-[10px] text-[var(--ar-accent-2)]">
              {videoDoneCount}/{shotCount || "—"}
            </span>
          </div>
          <div className="flex items-center gap-3">
            <div
              className="relative flex h-12 w-12 shrink-0 items-center justify-center rounded-full"
              style={{
                background: `conic-gradient(#7c3aed ${videoPct}%, #e5e7eb 0)`,
              }}
            >
              <div className="flex h-9 w-9 items-center justify-center rounded-full bg-[var(--ar-surface)] text-[10px] font-semibold text-[var(--ar-text)]">
                {videoPct}%
              </div>
            </div>
            <div className="min-w-0 text-[11px]">
              <div className="font-medium text-[var(--ar-text-2)]">{t("dramaforge.agent.shotVideoGen")}</div>
              <div className="text-[var(--ar-text-4)]">{t("dramaforge.agent.shotVideoHint")}</div>
            </div>
          </div>
        </div>

        <div className="grid grid-cols-2 gap-2">
          {actionButtons.map((item) => (
            <button
              key={item.label}
              type="button"
              disabled={disabled || sending}
              onClick={() => item.onClick?.()}
              className="rounded-xl border border-[var(--ar-hairline)] bg-white/[0.03] px-2 py-2.5 text-[11px] text-[var(--ar-text-2)] transition hover:border-[var(--ar-accent-soft)] hover:bg-[var(--ar-accent-dim)] disabled:opacity-40"
            >
              {item.label}
            </button>
          ))}
        </div>
      </div>

      <DfScrollArea ref={scrollRef} className="flex-1 space-y-3 p-4">
        <div className="text-[10px] font-medium uppercase tracking-wide text-[var(--ar-text-4)]">
          {t("dramaforge.agent.recentChat")}
        </div>
        {messages.map((message, index) => (
          <div
            key={`${message.role}-${index}`}
            className={`rounded-xl px-3 py-2 text-sm ${
              message.role === "user"
                ? "ml-4 border border-[var(--ar-accent-soft)]/30 bg-[var(--ar-accent-dim)] text-[var(--ar-text)]"
                : "mr-2 border border-[var(--ar-hairline)] bg-white/60 text-[var(--ar-text-2)]"
            }`}
          >
            <div className="mb-1 text-[10px] uppercase tracking-wider text-[var(--ar-text-4)]">
              {message.role === "user" ? t("dramaforge.agent.you") : t("dramaforge.agent.aiDirector")}
            </div>
            <p className="whitespace-pre-wrap text-[12px] leading-relaxed">{message.content}</p>
            {message.actions && message.actions.length > 0 && (
              <div className="mt-2 space-y-1">
                {message.actions.map((action, actionIndex) => (
                  <div
                    key={`${action.tool}-${actionIndex}`}
                    className="rounded-lg border border-[var(--ar-hairline)] bg-slate-50/80 px-2 py-1.5 font-mono text-[10px] text-[var(--ar-text-3)]"
                  >
                    {action.tool}
                    <span className="mx-1">·</span>
                    <span
                      className={
                        action.status === "failed" ? "text-[var(--ar-danger)]" : "text-[var(--ar-good)]"
                      }
                    >
                      {action.status}
                    </span>
                  </div>
                ))}
              </div>
            )}
          </div>
        ))}
        {sending && (
          <div className="mr-2 rounded-xl border border-[var(--ar-hairline)] px-3 py-2 text-sm text-[var(--ar-text-3)]">
            {t("dramaforge.agent.thinking")}
          </div>
        )}
      </DfScrollArea>

      {error && (
        <div className="mx-4 rounded-lg border border-[var(--ar-danger)]/40 bg-[var(--ar-danger)]/10 px-3 py-2 text-xs text-[var(--ar-danger)]">
          {error}
        </div>
      )}

      <div className="border-t border-[var(--ar-hairline)] p-3">
        <div className="mb-2 flex flex-wrap gap-1.5">
          {quickPrompts.map((prompt) => (
            <button
              key={prompt.key}
              type="button"
              disabled={sending || disabled}
              onClick={() => void sendMessage(prompt.key)}
              className="rounded-full border border-[var(--ar-hairline)] px-2 py-1 text-[10px] text-[var(--ar-text-3)] transition hover:border-[var(--ar-accent-soft)] hover:text-[var(--ar-accent-2)] disabled:opacity-40"
            >
              {prompt.key}
            </button>
          ))}
        </div>
        <div className="flex gap-2">
          <textarea
            value={input}
            onChange={(e) => setInput(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === "Enter" && !e.shiftKey) {
                e.preventDefault();
                void sendMessage(input);
              }
            }}
            rows={2}
            placeholder={t("dramaforge.agent.inputPlaceholder")}
            className="dramaforge-input flex-1 resize-none rounded-xl px-3 py-2 text-sm"
            disabled={sending || disabled}
          />
          <DramaForgePrimaryButton
            disabled={sending || disabled || !input.trim()}
            className="self-end px-3"
            onClick={() => void sendMessage(input)}
          >
            {t("dramaforge.agent.send")}
          </DramaForgePrimaryButton>
        </div>
      </div>
    </div>
  );
}

export function DramaForgeTaskPanelCompact({
  children,
  onSwitchToAgent,
}: {
  children: React.ReactNode;
  onSwitchToAgent: () => void;
}) {
  const t = useT();
  return (
    <div className="flex h-full min-h-0 flex-col">
      <div className="border-b border-[var(--ar-hairline)] px-4 py-2">
        <button
          type="button"
          className="dramaforge-btn-secondary w-full rounded-xl px-3 py-2 text-xs"
          onClick={onSwitchToAgent}
        >
          {t("dramaforge.agent.switchToAgent")}
        </button>
      </div>
      <DfScrollArea className="flex-1 space-y-4 p-4">{children}</DfScrollArea>
    </div>
  );
}
