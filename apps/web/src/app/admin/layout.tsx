"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useEffect } from "react";
import { FolderKanban, LayoutDashboard, LogOut, MonitorUp, Users } from "lucide-react";
import { BrandLogo } from "@/components/brand/logo";
import { useAuth } from "@/components/auth/auth-provider";
import { Avatar, AvatarFallback } from "@/components/ui/avatar";
import {
  Sidebar,
  SidebarContent,
  SidebarFooter,
  SidebarGroup,
  SidebarGroupContent,
  SidebarGroupLabel,
  SidebarHeader,
  SidebarInset,
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
  SidebarProvider,
  SidebarRail,
  SidebarTrigger,
} from "@/components/ui/sidebar";

const NAV = [
  { href: "/admin", label: "仪表盘", icon: LayoutDashboard },
  { href: "/admin/users", label: "用户管理", icon: Users },
  { href: "/admin/projects", label: "项目管理", icon: FolderKanban },
  { href: "/admin/generations", label: "生成记录", icon: MonitorUp },
];

export default function AdminLayout({ children }: { children: React.ReactNode }) {
  const { user, loading, isAdmin, logout } = useAuth();
  const router = useRouter();
  const pathname = usePathname();

  useEffect(() => {
    if (!loading && !user) router.replace("/login");
    if (!loading && user && !isAdmin) router.replace("/");
  }, [loading, user, isAdmin, router]);

  if (loading || !user || !isAdmin) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-zinc-50 text-sm text-zinc-500">
        加载中...
      </div>
    );
  }

  return (
    <SidebarProvider>
      <Sidebar className="border-r border-white/60 [&_[data-slot=sidebar-inner]]:bg-[linear-gradient(180deg,rgba(255,255,255,.9),rgba(240,248,255,.78))] [&_[data-slot=sidebar-inner]]:backdrop-blur-2xl">
        <SidebarHeader className="border-b border-sidebar-border p-4">
          <BrandLogo href="/admin" showSubtitle="管理后台" />
        </SidebarHeader>
        <SidebarContent className="p-2">
          <SidebarGroup>
            <SidebarGroupLabel className="text-primary">平台管理</SidebarGroupLabel>
            <SidebarGroupContent>
              <SidebarMenu>
                {NAV.map((item) => {
                  const Icon = item.icon;
                  return (
                    <SidebarMenuItem key={item.href}>
                      <SidebarMenuButton asChild isActive={pathname === item.href} tooltip={item.label} className="rounded-xl data-[active=true]:border data-[active=true]:border-emerald-500/20 data-[active=true]:bg-gradient-to-r data-[active=true]:from-emerald-500/15 data-[active=true]:to-sky-500/10 data-[active=true]:text-primary">
                        <Link href={item.href}><Icon /><span>{item.label}</span></Link>
                      </SidebarMenuButton>
                    </SidebarMenuItem>
                  );
                })}
              </SidebarMenu>
            </SidebarGroupContent>
          </SidebarGroup>
        </SidebarContent>
        <SidebarFooter className="border-t border-sidebar-border p-3">
          <SidebarMenu>
            <SidebarMenuItem><SidebarMenuButton asChild tooltip="返回前台"><Link href="/"><LayoutDashboard /><span>返回前台</span></Link></SidebarMenuButton></SidebarMenuItem>
            <SidebarMenuItem><SidebarMenuButton onClick={logout} tooltip="退出"><LogOut /><span>退出</span></SidebarMenuButton></SidebarMenuItem>
          </SidebarMenu>
        </SidebarFooter>
        <SidebarRail />
      </Sidebar>
      <SidebarInset className="bg-transparent">
        <header className="sticky top-0 z-30 flex h-14 items-center border-b border-white/60 bg-white/55 px-4 backdrop-blur-xl">
          <SidebarTrigger />
          <div className="ml-auto flex items-center gap-2">
            <Avatar className="size-8"><AvatarFallback className="bg-secondary text-primary">{(user.displayName || user.email).slice(0, 1)}</AvatarFallback></Avatar>
            <span className="text-sm font-medium">{user.displayName}</span>
          </div>
        </header>
        <main className="mx-auto w-full max-w-7xl p-4 md:p-8 lg:p-10">{children}</main>
      </SidebarInset>
    </SidebarProvider>
  );
}
