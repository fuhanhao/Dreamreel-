"use client";

import { Suspense } from "react";
import Image from "next/image";
import Link from "next/link";
import { LoginForm } from "@/components/auth/login-form";
import { useT } from "@/i18n/locale-provider";
import { APP_NAME } from "@/lib/brand";

export default function LoginPage() {
  const t = useT();

  return (
    <div className="grid min-h-svh lg:grid-cols-2">
      <section className="flex min-h-svh flex-col bg-white px-6 py-8 md:px-12 lg:px-16">
        <Link href="/" className="mb-10 flex items-center gap-2.5">
          <span className="relative flex size-8 overflow-hidden rounded-lg bg-[#17131f]">
            <Image src="/brand-icon.png" alt="" width={32} height={32} className="size-8 object-cover" />
          </span>
          <span className="text-sm font-extrabold tracking-wide">DREAMREEL</span>
        </Link>
        <div className="my-auto w-full max-w-md">
          <h1 className="text-3xl font-extrabold tracking-tight text-[#17131f]">{t("auth.loginHeroTitle")}</h1>
          <p className="mt-2 text-sm text-[var(--pf-muted)]">
            {APP_NAME} · {t("brand.tagline")}
          </p>
          <div className="mt-8">
            <Suspense fallback={<div className="text-sm text-muted-foreground">{t("common.loading")}</div>}>
              <LoginForm />
            </Suspense>
          </div>
        </div>
      </section>
      <section
        className="relative hidden overflow-hidden lg:block"
        style={{
          background:
            "radial-gradient(ellipse at 20% 50%, rgba(182,255,0,0.18), transparent 55%), #0c1008",
        }}
      >
        <div
          className="absolute inset-0 opacity-40"
          style={{
            backgroundImage:
              "repeating-linear-gradient(0deg, transparent, transparent 3px, rgba(255,255,255,0.03) 3px, rgba(255,255,255,0.03) 4px)",
          }}
        />
      </section>
    </div>
  );
}
