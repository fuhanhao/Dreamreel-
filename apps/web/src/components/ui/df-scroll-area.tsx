"use client";

import { forwardRef, type HTMLAttributes, type ReactNode } from "react";

type DfScrollAreaProps = HTMLAttributes<HTMLDivElement> & {
  children: ReactNode;
  horizontal?: boolean;
};

/** 统一暗色细滚动条容器，用于侧边栏 / 列表 / 弹层。 */
export const DfScrollArea = forwardRef<HTMLDivElement, DfScrollAreaProps>(
  function DfScrollArea({ children, className = "", horizontal = false, ...rest }, ref) {
    return (
      <div
        ref={ref}
        {...rest}
        className={`df-scroll-area ${horizontal ? "df-scroll-area--x" : ""} ${className}`}
      >
        {children}
      </div>
    );
  },
);
