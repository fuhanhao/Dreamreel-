import { describe, expect, it } from "vitest";
import { asTrimmedString, extractJobFailureMessage, getErrorMessage } from "./api-error";

describe("extractJobFailureMessage", () => {
  it("reads error field from SSE payload", () => {
    expect(
      extractJobFailureMessage({
        jobId: "1",
        error: "镜头 1 首帧生成失败: seedream error",
        message: "正在生成…",
      }),
    ).toBe("镜头 1 首帧生成失败: seedream error");
  });

  it("reads errorMessage when error is empty", () => {
    expect(
      extractJobFailureMessage({
        error: "  ",
        errorMessage: "尾帧生成失败: timeout",
        message: "正在生成…",
      }),
    ).toBe("尾帧生成失败: timeout");
  });

  it("does not treat progress message as failure reason", () => {
    expect(
      extractJobFailureMessage({
        error: "",
        message: "正在生成镜头 1 首帧/尾帧",
      }),
    ).toBe("任务失败，请查看任务列表详情");
  });

  it("reads nested error.message", () => {
    expect(
      extractJobFailureMessage({
        error: { message: "InputTextSensitiveContentDetected" },
      }),
    ).toBe("InputTextSensitiveContentDetected");
  });
});

describe("asTrimmedString", () => {
  it("handles non-strings without throwing", () => {
    expect(asTrimmedString({ message: "x" })).toBe("");
    expect(asTrimmedString(12)).toBe("12");
    expect(asTrimmedString(null)).toBe("");
  });
});

describe("getErrorMessage", () => {
  it("falls back when message is blank", () => {
    expect(getErrorMessage(new Error("  "), "操作失败")).toBe("操作失败");
  });
});
