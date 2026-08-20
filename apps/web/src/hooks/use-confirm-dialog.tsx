"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import {
  DfConfirmDialog,
  type DfConfirmTheme,
  type DfConfirmVariant,
} from "@/components/ui/df-confirm-dialog";

export interface ConfirmOptions {
  title?: string;
  message: string;
  confirmLabel?: string;
  cancelLabel?: string;
  variant?: DfConfirmVariant;
  theme?: DfConfirmTheme;
}

interface DialogState extends ConfirmOptions {
  open: boolean;
  mode: "confirm" | "alert";
}

const initialState: DialogState = {
  open: false,
  mode: "confirm",
  message: "",
  theme: "dark",
};

export function useConfirmDialog(defaultTheme: DfConfirmTheme = "dark") {
  const [state, setState] = useState<DialogState>({ ...initialState, theme: defaultTheme });
  const resolverRef = useRef<((value: boolean) => void) | null>(null);

  const close = useCallback((result: boolean) => {
    setState((prev) => ({ ...initialState, theme: prev.theme ?? defaultTheme }));
    resolverRef.current?.(result);
    resolverRef.current = null;
  }, [defaultTheme]);

  const confirm = useCallback(
    (options: ConfirmOptions) => {
      return new Promise<boolean>((resolve) => {
        resolverRef.current = resolve;
        setState({
          open: true,
          mode: "confirm",
          title: options.title ?? "确认操作",
          message: options.message,
          confirmLabel: options.confirmLabel ?? "确定",
          cancelLabel: options.cancelLabel ?? "取消",
          variant: options.variant ?? "default",
          theme: options.theme ?? defaultTheme,
        });
      });
    },
    [defaultTheme],
  );

  const alert = useCallback(
    (
      message: string,
      title = "提示",
      options?: Partial<Omit<ConfirmOptions, "message">>,
    ) => {
      return new Promise<void>((resolve) => {
        resolverRef.current = () => resolve();
        setState({
          open: true,
          mode: "alert",
          title,
          message,
          confirmLabel: options?.confirmLabel ?? "知道了",
          variant: options?.variant ?? "default",
          theme: options?.theme ?? defaultTheme,
        });
      });
    },
    [defaultTheme],
  );

  useEffect(() => {
    if (!state.open) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") {
        if (state.mode === "alert") {
          close(true);
        } else {
          close(false);
        }
      }
    };
    document.addEventListener("keydown", onKey);
    document.body.style.overflow = "hidden";
    return () => {
      document.removeEventListener("keydown", onKey);
      document.body.style.overflow = "";
    };
  }, [state.open, state.mode, close]);

  const ConfirmDialog = (
    <DfConfirmDialog
      open={state.open}
      mode={state.mode}
      title={state.title}
      message={state.message}
      confirmLabel={state.confirmLabel}
      cancelLabel={state.cancelLabel}
      variant={state.variant}
      theme={state.theme}
      onConfirm={() => close(true)}
      onCancel={() => close(false)}
    />
  );

  return { confirm, alert, ConfirmDialog };
}
