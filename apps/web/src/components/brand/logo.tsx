import Link from "next/link";
import Image from "next/image";
import { APP_NAME } from "@/lib/brand";

interface BrandLogoProps {
  href?: string;
  showSubtitle?: string;
  className?: string;
  variant?: "light" | "dark";
}

export function BrandLogo({
  href = "/",
  showSubtitle,
  className = "",
  variant = "light",
}: BrandLogoProps) {
  const isDark = variant === "dark";

  const inner = (
    <div className={`flex items-center gap-2.5 ${className}`}>
      <Image
        src="/brand-icon.png"
        alt={`${APP_NAME} logo`}
        width={36}
        height={36}
        className="h-9 w-9 rounded-lg object-cover"
        priority
      />
      <div>
        <span
          className={`text-base font-semibold tracking-tight ${
            isDark ? "text-[var(--df-text)]" : "lowercase text-zinc-900"
          }`}
        >
          {APP_NAME}
        </span>
        {showSubtitle && (
          <div className={`text-xs ${isDark ? "text-[var(--df-text-4)]" : "text-zinc-400"}`}>
            {showSubtitle}
          </div>
        )}
      </div>
    </div>
  );

  if (href) {
    return <Link href={href}>{inner}</Link>;
  }

  return inner;
}
