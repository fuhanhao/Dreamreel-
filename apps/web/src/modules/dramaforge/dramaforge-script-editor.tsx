"use client";

import { useMemo, useState } from "react";
import { useT } from "@/i18n/locale-provider";

interface ScriptShot {
  description: string;
  dialogue?: string;
  camera?: string;
  scene?: string;
  characters?: string[];
  props?: string[];
}

interface ScriptScene {
  name: string;
  description?: string;
  dialogue?: string;
  characters?: string[];
  location?: string;
  time?: string;
  shots?: ScriptShot[];
}

export interface DramaForgeScriptEditorProps {
  scriptJson: string;
  onChange: (scriptJson: string) => void;
  disabled?: boolean;
}

function hasShots(scenes: ScriptScene[]): boolean {
  return scenes.some((s) => (s.shots?.length ?? 0) > 0);
}

function parseScript(scriptJson: string): { title: string; scenes: ScriptScene[] } | null {
  try {
    const root = JSON.parse(scriptJson) as { title?: string; scenes?: ScriptScene[] };
    if (!root.scenes?.length) return null;
    return { title: root.title ?? "", scenes: root.scenes };
  } catch {
    return null;
  }
}

export function DramaForgeScriptEditor({ scriptJson, onChange, disabled }: DramaForgeScriptEditorProps) {
  const t = useT();
  const parsed = useMemo(() => parseScript(scriptJson), [scriptJson]);
  const shotMode = parsed ? hasShots(parsed.scenes) : false;
  const [mode, setMode] = useState<"text" | "cards">(parsed ? "cards" : "text");

  if (!parsed || mode === "text") {
    return (
      <div className="space-y-2">
        <div className="flex gap-2">
          {parsed && (
            <button
              type="button"
              className="text-xs text-[var(--ar-accent-2)]"
              onClick={() => setMode("cards")}
            >
              {t("dramaforge.script.switchToCards")}
            </button>
          )}
        </div>
        <textarea
          disabled={disabled}
          value={scriptJson}
          onChange={(e) => onChange(e.target.value)}
          rows={12}
          className="dramaforge-input w-full rounded-xl px-3 py-2 text-sm font-mono"
        />
      </div>
    );
  }

  function emit(next: { title: string; scenes: ScriptScene[] }) {
    onChange(JSON.stringify(next, null, 2));
  }

  function updateScene(sceneIndex: number, patch: Partial<ScriptScene>) {
    if (!parsed) return;
    const next = structuredClone(parsed);
    Object.assign(next.scenes[sceneIndex], patch);
    emit(next);
  }

  function updateShot(sceneIndex: number, shotIndex: number, patch: Partial<ScriptShot>) {
    if (!parsed) return;
    const next = structuredClone(parsed);
    const shots = next.scenes[sceneIndex].shots;
    if (!shots) return;
    Object.assign(shots[shotIndex], patch);
    emit(next);
  }

  function mergeShots(sceneIndex: number, shotIndex: number) {
    if (!parsed) return;
    const next = structuredClone(parsed);
    const shots = next.scenes[sceneIndex].shots;
    if (!shots || shotIndex >= shots.length - 1) return;
    const a = shots[shotIndex];
    const b = shots[shotIndex + 1];
    a.description = `${a.description}\n${b.description}`.trim();
    a.dialogue = [a.dialogue, b.dialogue].filter(Boolean).join(" ");
    shots.splice(shotIndex + 1, 1);
    emit(next);
  }

  function splitShot(sceneIndex: number, shotIndex: number) {
    if (!parsed) return;
    const next = structuredClone(parsed);
    const shots = next.scenes[sceneIndex].shots;
    if (!shots) return;
    const shot = shots[shotIndex];
    const copy = { ...shot, description: `${shot.description}${t("dramaforge.script.continued")}` };
    shots.splice(shotIndex + 1, 0, copy);
    emit(next);
  }

  return (
    <div className="space-y-3">
      <div className="flex items-center gap-3">
        <button type="button" className="text-xs text-[var(--ar-accent-2)]" onClick={() => setMode("text")}>
          {t("dramaforge.script.switchToText")}
        </button>
        <span className="text-[10px] text-[var(--ar-text-4)]">
          {shotMode ? t("dramaforge.script.shotMode") : t("dramaforge.script.scriptMode")}
        </span>
      </div>
      {parsed.scenes.map((scene, si) => (
        <div key={si} className="rounded-xl border border-[var(--ar-hairline)] p-3">
          <input
            disabled={disabled}
            value={scene.name}
            onChange={(e) => updateScene(si, { name: e.target.value })}
            className="dramaforge-input mb-2 w-full rounded px-2 py-1 text-sm font-medium"
            placeholder={t("dramaforge.script.sceneName")}
          />
          {!shotMode && (
            <div className="space-y-2">
              <textarea
                disabled={disabled}
                value={scene.description ?? ""}
                onChange={(e) => updateScene(si, { description: e.target.value })}
                rows={3}
                className="dramaforge-input w-full rounded px-2 py-1 text-xs"
                placeholder={t("dramaforge.script.sceneDescription")}
              />
              <textarea
                disabled={disabled}
                value={scene.dialogue ?? ""}
                onChange={(e) => updateScene(si, { dialogue: e.target.value })}
                rows={3}
                className="dramaforge-input w-full rounded px-2 py-1 text-xs font-mono"
                placeholder={t("dramaforge.script.sceneDialogue")}
              />
              <div className="flex gap-2">
                <input
                  disabled={disabled}
                  value={scene.location ?? ""}
                  onChange={(e) => updateScene(si, { location: e.target.value })}
                  className="dramaforge-input flex-1 rounded px-2 py-1 text-xs"
                  placeholder={t("dramaforge.script.location")}
                />
                <input
                  disabled={disabled}
                  value={scene.time ?? ""}
                  onChange={(e) => updateScene(si, { time: e.target.value })}
                  className="dramaforge-input w-24 rounded px-2 py-1 text-xs"
                  placeholder={t("dramaforge.script.dayNight")}
                />
              </div>
            </div>
          )}
          {shotMode && (scene.shots ?? []).map((shot, ji) => (
            <div key={ji} className="mt-2 rounded-lg bg-white/[0.03] p-2">
              <textarea
                disabled={disabled}
                value={shot.description}
                onChange={(e) => updateShot(si, ji, { description: e.target.value })}
                rows={2}
                className="dramaforge-input mb-1 w-full rounded px-2 py-1 text-xs"
                placeholder={t("dramaforge.script.shotDescription")}
              />
              <input
                disabled={disabled}
                value={shot.dialogue ?? ""}
                onChange={(e) => updateShot(si, ji, { dialogue: e.target.value })}
                className="dramaforge-input mb-1 w-full rounded px-2 py-1 text-xs"
                placeholder={t("dramaforge.script.shotDialogue")}
              />
              <input
                disabled={disabled}
                value={shot.camera ?? ""}
                onChange={(e) => updateShot(si, ji, { camera: e.target.value })}
                className="dramaforge-input w-full rounded px-2 py-1 text-xs"
                placeholder={t("dramaforge.script.camera")}
              />
              <div className="mt-1 flex gap-2">
                <button
                  type="button"
                  disabled={disabled}
                  className="text-[10px] text-[var(--ar-accent-2)]"
                  onClick={() => mergeShots(si, ji)}
                >
                  {t("dramaforge.script.mergeNextShot")}
                </button>
                <button
                  type="button"
                  disabled={disabled}
                  className="text-[10px] text-[var(--ar-accent-2)]"
                  onClick={() => splitShot(si, ji)}
                >
                  {t("dramaforge.script.splitShot")}
                </button>
              </div>
            </div>
          ))}
        </div>
      ))}
    </div>
  );
}
