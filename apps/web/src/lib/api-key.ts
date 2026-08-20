import type { User } from "@dreamreel/shared-types";

const TOKENFREE_STORAGE_KEY = "tokenfree_api_key";
const ARK_STORAGE_KEY = "ark_api_key";

/** 表示密钥已保存在账号；勿作为真实 Header 发送 */
export const ACCOUNT_STORED_KEY = "__account__";

export function getTokenfreeApiKey(): string | null {
  if (typeof window === "undefined") return null;
  return localStorage.getItem(TOKENFREE_STORAGE_KEY);
}

export function setTokenfreeApiKey(key: string): void {
  localStorage.setItem(TOKENFREE_STORAGE_KEY, key.trim());
}

export function clearTokenfreeApiKey(): void {
  localStorage.removeItem(TOKENFREE_STORAGE_KEY);
}

/** 从脏粘贴（api-key-时间戳+UUID / curl）中抽出方舟 Key。 */
export function sanitizeArkApiKey(raw: string | null | undefined): string | null {
  if (!raw) return null;
  const trimmed = raw.trim();
  if (!trimmed || trimmed === ACCOUNT_STORED_KEY) return null;
  const arkPrefixed =
    /ark-([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})(-[0-9a-f]+)?/i.exec(
      trimmed,
    );
  if (arkPrefixed) {
    return `ark-${arkPrefixed[1]}${arkPrefixed[2] ?? ""}`;
  }
  const uuid =
    /([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})/i.exec(trimmed);
  return uuid?.[1] ?? null;
}

export function getArkApiKey(): string | null {
  if (typeof window === "undefined") return null;
  const raw = localStorage.getItem(ARK_STORAGE_KEY);
  const clean = sanitizeArkApiKey(raw);
  if (raw && clean && raw !== clean) {
    localStorage.setItem(ARK_STORAGE_KEY, clean);
  } else if (raw && !clean) {
    localStorage.removeItem(ARK_STORAGE_KEY);
  }
  return clean;
}

export function setArkApiKey(key: string): void {
  const clean = sanitizeArkApiKey(key);
  if (!clean) {
    localStorage.removeItem(ARK_STORAGE_KEY);
    return;
  }
  localStorage.setItem(ARK_STORAGE_KEY, clean);
}

export function clearArkApiKey(): void {
  localStorage.removeItem(ARK_STORAGE_KEY);
}

export function maskApiKey(key: string): string {
  if (!key || key === ACCOUNT_STORED_KEY) return "已保存在账号";
  if (key.length <= 8) return "••••••••";
  return `${key.slice(0, 4)}••••${key.slice(-4)}`;
}

/** 解析可发送的 Header 值；账号哨兵不发送，由后端用用户存档 Key */
export function resolveApiKeyHeader(key?: string | null): string | null {
  if (!key || key === ACCOUNT_STORED_KEY) return null;
  return key;
}

export function resolveTokenfreeApiKey(user?: User | null): string | null {
  const local = getTokenfreeApiKey();
  if (local) return local;
  if (user?.hasTokenfreeApiKey) return ACCOUNT_STORED_KEY;
  return null;
}

export function resolveArkApiKey(user?: User | null): string | null {
  const local = getArkApiKey();
  if (local) return local;
  if (user?.hasArkApiKey) return ACCOUNT_STORED_KEY;
  return null;
}

export function hasAnyApiKey(user?: User | null): boolean {
  return Boolean(
    getTokenfreeApiKey() ||
      getArkApiKey() ||
      user?.hasTokenfreeApiKey ||
      user?.hasArkApiKey,
  );
}
