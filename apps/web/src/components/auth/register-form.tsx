"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { useState } from "react";
import { useAuth } from "@/components/auth/auth-provider";
import { LanguageSwitcher } from "@/components/i18n/language-switcher";
import { useT } from "@/i18n/locale-provider";
import { loginPath } from "@/lib/auth-redirect";
import { mapAuthErrorMessage, ApiError } from "@/lib/api-error";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";

export function RegisterForm() {
  const { register } = useAuth();
  const t = useT();
  const router = useRouter();
  const searchParams = useSearchParams();
  const redirectTo = searchParams.get("redirect");
  const [displayName, setDisplayName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setLoading(true);
    setError(null);
    try {
      await register(email, password, displayName);
      router.push(redirectTo && redirectTo.startsWith("/") ? redirectTo : "/");
      router.refresh();
    } catch (err) {
      const status = err instanceof ApiError ? err.status : 0;
      const raw = err instanceof Error ? err.message : t("auth.registerFailed");
      setError(mapAuthErrorMessage(raw, status || 400, {
        rejected: t("auth.loginRejected"),
        invalidCredentials: t("auth.invalidRegistration"),
        defaultMessage: t("auth.registerFailed"),
      }));
    } finally {
      setLoading(false);
    }
  }

  const afterRegister = redirectTo && redirectTo.startsWith("/") ? redirectTo : "/";

  return (
    <div className="relative w-full">
      <div className="absolute -top-10 right-0">
        <LanguageSwitcher />
      </div>
      <form onSubmit={handleSubmit} className="space-y-4">
        <div className="space-y-2">
          <Label htmlFor="register-name">{t("auth.displayName")}</Label>
          <Input id="register-name" value={displayName} onChange={(e) => setDisplayName(e.target.value)} placeholder={t("auth.displayName")} required className="h-11 bg-[#f8f7fc]" />
        </div>
        <div className="space-y-2">
          <Label htmlFor="register-email">{t("auth.email")}</Label>
          <Input id="register-email" type="email" value={email} onChange={(e) => setEmail(e.target.value)} placeholder={t("auth.email")} required className="h-11 bg-[#f8f7fc]" />
        </div>
        <div className="space-y-2">
          <Label htmlFor="register-password">{t("auth.password")}</Label>
          <Input id="register-password" type="password" value={password} onChange={(e) => setPassword(e.target.value)} placeholder={t("auth.passwordHint")} required minLength={6} className="h-11 bg-[#f8f7fc]" />
        </div>
        {error && <p className="text-sm text-[var(--df-danger)]">{error}</p>}
        <Button type="submit" disabled={loading} className="h-11 w-full bg-[#7c3aed] font-semibold text-[#17131f] shadow-none hover:bg-[#6d28d9]">
          {loading ? t("auth.registering") : t("common.register")}
        </Button>
      </form>
      <p className="mt-5 text-center text-sm text-[var(--pf-muted)]">
        {t("auth.hasAccount")}{" "}
        <Link href={loginPath(afterRegister)} className="font-semibold text-[#7c3aed] hover:underline">
          {t("auth.goLogin")}
        </Link>
      </p>
    </div>
  );
}
