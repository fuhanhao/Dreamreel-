"use client";

import Image from "next/image";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { ChevronDown, LogOut, Menu, Settings, UserRound, X } from "lucide-react";
import { useAuth } from "@/components/auth/auth-provider";
import { LanguageSwitcher } from "@/components/i18n/language-switcher";
import { DfPageLoading } from "@/components/shell/df-in-shell-state";
import { Avatar, AvatarFallback } from "@/components/ui/avatar";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { useT } from "@/i18n/locale-provider";
import { loginPath, registerPath } from "@/lib/auth-redirect";
import { hasAnyApiKey } from "@/lib/api-key";
import { APP_NAME } from "@/lib/brand";
import { cn } from "@/lib/utils";
import { CurrentProjectProvider } from "./current-project";
import { ProjectSwitcher } from "./project-switcher";

const AUTH_PATHS = ["/login", "/register"];
const PUBLIC_PATHS = ["/", "/creator"];
const BARE_PATHS = ["/admin"];

function isPublicPath(pathname: string) {
  return PUBLIC_PATHS.some((p) => pathname === p || pathname.startsWith(`${p}/`));
}

function HeaderActions({ compact = false }: { compact?: boolean }) {
  const { user, logout, isAdmin } = useAuth();
  const t = useT();
  const apiConnected = hasAnyApiKey(user);

  return (
    <div className={cn("flex items-center gap-2", compact && "w-full flex-wrap")}>
      <LanguageSwitcher />
      {user && <ProjectSwitcher />}
      {user && !compact && (
        <Badge
          variant="outline"
          className={
            apiConnected
              ? "hidden border-[#7c3aed]/30 bg-[#7c3aed]/15 text-[#5c8200] sm:inline-flex"
              : "hidden border-amber-500/25 bg-amber-500/10 text-amber-700 sm:inline-flex"
          }
        >
          <span className={`mr-1.5 size-1.5 rounded-full ${apiConnected ? "bg-[#7c3aed]" : "bg-amber-500"}`} />
          {apiConnected ? t("shell.apiConnected") : t("shell.apiMissing")}
        </Badge>
      )}
      {user ? (
        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <Button variant="ghost" className="h-9 gap-2 rounded-lg px-2">
              <Avatar className="size-7 border border-border">
                <AvatarFallback className="bg-[#f3e8ff] text-xs font-semibold text-[#5b21b6]">
                  {(user.displayName ?? user.email ?? "U").slice(0, 1).toUpperCase()}
                </AvatarFallback>
              </Avatar>
              <span className="hidden max-w-28 truncate text-xs lg:inline">
                {user.displayName ?? user.email}
              </span>
              <ChevronDown className="size-3.5 text-muted-foreground" />
            </Button>
          </DropdownMenuTrigger>
          <DropdownMenuContent align="end" className="w-56">
            <DropdownMenuLabel>
              <span className="block text-sm">{user.displayName ?? t("common.user")}</span>
              <span className="block truncate text-xs font-normal text-muted-foreground">{user.email}</span>
            </DropdownMenuLabel>
            <DropdownMenuSeparator />
            <DropdownMenuItem asChild>
              <Link href="/creator?openApiKey=1"><Settings />{t("creator.apiKeySettings")}</Link>
            </DropdownMenuItem>
            <DropdownMenuItem asChild>
              <Link href="/projects"><UserRound />{t("nav.projects")}</Link>
            </DropdownMenuItem>
            {isAdmin && (
              <DropdownMenuItem asChild>
                <Link href="/admin"><Settings />{t("common.admin")}</Link>
              </DropdownMenuItem>
            )}
            <DropdownMenuSeparator />
            <DropdownMenuItem onClick={logout}>
              <LogOut />{t("common.logout")}
            </DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>
      ) : (
        <div className="flex items-center gap-1.5">
          <Button variant="ghost" size="sm" asChild>
            <Link href={loginPath("/creator")}>{t("common.login")}</Link>
          </Button>
          <Button size="sm" asChild className="bg-[#7c3aed] font-semibold text-[#17131f] shadow-none hover:bg-[#6d28d9]">
            <Link href={registerPath("/creator")}>{t("common.register")}</Link>
          </Button>
        </div>
      )}
    </div>
  );
}

function useNavItems() {
  const t = useT();
  const pathname = usePathname();

  return [
    { href: "/", label: t("shell.home"), active: pathname === "/" },
    { href: "/creator", label: t("nav.home"), active: pathname === "/creator" || pathname.startsWith("/creator/") },
    { href: "/projects", label: t("nav.projects"), active: pathname === "/projects" || pathname.startsWith("/projects/") },
    { href: "/library", label: t("nav.library"), active: pathname === "/library" },
    { href: "/quick-episode", label: t("shell.quickEpisodeMode"), active: pathname === "/quick-episode" },
    { href: "/market", label: t("nav.market"), active: pathname === "/market" },
    { href: "/community", label: t("nav.community"), active: pathname === "/community" },
  ];
}

function TopNav({ studioMode = false }: { studioMode?: boolean }) {
  const t = useT();
  const items = useNavItems();
  const [mobileOpen, setMobileOpen] = useState(false);

  return (
    <header className="pf-nav">
      <div className="pf-nav-left">
        <Link href="/" className="flex items-center gap-2.5">
          <span className="relative flex size-8 shrink-0 items-center justify-center overflow-hidden rounded-lg bg-[#17131f]">
            <Image
              src="/brand-icon.png"
              alt={`${APP_NAME} logo`}
              width={32}
              height={32}
              className="size-8 object-cover"
              priority
            />
          </span>
          <span className="hidden text-sm font-extrabold tracking-wide text-[#17131f] sm:inline">
            DREAMREEL
          </span>
        </Link>
        {studioMode && (
          <Button variant="ghost" size="sm" asChild className="ml-1 text-[var(--pf-muted)]">
            <Link href="/projects">← {t("nav.projects")}</Link>
          </Button>
        )}
        <Button
          type="button"
          variant="ghost"
          size="icon"
          className="ml-1 size-9 md:hidden"
          aria-label="Menu"
          onClick={() => setMobileOpen((v) => !v)}
        >
          {mobileOpen ? <X className="size-4" /> : <Menu className="size-4" />}
        </Button>
      </div>

      {!studioMode && (
        <nav className="pf-nav-center" aria-label="Main">
          {items.map((item) => (
            <Link key={item.href} href={item.href} className={cn(item.active && "active")}>
              {item.label}
            </Link>
          ))}
        </nav>
      )}

      <div className="pf-nav-right">
        <HeaderActions />
      </div>

      {mobileOpen && !studioMode && (
        <div className="absolute left-0 right-0 top-[var(--pf-nav-h)] z-50 border-b border-[var(--pf-line)] bg-white px-4 py-3 shadow-sm md:hidden">
          <nav className="flex flex-col gap-1">
            {items.map((item) => (
              <Link
                key={item.href}
                href={item.href}
                onClick={() => setMobileOpen(false)}
                className={cn(
                  "rounded-lg px-3 py-2.5 text-sm font-medium",
                  item.active ? "bg-[#f7ffe8] text-[#17131f]" : "text-[var(--pf-muted)] hover:bg-[#f8f7fc]",
                )}
              >
                {item.label}
              </Link>
            ))}
          </nav>
        </div>
      )}
    </header>
  );
}

function ShellInner({ children }: { children: React.ReactNode }) {
  const { user, loading } = useAuth();
  const pathname = usePathname();
  const router = useRouter();
  const guestOnHome = !user && isPublicPath(pathname);
  const isStudio = pathname.startsWith("/studio");

  useEffect(() => {
    if (loading || user) return;
    if (AUTH_PATHS.some((p) => pathname.startsWith(p))) return;
    if (isPublicPath(pathname)) return;
    router.replace(loginPath("/"));
  }, [loading, user, pathname, router]);

  if (loading) {
    return <DfPageLoading variant="fullscreen" />;
  }

  if (!user && !guestOnHome) {
    return <DfPageLoading variant="fullscreen" />;
  }

  return (
    <div className="pf-shell flex h-svh flex-col overflow-hidden">
      <TopNav studioMode={isStudio} />
      <main className={cn("min-h-0 flex-1", isStudio ? "overflow-hidden" : "df-scrollbar overflow-auto")}>
        {children}
      </main>
    </div>
  );
}

export function AppShell({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();
  const isAuth = AUTH_PATHS.some((p) => pathname.startsWith(p));
  const isBare = BARE_PATHS.some((p) => pathname.startsWith(p));
  const isLanding = pathname === "/";

  if (isAuth || isBare || isLanding) {
    return <>{children}</>;
  }

  return (
    <CurrentProjectProvider>
      <ShellInner>{children}</ShellInner>
    </CurrentProjectProvider>
  );
}
