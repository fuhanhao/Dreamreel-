import type { Messages } from "./locales/zh";

export type Locale = "zh" | "en";

export type MessagePath = string;
// Note: fully typed message paths use the LeafKeys recursive type below.
// Due to the 400+ key dramaforge namespace exceeding TS recursion limits,
// we allow string as a pragmatic fallback. Path validation happens at runtime.
//
// type LeafKeys<T, Prefix extends string = ""> = {
//   [K in keyof T & string]: T[K] extends Record<string, unknown>
//     ? LeafKeys<T[K], Prefix extends "" ? K : `${Prefix}.${K}`>
//     : Prefix extends "" ? K : `${Prefix}.${K}`;
// }[keyof T & string];
// export type MessagePath = LeafKeys<Messages>;

type Params = Record<string, string | number>;

function getNestedValue(obj: unknown, path: string): string | undefined {
  const parts = path.split(".");
  let current: unknown = obj;
  for (const part of parts) {
    if (current == null || typeof current !== "object") return undefined;
    current = (current as Record<string, unknown>)[part];
  }
  return typeof current === "string" ? current : undefined;
}

export function translate(
  messages: Messages,
  key: MessagePath,
  params?: Params,
): string {
  const template = getNestedValue(messages, key) ?? key;
  if (!params) return template;
  return template.replace(/\{(\w+)\}/g, (_, name: string) =>
    params[name] != null ? String(params[name]) : `{${name}}`,
  );
}

export const LOCALE_STORAGE_KEY = "pf-locale";

export function detectDefaultLocale(): Locale {
  if (typeof navigator === "undefined") return "zh";
  return navigator.language.toLowerCase().startsWith("zh") ? "zh" : "en";
}
