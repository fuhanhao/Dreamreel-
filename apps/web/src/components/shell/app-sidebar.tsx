"use client";

import Image from "next/image";
import Link from "next/link";
import { usePathname, useSearchParams } from "next/navigation";
import {
  BookOpen,
  ChevronRight,
  Clapperboard,
  Crown,
  Film,
  FolderKanban,
  GalleryVerticalEnd,
  ImageIcon,
  KeyRound,
  LayoutDashboard,
  MessageSquareText,
  PackageOpen,
  PanelsTopLeft,
  Plus,
  Settings2,
  Sparkles,
  Store,
  Video,
  WandSparkles,
} from "lucide-react";
import {
  Sidebar,
  SidebarContent,
  SidebarFooter,
  SidebarGroup,
  SidebarGroupContent,
  SidebarGroupLabel,
  SidebarHeader,
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
  SidebarRail,
} from "@/components/ui/sidebar";
import { useT } from "@/i18n/locale-provider";
import { APP_NAME, DOCS_REPO_URL } from "@/lib/brand";
import { useCurrentProject } from "./current-project";

export function AppSidebar({ isAdmin = false }: { isAdmin?: boolean }) {
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const t = useT();
  const { currentProjectId } = useCurrentProject();
  const creatorMode = searchParams.get("mode");
  const videoSubMode = searchParams.get("videoSubMode");
  const entry = searchParams.get("entry");
  const canvasHref = currentProjectId
    ? `/studio/${currentProjectId}?mode=canvas`
    : "/projects?entry=canvas";

  const groups = [
    {
      label: t("shell.workspace"),
      icon: LayoutDashboard,
      items: [
        { href: "/", label: t("shell.home"), icon: LayoutDashboard, active: pathname === "/" },
        { href: "/creator", label: t("nav.home"), icon: LayoutDashboard, active: pathname === "/creator" && !creatorMode },
        { href: "/projects", label: t("nav.projects"), icon: FolderKanban, active: pathname === "/projects" && !entry },
        { href: "/projects?entry=dramaforge", label: t("creator.newProject").replace(/^\+\s*/, ""), icon: Plus, active: false },
        { href: DOCS_REPO_URL, label: t("nav.docs"), icon: BookOpen, active: false },
      ],
    },
    {
      label: t("shell.videoGeneration"),
      icon: Video,
      items: [
        {
          href: "/creator?mode=video&videoSubMode=text-to-video#creation-workbench",
          label: t("shell.textToVideo"),
          icon: Video,
          active: pathname === "/creator" && creatorMode === "video" && videoSubMode === "text-to-video",
        },
        {
          href: "/creator?mode=video&videoSubMode=image-to-video#creation-workbench",
          label: t("shell.imageToVideo"),
          icon: ImageIcon,
          active: pathname === "/creator" && creatorMode === "video" && videoSubMode === "image-to-video",
        },
        {
          href: "/creator?mode=video&videoSubMode=video-to-video#creation-workbench",
          label: t("shell.videoToVideo"),
          icon: Film,
          active: pathname === "/creator" && creatorMode === "video" && videoSubMode === "video-to-video",
        },
      ],
    },
    {
      label: t("shell.imageAndTextTools"),
      icon: WandSparkles,
      items: [
        {
          href: "/creator?mode=image&imageSubMode=text-to-image#creation-workbench",
          label: t("shell.imageGeneration"),
          icon: ImageIcon,
          active: pathname === "/creator" && creatorMode === "image",
        },
        {
          href: "/creator?mode=prompt#creation-workbench",
          label: t("shell.promptOptimization"),
          icon: WandSparkles,
          active: pathname === "/creator" && creatorMode === "prompt",
        },
      ],
    },
    {
      label: t("shell.professionalWorkflows"),
      icon: Clapperboard,
      items: [
        {
          href: "/projects?entry=dramaforge",
          label: t("shell.dramaCreation"),
          icon: Clapperboard,
          active:
            (pathname === "/projects" && entry === "dramaforge")
            || (pathname.startsWith("/studio") && searchParams.get("mode") !== "canvas"),
        },
        {
          href: "/quick-episode",
          label: t("shell.quickEpisodeMode"),
          icon: Sparkles,
          active: pathname === "/quick-episode",
        },
        {
          href: canvasHref,
          label: t("shell.canvasWorkflow"),
          icon: PanelsTopLeft,
          active:
            (pathname.startsWith("/studio") && searchParams.get("mode") === "canvas")
            || (pathname === "/projects" && entry === "canvas"),
        },
      ],
    },
    {
      label: t("shell.contentAndAssets"),
      icon: PackageOpen,
      items: [
        { href: "/library", label: t("nav.library"), icon: GalleryVerticalEnd, active: pathname === "/library" },
        { href: "/market", label: t("nav.market"), icon: Store, active: pathname === "/market" },
        { href: "/community", label: t("nav.community"), icon: MessageSquareText, active: pathname === "/community" },
      ],
    },
  ];

  return (
    <Sidebar
      collapsible="icon"
      className="border-r border-border bg-white"
    >
      <SidebarHeader className="border-b border-sidebar-border p-3">
        <SidebarMenu>
          <SidebarMenuItem>
            <SidebarMenuButton asChild size="lg" className="h-auto rounded-lg py-2">
              <Link href="/">
                <span className="relative flex size-9 shrink-0 items-center justify-center overflow-hidden rounded-lg bg-[#17131f]">
                  <Image
                    src="/brand-icon.png"
                    alt={`${APP_NAME} logo`}
                    width={40}
                    height={40}
                    className="size-9 object-cover"
                    priority
                  />
                </span>
                <span className="grid flex-1 text-left leading-tight">
                  <span className="truncate text-[10px] font-bold uppercase tracking-[.18em] text-[#6d28d9]">
                    DREAMREEL
                  </span>
                  <span className="truncate text-base font-extrabold text-[#17131f]">{APP_NAME}</span>
                </span>
              </Link>
            </SidebarMenuButton>
          </SidebarMenuItem>
        </SidebarMenu>
        <div className="mt-2 rounded-lg border border-[#e5e7eb] bg-[#f8f7fc] p-3 group-data-[collapsible=icon]:hidden">
          <p className="text-sm font-semibold">{t("shell.creativeSpace")}</p>
          <p className="mt-1.5 text-xs text-muted-foreground">{t("shell.creativeSpaceTagline")}</p>
        </div>
      </SidebarHeader>

      <SidebarContent className="px-2 py-3">
        {groups.map((group) => (
          <SidebarGroup key={group.label} className="py-2">
            <SidebarGroupLabel className="gap-2 font-semibold text-[#62666d]">
              <group.icon className="size-3.5" />
              {group.label}
            </SidebarGroupLabel>
            <SidebarGroupContent>
              <SidebarMenu>
                {group.items.map((item) => {
                  const active = item.active;
                  const Icon = item.icon;
                  return (
                    <SidebarMenuItem key={`${group.label}-${item.label}`}>
                      <SidebarMenuButton
                        asChild
                        isActive={active}
                        tooltip={item.label}
                        className="rounded-lg border-l-2 border-l-transparent data-[active=true]:border-[#dce3c9] data-[active=true]:border-l-[#8b5cf6] data-[active=true]:bg-[#f3e8ff] data-[active=true]:font-semibold data-[active=true]:text-[#17131f]"
                      >
                      {item.href.startsWith("http") ? (
                        <a href={item.href} target="_blank" rel="noreferrer">
                          <Icon />
                          <span>{item.label}</span>
                        </a>
                      ) : (
                        <Link href={item.href}>
                          <Icon />
                          <span>{item.label}</span>
                        </Link>
                      )}
                      </SidebarMenuButton>
                    </SidebarMenuItem>
                  );
                })}
              </SidebarMenu>
            </SidebarGroupContent>
          </SidebarGroup>
        ))}
      </SidebarContent>

      <SidebarFooter className="border-t border-sidebar-border bg-white p-3">
        <div className="mb-2 space-y-4 overflow-hidden rounded-lg border border-[#e5e7eb] bg-[#f8f7fc] p-3 group-data-[collapsible=icon]:hidden">
          <div className="flex items-center justify-between gap-2">
            <span className="flex items-center gap-1.5 text-xs font-bold text-slate-800">
              <span className="grid size-6 place-items-center rounded-md bg-[#7c3aed] text-[#17131f]">
                <Crown className="size-3.5" />
              </span>
              {t("creator.proPlan")}
            </span>
            <span className="rounded-full border border-[#ddd6fe] bg-[#f3e8ff] px-2 py-0.5 text-[10px] font-semibold text-[#5b21b6]">
              {t("creator.upgrade")}
            </span>
          </div>
          <div>
            <div className="mb-1.5 flex justify-between gap-2 text-[10px] text-slate-500">
              <span className="font-medium">{t("creator.renderHours")}</span>
              <span><strong className="font-semibold text-slate-700">120</strong> / 300 h</span>
            </div>
            <div className="h-1.5 overflow-hidden rounded-full bg-slate-200/80">
              <div className="h-full w-[40%] rounded-full bg-[#7c3aed]" />
            </div>
          </div>
          <div>
            <div className="mb-1.5 flex justify-between gap-2 text-[10px] text-slate-500">
              <span className="font-medium">{t("creator.storage")}</span>
              <span><strong className="font-semibold text-slate-700">86.4</strong> / 200 GB</span>
            </div>
            <div className="h-1.5 overflow-hidden rounded-full bg-slate-200/80">
              <div className="h-full w-[43%] rounded-full bg-[#17131f]" />
            </div>
          </div>
          <Link
            href="/creator?openApiKey=1"
            className="-mx-1 -mb-1 flex h-9 items-center gap-2 rounded-lg border border-[#e5e7eb] bg-white px-3 text-xs font-medium text-slate-700 transition hover:bg-[#f3e8ff] hover:text-[#17131f]"
          >
            <KeyRound className="size-3.5 text-[#6d28d9]" />
            <span>{t("creator.apiKeySettings")}</span>
            <ChevronRight className="ml-auto size-3.5 text-slate-400" />
          </Link>
        </div>
        <SidebarMenu>
          <SidebarMenuItem className="hidden group-data-[collapsible=icon]:block">
            <SidebarMenuButton
              asChild
              tooltip={t("creator.apiKeySettings")}
            >
              <Link href="/creator?openApiKey=1">
                <KeyRound />
                <span>{t("creator.apiKeySettings")}</span>
              </Link>
            </SidebarMenuButton>
          </SidebarMenuItem>
          {isAdmin && (
            <SidebarMenuItem>
              <SidebarMenuButton asChild tooltip={t("common.admin")}>
                <Link href="/admin">
                  <Settings2 />
                  <span>{t("common.admin")}</span>
                </Link>
              </SidebarMenuButton>
            </SidebarMenuItem>
          )}
        </SidebarMenu>
      </SidebarFooter>
      <SidebarRail />
    </Sidebar>
  );
}
