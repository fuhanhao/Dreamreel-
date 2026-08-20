"use client";

import { useCallback, useEffect, useState } from "react";
import { fetchAdminUsers, updateAdminUser } from "@/lib/api";
import type { User } from "@dreamreel/shared-types";
import { Search, Users } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";

export default function AdminUsersPage() {
  const [users, setUsers] = useState<User[]>([]);
  const [keyword, setKeyword] = useState("");

  const load = useCallback(async () => {
    const res = await fetchAdminUsers(0, 50, keyword || undefined);
    setUsers(res.data.items);
  }, [keyword]);

  useEffect(() => {
    const timer = window.setTimeout(() => {
      void load().catch(() => setUsers([]));
    }, 0);
    return () => window.clearTimeout(timer);
  }, [load]);

  async function toggleStatus(user: User) {
    const next = user.status === "ACTIVE" ? "DISABLED" : "ACTIVE";
    await updateAdminUser(user.id, { status: next });
    await load();
  }

  return (
    <div className="space-y-6">
      <section className="geo-page-header rounded-3xl p-6">
      <div className="flex flex-wrap items-center justify-between gap-4">
        <div><p className="geo-gradient-text text-xs font-bold uppercase tracking-[.18em]">USER MANAGEMENT</p><h1 className="mt-2 text-3xl font-bold">用户管理</h1></div>
        <div className="flex gap-2">
          <Input
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
            placeholder="搜索邮箱或昵称"
            className="bg-white/65"
          />
          <Button onClick={() => load()} className="bg-gradient-to-r from-emerald-600 to-cyan-600"><Search />搜索</Button>
        </div>
      </div>
      </section>
      <Card className="geo-glass border-white/65">
        <CardHeader><CardTitle className="flex items-center gap-2 text-base"><Users className="size-4 text-primary" />用户列表</CardTitle></CardHeader>
        <CardContent className="overflow-x-auto">
        <Table>
          <TableHeader><TableRow>
              <TableHead>昵称</TableHead><TableHead>邮箱</TableHead><TableHead>角色</TableHead><TableHead>状态</TableHead><TableHead>API Key</TableHead><TableHead>操作</TableHead>
          </TableRow></TableHeader>
          <TableBody>
            {users.map((user) => (
              <TableRow key={user.id}>
                <TableCell className="font-medium">{user.displayName}</TableCell><TableCell>{user.email}</TableCell><TableCell><Badge variant="outline">{user.role}</Badge></TableCell>
                <TableCell><Badge variant={user.status === "ACTIVE" ? "default" : "secondary"}>{user.status}</Badge></TableCell>
                <TableCell><Badge variant={user.hasTokenfreeApiKey ? "secondary" : "outline"}>{user.hasTokenfreeApiKey ? "已配置" : "未配置"}</Badge></TableCell>
                <TableCell><Button variant="ghost" size="sm" onClick={() => toggleStatus(user)}>{user.status === "ACTIVE" ? "禁用" : "启用"}</Button></TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
        </CardContent>
      </Card>
    </div>
  );
}
