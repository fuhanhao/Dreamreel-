"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useT } from "@/i18n/locale-provider";
import { DOCS_REPO_URL } from "@/lib/brand";
import { useCurrentProject } from "./current-project";

export function TopNav() {
  const pathname = usePathname();
  const { currentProjectId } = useCurrentProject();
  const t = useT();

  const navItems = [
    { href: "/", label: t("nav.home"), match: (p: string) => p === "/", external: false },
    { href: "/projects", label: t("nav.projects"), match: (p: string) => p.startsWith("/projects"), external: false },
    { href: "/library", label: t("nav.library"), match: (p: string) => p.startsWith("/library"), external: false },
    { href: "/market", label: t("nav.market"), match: (p: string) => p.startsWith("/market"), external: false },
    {
      href: "workflow",
      label: t("nav.workflow"),
      match: (p: string) => p.startsWith("/studio"),
      external: false,
    },
    { href: "/community", label: t("nav.community"), match: (p: string) => p.startsWith("/community"), external: false },
    { href: DOCS_REPO_URL, label: t("nav.docs"), match: () => false, external: true },
  ] as const;

  const linkClass = (active: boolean) =>
    `relative px-3 py-2 text-sm transition ${
      active
        ? "font-semibold text-[#17131f]"
        : "text-[var(--df-text-3)] hover:text-[var(--df-text)]"
    }`;

  return (
    <nav className="hidden items-center gap-1 md:flex">
      {navItems.map((item) => {
        const href =
          item.href === "workflow"
            ? currentProjectId
              ? `/studio/${currentProjectId}`
              : "/projects?entry=dramaforge"
            : item.href;
        const active = item.match(pathname);
        if (item.external) {
          return (
            <a
              key={item.href}
              href={href}
              target="_blank"
              rel="noreferrer"
              className={linkClass(false)}
            >
              {item.label}
            </a>
          );
        }
        return (
          <Link key={item.href} href={href} className={linkClass(active)}>
            {item.label}
            {active && (
              <span className="absolute inset-x-3 -bottom-0.5 h-0.5 rounded-full bg-[#8b5cf6]" />
            )}
          </Link>
        );
      })}
    </nav>
  );
}
