"use client";

import { useEffect, useState } from "react";
import { fetchAdminGenerations } from "@/lib/api";
import type { GenerationJobRecord } from "@dreamreel/shared-types";
import { MonitorUp } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";

export default function AdminGenerationsPage() {
  const [items, setItems] = useState<GenerationJobRecord[]>([]);

  useEffect(() => {
    fetchAdminGenerations(0, 50).then((res) => setItems(res.data.items)).catch(() => setItems([]));
  }, []);

  return (
    <div className="space-y-6">
      <section className="geo-page-header rounded-3xl p-6"><p className="geo-gradient-text text-xs font-bold uppercase tracking-[.18em]">GENERATION MONITOR</p><h1 className="mt-2 text-3xl font-bold">生成记录</h1><p className="mt-2 text-sm text-muted-foreground">监控平台模型调用与生成任务状态</p></section>
      <Card className="geo-glass border-white/65">
        <CardHeader><CardTitle className="flex items-center gap-2 text-base"><MonitorUp className="size-4 text-primary" />任务记录</CardTitle></CardHeader>
        <CardContent className="overflow-x-auto">
        <Table>
          <TableHeader><TableRow><TableHead>类型</TableHead><TableHead>模型</TableHead><TableHead>状态</TableHead><TableHead>提示词</TableHead><TableHead>时间</TableHead></TableRow></TableHeader>
          <TableBody>
            {items.map((item) => (
              <TableRow key={item.id}>
                <TableCell><Badge variant="outline">{item.mediaType}</Badge></TableCell><TableCell>{item.model}</TableCell>
                <TableCell><Badge variant={item.status === "COMPLETED" ? "default" : item.status === "FAILED" ? "destructive" : "secondary"}>{item.status}</Badge></TableCell>
                <TableCell className="max-w-xs truncate">{item.prompt}</TableCell><TableCell className="text-muted-foreground">{new Date(item.createdAt).toLocaleString()}</TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
        </CardContent>
      </Card>
    </div>
  );
}
