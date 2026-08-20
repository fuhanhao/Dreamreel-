"use client";

import { useCallback, useState } from "react";

export interface DfNumberStepperProps {
  value: number;
  onChange: (value: number) => void;
  min?: number;
  max?: number;
  step?: number;
  disabled?: boolean;
  className?: string;
  size?: "sm" | "md";
  variant?: "dark" | "light";
}

function clamp(n: number, min: number, max: number) {
  return Math.min(max, Math.max(min, n));
}

function StepIcon({ direction }: { direction: "up" | "down" }) {
  return (
    <svg viewBox="0 0 12 12" fill="none" aria-hidden className="h-2.5 w-2.5">
      <path
        d={direction === "up" ? "M2.5 7.5L6 4L9.5 7.5" : "M2.5 4.5L6 8L9.5 4.5"}
        stroke="currentColor"
        strokeWidth="1.4"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  );
}

export function DfNumberStepper({
  value,
  onChange,
  min = 0,
  max = 99,
  step = 1,
  disabled = false,
  className = "",
  size = "sm",
  variant = "light",
}: DfNumberStepperProps) {
  const [draft, setDraft] = useState(String(value));
  const [prevValue, setPrevValue] = useState(value);

  if (prevValue !== value) {
    setPrevValue(value);
    setDraft(String(value));
  }

  const commit = useCallback(
    (raw: string) => {
      const n = Number(raw);
      if (!Number.isFinite(n)) {
        setDraft(String(value));
        return;
      }
      const next = clamp(n, min, max);
      setDraft(String(next));
      if (next !== value) onChange(next);
    },
    [max, min, onChange, value],
  );

  const bump = (delta: number) => {
    const next = clamp(value + delta, min, max);
    setDraft(String(next));
    onChange(next);
  };

  const tone = variant === "light" ? "df-number-stepper--light" : "df-number-stepper--dark";
  const sizeClass = size === "sm" ? "df-number-stepper--sm" : "df-number-stepper--md";
  const atMin = value <= min;
  const atMax = value >= max;

  return (
    <div className={`df-number-stepper ${tone} ${sizeClass} ${className}`}>
      <input
        type="number"
        role="spinbutton"
        inputMode="numeric"
        min={min}
        max={max}
        step={step}
        disabled={disabled}
        value={draft}
        onChange={(e) => setDraft(e.target.value)}
        onBlur={() => commit(draft)}
        onKeyDown={(e) => {
          if (e.key === "Enter") {
            e.preventDefault();
            commit(draft);
            (e.target as HTMLInputElement).blur();
          }
          if (e.key === "ArrowUp") {
            e.preventDefault();
            bump(step);
          }
          if (e.key === "ArrowDown") {
            e.preventDefault();
            bump(-step);
          }
        }}
        className="df-number-stepper-input"
        aria-valuemin={min}
        aria-valuemax={max}
        aria-valuenow={value}
      />
      <div className="df-number-stepper-actions" aria-hidden={disabled}>
        <button
          type="button"
          disabled={disabled || atMax}
          className="df-number-stepper-btn"
          aria-label="增加"
          tabIndex={-1}
          onClick={() => bump(step)}
        >
          <StepIcon direction="up" />
        </button>
        <button
          type="button"
          disabled={disabled || atMin}
          className="df-number-stepper-btn"
          aria-label="减少"
          tabIndex={-1}
          onClick={() => bump(-step)}
        >
          <StepIcon direction="down" />
        </button>
      </div>
    </div>
  );
}
