type ApiErrorBody = {
  success?: boolean;
  message?: string;
  error?: string;
  data?: { error?: string };
};

export class ApiError extends Error {
  readonly status: number;

  constructor(message: string, status: number) {
    super(message);
    this.name = "ApiError";
    this.status = status;
  }
}

/** 从 API 响应体提取可读错误信息 */
export function extractApiErrorMessage(body: unknown, status: number): string {
  if (body && typeof body === "object") {
    const parsed = body as ApiErrorBody;
    if (typeof parsed.message === "string" && parsed.message.trim()) {
      return parsed.message.trim();
    }
    if (typeof parsed.data?.error === "string" && parsed.data.error.trim()) {
      return parsed.data.error.trim();
    }
    if (typeof parsed.error === "string" && parsed.error.trim()) {
      return parsed.error.trim();
    }
  }

  return defaultStatusMessage(status);
}

/** 安全取出非空字符串（避免对非 string 调 .trim 抛错导致整段错误处理中断） */
export function asTrimmedString(value: unknown): string {
  if (typeof value === "string") {
    return value.trim();
  }
  if (typeof value === "number" || typeof value === "boolean") {
    return String(value).trim();
  }
  return "";
}

/**
 * 从 SSE job_failed / 任务对象提取可读失败文案。
 * 不把进度 message（如「正在生成…」）当作失败原因。
 */
export function extractJobFailureMessage(
  payload: unknown,
  fallback = "任务失败，请查看任务列表详情",
): string {
  if (!payload || typeof payload !== "object") {
    return fallback;
  }
  const record = payload as Record<string, unknown>;
  for (const key of ["error", "errorMessage"] as const) {
    const text = asTrimmedString(record[key]);
    if (text) return text;
  }
  // 嵌套 error: { message }（部分上游结构）
  const nested = record.error;
  if (nested && typeof nested === "object") {
    const nestedMsg =
      asTrimmedString((nested as { message?: unknown }).message) ||
      asTrimmedString((nested as { errorMessage?: unknown }).errorMessage);
    if (nestedMsg) return nestedMsg;
  }
  return fallback;
}

/** 从 catch 值提取可读文案，避免空字符串导致 UI 空白 */
export function getErrorMessage(error: unknown, fallback = "操作失败"): string {
  if (error instanceof ApiError && error.message.trim()) {
    return error.message.trim();
  }
  if (error instanceof Error && error.message.trim()) {
    return error.message.trim();
  }
  if (typeof error === "string" && error.trim()) {
    return error.trim();
  }
  if (error && typeof error === "object") {
    const record = error as { message?: unknown; error?: unknown; errorMessage?: unknown };
    for (const value of [record.message, record.error, record.errorMessage]) {
      const text = asTrimmedString(value);
      if (text) return text;
    }
  }
  return fallback;
}

function defaultStatusMessage(status: number): string {
  switch (status) {
    case 400:
      return "请求参数有误，请检查后重试";
    case 401:
      return "请先登录";
    case 403:
      return "无权访问，请确认账号权限或联系管理员";
    case 404:
      return "请求的资源不存在";
    case 409:
      return "数据冲突，请刷新后重试";
    case 429:
      return "操作过于频繁，请稍后再试";
    case 500:
    case 502:
    case 503:
      return "服务暂时不可用，请稍后再试";
    default:
      return `请求失败（${status}）`;
  }
}

/** 登录/注册场景下的友好提示 */
export function mapAuthErrorMessage(
  message: string,
  status: number,
  fallbacks?: {
    rejected?: string;
    invalidCredentials?: string;
    defaultMessage?: string;
  },
): string {
  const normalized = message.trim();
  if (normalized && !/^API 请求失败:/.test(normalized) && !/^请求失败（\d+）$/.test(normalized)) {
    return normalized;
  }
  if (status === 403) {
    return fallbacks?.rejected ?? "登录被拒绝，请刷新页面后重试";
  }
  if (status === 400) {
    return fallbacks?.invalidCredentials ?? "邮箱或密码错误";
  }
  if (status === 401) {
    return fallbacks?.invalidCredentials ?? "邮箱或密码错误";
  }
  return fallbacks?.defaultMessage ?? defaultStatusMessage(status);
}
