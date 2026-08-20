"use client";

import { useEffect, useState } from "react";
import { fetchAdminStats } from "@/lib/api";
import type { AdminStats } from "@dreamreel/shared-types";
import { CheckCircle2, FolderKanban, MonitorUp, ShieldAlert, UserCheck, Users } from "lucide-react";
import { Card, CardContent } from "@/components/ui/card";

export default function AdminDashboardPage() {
  const [stats, setStats] = useState<AdminStats | null>(null);

  useEffect(() => {
    fetchAdminStats().then((res) => setStats(res.data)).catch(() => setStats(null));
  }, []);

  const cards = stats
    ? [
        { label: "总用户", value: stats.totalUsers, icon: Users },
        { label: "活跃用户", value: stats.activeUsers, icon: UserCheck },
        { label: "总项目", value: stats.totalProjects, icon: FolderKanban },
        { label: "生成任务", value: stats.totalGenerations, icon: MonitorUp },
        { label: "已完成", value: stats.completedGenerations, icon: CheckCircle2 },
        { label: "失败", value: stats.failedGenerations, icon: ShieldAlert },
      ]
    : [];

  return (
    <div className="space-y-6">
      <section className="geo-page-header rounded-3xl p-6 md:p-8">
        <p className="geo-gradient-text text-xs font-bold uppercase tracking-[.18em]">ADMIN CONSOLE</p>
        <h1 className="mt-2 text-3xl font-bold">仪表盘</h1>
        <p className="mt-2 text-sm text-muted-foreground">平台运营数据概览</p>
      </section>
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {cards.map((card, index) => {
          const Icon = card.icon;
          return (
          <Card key={card.label} className="geo-glass geo-metric border-white/65">
            <CardContent className="p-5 pl-6">
              <div className="flex items-start justify-between">
                <p className="text-sm text-muted-foreground">{card.label}</p>
                <span className={`grid size-10 place-items-center rounded-xl bg-gradient-to-br text-white ${index % 3 === 0 ? "from-emerald-400 to-cyan-500" : index % 3 === 1 ? "from-sky-500 to-cyan-300" : "from-orange-500 to-amber-400"}`}><Icon className="size-4" /></span>
              </div>
              <p className="mt-3 text-3xl font-bold">{card.value}</p>
            </CardContent>
          </Card>
          );
        })}
      </div>
    </div>
  );
}
