"use client";

import { useEffect, useState } from "react";
import { fetchAdminProjects } from "@/lib/api";
import type { Project } from "@dreamreel/shared-types";
import { FolderKanban } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";

export default function AdminProjectsPage() {
  const [projects, setProjects] = useState<Project[]>([]);

  useEffect(() => {
    fetchAdminProjects(0, 50).then((res) => setProjects(res.data.items)).catch(() => setProjects([]));
  }, []);

  return (
    <div className="space-y-6">
      <section className="geo-page-header rounded-3xl p-6"><p className="geo-gradient-text text-xs font-bold uppercase tracking-[.18em]">PROJECT OPERATIONS</p><h1 className="mt-2 text-3xl font-bold">项目管理</h1><p className="mt-2 text-sm text-muted-foreground">查看平台所有项目与最近更新时间</p></section>
      <Card className="geo-glass border-white/65">
        <CardHeader><CardTitle className="flex items-center gap-2 text-base"><FolderKanban className="size-4 text-primary" />项目列表</CardTitle></CardHeader>
        <CardContent className="overflow-x-auto">
        <Table>
          <TableHeader><TableRow><TableHead>名称</TableHead><TableHead>类型</TableHead><TableHead>更新时间</TableHead></TableRow></TableHeader>
          <TableBody>
            {projects.map((p) => (
              <TableRow key={p.id}><TableCell className="font-medium">{p.name}</TableCell><TableCell><Badge variant="secondary">{p.type}</Badge></TableCell><TableCell className="text-muted-foreground">{new Date(p.updatedAt).toLocaleString()}</TableCell></TableRow>
            ))}
          </TableBody>
        </Table>
        </CardContent>
      </Card>
    </div>
  );
}
