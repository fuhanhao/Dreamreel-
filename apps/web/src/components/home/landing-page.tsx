"use client";

import Link from "next/link";
import Image from "next/image";
import {
  ArrowRight,
  Check,
  Clapperboard,
  Film,
  ImageIcon,
  Layers,
  PanelsTopLeft,
  Play,
  Sparkles,
  WandSparkles,
  ChevronRight,
  Star,
  Zap,
  Users,
  Clock,
} from "lucide-react";
import { APP_NAME } from "@/lib/brand";
import { useAuth } from "@/components/auth/auth-provider";
import { loginPath, registerPath } from "@/lib/auth-redirect";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { useLocale } from "@/i18n/locale-provider";

const CAPABILITY_ICONS = [Film, ImageIcon, Clapperboard, PanelsTopLeft, WandSparkles, Layers];
const HIGHLIGHT_ICONS = [Star, Zap, Users, Clock];

const LANDING_CONTENT = {
  zh: {
    nav: ["核心能力", "工作流", "常见问题"],
    enterCreator: "进入创作中心",
    heroBadge: "AI 驱动的短剧创作平台",
    heroTitle: "把灵感，映成梦",
    heroAccent: "让每个故事都有光影",
    heroDescription: `${APP_NAME} 是你的 AI 视频创作伙伴。从主题输入到成片发布，只需几步，轻松完成短片创作。`,
    startCreating: "开始创作",
    heroPills: ["主题解析", "镜头生成", "配音配乐", "输出成片"],
    highlights: [
      ["主题解析", "AI 智能解析主题，自动生成大纲与镜头"],
      ["镜头生成", "从文字到画面，自动匹配角色与场景"],
      ["角色一致", "定妆参考 + 音色样本，锁定角色形象"],
      ["快速出片", "批量生成镜头，实时追踪进度"],
    ],
    capabilityBadge: "核心能力",
    capabilityTitle: "一站式创作能力",
    capabilityDescription: "从简单的图文生成，到完整的短剧生产流水线，都集中在一个可平滑扩展的工作台里。",
    capabilities: [
      ["文生视频", "用文字描述直接生成镜头视频，支持多种 Seedance 模型切换。"],
      ["图生视频", "以上传图片为参考，让画面动起来，保持角色与场景一致。"],
      ["短剧流水线", "从故事正文到剧本、资产、镜头、视频、合成导出的完整链路。"],
      ["画布工作流", "React Flow 节点式画布，可从流水线一键同步镜头与资产节点。"],
      ["提示词优化", "一键扩写与优化中文提示词，提升生成质量与画面一致性。"],
      ["版本管理", "定妆图、镜头、视频均保留历史版本，可随时回退与切换。"],
    ],
    workflowBadge: "推荐工作流",
    workflowTitle: "5 步从 0 到 1 生成短剧",
    workflow: [
      ["01", "写故事", "填入原文、世界观、风格与画幅，作为全剧创作底稿。"],
      ["02", "定剧本", "正文结构化为场次与镜头，写入镜头库并确认剧本。"],
      ["03", "建资产", "提取角色/场景/道具，生成定妆图与角色音色。"],
      ["04", "出成片", "批量生成镜头视频，实时跟踪任务进度并处理失败项。"],
      ["05", "AI 剪辑", "按时间轴拼接镜头，导出成片或剪映工程。"],
    ],
    whyChoose: `为什么选择 ${APP_NAME}`,
    detailsTitle: "为专业创作而生的细节",
    features: ["角色 / 场景 / 道具统一资产库", "三选一定妆候选与版本回退", "角色音色样本与对白 TTS", "镜头尾帧自动衔接下一镜", "合成就绪检查与失败原因可视化", "任务队列支持取消、重试与清理", "多集管理与分集规划", "剪映草稿与项目包导出"],
    faqTitle: "常见问题",
    faqs: [
      ["短剧从 0 到 1 需要多久？", "取决于剧本长度与镜头数量。确认剧本和资产后，剩余镜头可以交给批量生成任务，全程在项目工作台内完成。"],
      ["角色一致性怎么保证？", "每个角色都有独立的定妆参考图与音色样本，生成视频时会按 @ImageN / @AudioN 绑定参考，锁定五官、服装与声线。"],
      ["生成结果可以导出吗？", "合成本集成片后可直接播放与下载，也支持导出剪映工程继续精剪，或导出项目 Zip 包归档。"],
    ],
    ctaTitle: "准备好开始你的第一部 AI 短剧了吗？",
    ctaDescription: `创建项目、填入故事，剩下的交给 ${APP_NAME} 流水线。`,
    createNow: "立即创建",
    footerTagline: "AI 短剧创作平台",
    officeAddress: "公司地址",
    addressLines: ["线上办公", "地址以官方公示为准"],
    contact: "联系我们",
    customerService: "商务合作",
    landline: "服务时间",
    email: "邮箱",
    icp: "ICP 备案（待更新）",
    publicSecurity: "公安备案（待更新）",
  },
  en: {
    nav: ["Capabilities", "Workflow", "FAQ"],
    enterCreator: "Open creation center",
    heroBadge: "AI-powered drama creation platform",
    heroTitle: "Turn inspiration into dream reels",
    heroAccent: "Let every story shine on screen",
    heroDescription: `${APP_NAME} is your AI video creation partner. Go from a theme to a published short in just a few steps.`,
    startCreating: "Start creating",
    heroPills: ["Theme analysis", "Shot generation", "Voice & music", "Final output"],
    highlights: [
      ["Theme analysis", "AI turns a theme into an outline and shot plan"],
      ["Shot generation", "Move from words to visuals with matched characters and scenes"],
      ["Character consistency", "Lock character identity with design and voice references"],
      ["Fast production", "Generate shots in batches and track progress live"],
    ],
    capabilityBadge: "Core capabilities",
    capabilityTitle: "Everything you need to create",
    capabilityDescription: "From quick image and video generation to a complete drama pipeline, everything lives in one extensible workspace.",
    capabilities: [
      ["Text to video", "Generate shot videos from text with a choice of Seedance models."],
      ["Image to video", "Animate reference images while preserving character and scene consistency."],
      ["Drama pipeline", "A complete path from source story through script, assets, shots, video, composition, and export."],
      ["Canvas workflow", "A React Flow canvas that can sync shot and asset nodes from the pipeline."],
      ["Prompt optimization", "Expand and refine prompts to improve generation quality and visual consistency."],
      ["Version management", "Keep and restore historical versions of designs, shots, and videos."],
    ],
    workflowBadge: "Recommended workflow",
    workflowTitle: "Create a drama in 5 steps",
    workflow: [
      ["01", "Write the story", "Add source text, worldbuilding, style, and aspect ratio as the creative foundation."],
      ["02", "Build the script", "Structure the text into scenes and shots, then review and lock the script."],
      ["03", "Create assets", "Extract characters, scenes, and props, then generate designs and character voices."],
      ["04", "Produce video", "Generate shot videos in batches, track jobs, and resolve failures."],
      ["05", "AI edit", "Assemble shots on a timeline and export a final video or JianYing project."],
    ],
    whyChoose: `Why choose ${APP_NAME}`,
    detailsTitle: "Details built for professional creation",
    features: ["Unified character, scene, and prop library", "Three design candidates with version rollback", "Character voice samples and dialogue TTS", "Automatic tail-frame continuity between shots", "Composition readiness checks and visible failure reasons", "Cancelable, retryable, cleanable task queues", "Multi-episode management and planning", "JianYing draft and project package export"],
    faqTitle: "Frequently asked questions",
    faqs: [
      ["How long does it take to create a drama?", "It depends on script length and shot count. Once the script and assets are confirmed, batch generation can handle the remaining shots inside the project workspace."],
      ["How do you keep characters consistent?", "Each character has an independent design reference and voice sample. Video generation binds them through @ImageN and @AudioN to preserve facial features, wardrobe, and voice."],
      ["Can I export generated results?", "Finished episodes can be played and downloaded directly. You can also export a JianYing project for detailed editing or archive the full project as a Zip package."],
    ],
    ctaTitle: "Ready to create your first AI drama?",
    ctaDescription: `Create a project, add your story, and let the ${APP_NAME} pipeline handle the rest.`,
    createNow: "Create now",
    footerTagline: "AI drama creation platform",
    officeAddress: "Company address",
    addressLines: ["Remote-first", "Address published on official site"],
    contact: "Contact us",
    customerService: "Partnership",
    landline: "Service hours",
    email: "Email",
    icp: "ICP filing (to be updated)",
    publicSecurity: "Public security filing (to be updated)",
  },
} as const;

export function LandingPage() {
  const { user } = useAuth();
  const { locale } = useLocale();
  const content = LANDING_CONTENT[locale];
  return (
    <div className="min-h-svh w-full bg-[#f8f7fc] text-[#17131f]">
      {/* ── 顶部导航 ── */}
      <header className="pf-nav sticky top-0 z-50">
        <div className="pf-nav-left">
          <Link href="/" className="flex items-center gap-2.5">
            <Image
              src="/brand-icon.png"
              alt={APP_NAME}
              width={32}
              height={32}
              className="size-8 rounded-lg object-cover"
            />
            <span className="text-sm font-extrabold tracking-wide text-[#17131f]">DREAMREEL</span>
          </Link>
        </div>
        <nav className="pf-nav-center">
          <a href="#capabilities">{content.nav[0]}</a>
          <a href="#workflow">{content.nav[1]}</a>
          <a href="#faq">{content.nav[2]}</a>
        </nav>
        <div className="pf-nav-right">
          {user ? (
            <Button asChild className="h-9 rounded-lg bg-[#7c3aed] px-4 text-sm font-semibold text-[#17131f] hover:bg-[#6d28d9]">
              <Link href="/creator">{content.enterCreator} <ArrowRight className="ml-1 size-3.5" /></Link>
            </Button>
          ) : (
              <>
                <Button asChild variant="ghost" className="h-9 rounded-lg text-sm font-medium text-[#62666d] hover:text-[#17131f]">
                  <Link href={loginPath("/creator")}>{locale === "zh" ? "登录" : "Sign in"}</Link>
                </Button>
                <Button asChild className="h-9 rounded-lg bg-[#7c3aed] px-4 text-sm font-semibold text-[#17131f] hover:bg-[#6d28d9]">
                  <Link href={registerPath("/creator")}>{locale === "zh" ? "注册" : "Sign up"}</Link>
                </Button>
              </>
            )}
        </div>
      </header>

      {/* ── Hero ── */}
      <section className="bg-[#f8f7fc]">
        <div className="mx-auto grid max-w-[1280px] gap-12 px-4 pb-24 pt-20 md:px-6 md:pb-32 md:pt-28 lg:grid-cols-[.9fr_1.1fr] lg:items-center">
          <div>
            <div className="mb-7 inline-flex items-center gap-2 rounded-full border border-[#dfe3e7] bg-white px-4 py-1.5 text-sm text-[#62666d]">
              <Sparkles className="size-3.5 text-[#8b5cf6]" />
              {content.heroBadge}
            </div>
            <h1 className="text-4xl font-extrabold leading-[1.08] tracking-[-.035em] text-[#17131f] md:text-5xl lg:text-6xl">
              {content.heroTitle}
              <br />
              <span className="decoration-[#7c3aed] decoration-[.22em] underline underline-offset-[-.08em]">
                {content.heroAccent}
              </span>
            </h1>
            <p className="mt-7 max-w-xl text-base leading-7 text-[#62666d] md:text-lg">
              {content.heroDescription}
            </p>
            <div className="mt-9 flex flex-wrap items-center gap-3">
              <Button asChild size="lg" className="h-12 rounded-lg bg-[#7c3aed] px-6 text-base font-semibold text-[#17131f] hover:bg-[#6d28d9]">
                <Link href="/projects?entry=dramaforge">
                  {content.startCreating} <ArrowRight className="ml-1.5 size-4" />
                </Link>
              </Button>
              <Button
                asChild
                size="lg"
                variant="outline"
                className="h-12 rounded-lg border-[#d8dce1] bg-white px-6 text-base font-medium text-[#17131f] hover:bg-[#f1f3f5]"
              >
                <Link href="/creator">
                  <Play className="mr-1.5 size-4" /> {content.enterCreator}
                </Link>
              </Button>
            </div>
            <div className="mt-9 flex flex-wrap items-center gap-x-5 gap-y-2 text-sm text-[#62666d]">
              {content.heroPills.map((pill) => (
                <span key={pill} className="inline-flex items-center gap-1.5">
                  <Check className="size-3.5 text-[#8b5cf6]" /> {pill}
                </span>
              ))}
            </div>
          </div>
          <div className="rounded-xl border border-black bg-[#17131f] p-3 text-white">
            <div className="flex items-center justify-between border-b border-white/10 px-2 pb-3 text-xs text-white/55">
              <span>DREAMREEL / STUDIO</span>
              <span className="rounded-full bg-[#7c3aed] px-2.5 py-1 font-bold text-[#17131f]">AI</span>
            </div>
            <div className="grid min-h-[390px] gap-3 pt-3 md:grid-cols-[150px_1fr]">
              <div className="space-y-2 rounded-lg border border-white/10 bg-white/[.04] p-3">
                {content.workflow.map(([step, title], index) => (
                  <div key={step} className={`flex items-center gap-2 rounded-md px-2 py-2 text-xs ${index === 1 ? "bg-[#7c3aed] font-semibold text-[#17131f]" : "text-white/55"}`}>
                    <span>{step}</span><span>{title}</span>
                  </div>
                ))}
              </div>
              <div className="grid gap-3">
                <div className="relative overflow-hidden rounded-lg border border-white/10 bg-black">
                  <div className="absolute inset-x-8 top-1/2 h-px bg-white/10" />
                  <div className="absolute inset-y-8 left-1/2 w-px bg-white/10" />
                  <div className="absolute bottom-4 left-4 rounded-md bg-white/10 px-3 py-2 text-xs text-white/65">{content.highlights[1][0]}</div>
                  <Play className="absolute left-1/2 top-1/2 size-12 -translate-x-1/2 -translate-y-1/2 rounded-full bg-[#7c3aed] p-3 text-[#17131f]" />
                </div>
                <div className="grid grid-cols-3 gap-2">
                  {content.highlights.slice(0, 3).map(([title]) => (
                    <div key={title} className="rounded-md border border-white/10 bg-white/[.04] p-3 text-xs text-white/60">{title}</div>
                  ))}
                </div>
              </div>
            </div>
          </div>
        </div>
      </section>

      {/* ── 亮点 ── */}
      <section className="bg-[#f8f7fc]">
        <div className="mx-auto max-w-[1280px] px-4 py-20 md:px-6 md:py-24">
          <div className="grid gap-10 sm:grid-cols-2 lg:grid-cols-4">
            {content.highlights.map(([title, desc], index) => {
              const Icon = HIGHLIGHT_ICONS[index];
              return (
              <div
                key={title}
                className="group rounded-xl border border-[#e5e7eb] bg-white p-8 transition hover:border-[#cbd0d6]"
              >
                <div className="mb-6 flex size-11 items-center justify-center rounded-lg bg-[#7c3aed] text-[#17131f]">
                  <Icon className="size-5" />
                </div>
                <h3 className="text-base font-bold text-[#17131f]">{title}</h3>
                <p className="mt-2 text-sm leading-6 text-[#62666d]">{desc}</p>
              </div>
              );
            })}
          </div>
        </div>
      </section>

      {/* ── 核心能力 ── */}
      <section className="bg-white" id="capabilities">
        <div className="mx-auto max-w-[1280px] px-4 py-24 md:px-6 md:py-28">
          <div className="mb-16 text-center">
            <Badge variant="secondary" className="mx-auto mb-4 w-fit rounded-full border-[#ddd6fe] bg-[#f3e8ff] text-[#5b21b6] hover:bg-[#f3e8ff]">
              {content.capabilityBadge}
            </Badge>
            <h2 className="text-3xl font-extrabold tracking-tight text-[#17131f] md:text-4xl">
              {content.capabilityTitle}
            </h2>
            <p className="mx-auto mt-5 max-w-lg text-[#62666d]">
              {content.capabilityDescription}
            </p>
          </div>
          <div className="grid gap-8 sm:grid-cols-2 lg:grid-cols-3">
            {content.capabilities.map(([title, desc], index) => {
              const Icon = CAPABILITY_ICONS[index];
              return (
              <div
                key={title}
                className="group rounded-xl border border-[#e5e7eb] bg-white p-8 transition hover:border-[#cbd0d6]"
              >
                <div className="mb-6 flex size-11 items-center justify-center rounded-lg bg-[#f3e8ff] text-[#5b21b6]">
                  <Icon className="size-5" />
                </div>
                <h3 className="text-lg font-bold text-[#17131f]">{title}</h3>
                <p className="mt-4 text-sm leading-6 text-[#62666d]">{desc}</p>
              </div>
              );
            })}
          </div>
        </div>
      </section>

      {/* ── 工作流 + 适用人群 ── */}
      <section className="bg-[#f8f7fc]" id="workflow">
        <div className="mx-auto max-w-[1280px] px-4 py-24 md:px-6 md:py-28">
          <div className="mb-16 text-center">
            <Badge variant="secondary" className="mx-auto mb-4 w-fit rounded-full border-[#ddd6fe] bg-[#f3e8ff] text-[#5b21b6] hover:bg-[#f3e8ff]">
              <WandSparkles className="mr-1 size-3.5" /> {content.workflowBadge}
            </Badge>
            <h2 className="text-3xl font-extrabold tracking-tight text-[#17131f] md:text-4xl">
              {content.workflowTitle}
            </h2>
          </div>
          <div className="grid gap-8 md:grid-cols-5">
            {content.workflow.map(([step, title, desc], idx) => (
              <div key={step} className="relative">
                <div className="rounded-xl border border-[#e5e7eb] bg-white p-7 transition hover:border-[#cbd0d6]">
                  <span className="inline-flex h-8 w-8 items-center justify-center rounded-full bg-[#7c3aed] text-xs font-bold text-[#17131f]">
                    {step}
                  </span>
                  <h3 className="mt-5 text-base font-bold text-[#17131f]">{title}</h3>
                  <p className="mt-4 text-sm leading-6 text-[#62666d]">{desc}</p>
                </div>
                {/* 步骤间箭头 (桌面端) */}
                {idx < content.workflow.length - 1 && (
                  <div className="absolute -right-3 top-1/2 hidden -translate-y-1/2 md:block">
                    <ChevronRight className="size-5 text-[#b8bdc4]" />
                  </div>
                )}
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* ── 特性清单 ── */}
      <section className="bg-white">
        <div className="mx-auto max-w-[1280px] px-4 py-24 md:px-6 md:py-28">
          <div className="overflow-hidden rounded-xl border border-[#e5e7eb] bg-white">
            <div className="p-14 text-center md:p-16">
              <Badge variant="secondary" className="mx-auto mb-4 w-fit rounded-full border-[#ddd6fe] bg-[#f3e8ff] text-[#5b21b6] hover:bg-[#f3e8ff]">
                {content.whyChoose}
              </Badge>
              <h2 className="text-3xl font-extrabold tracking-tight text-[#17131f] md:text-4xl">
                {content.detailsTitle}
              </h2>
            </div>
            <div className="grid gap-px bg-[#e5e7eb] md:grid-cols-2">
              {content.features.map((x) => (
                <div key={x} className="flex items-center gap-3 bg-white px-12 py-6 text-sm">
                  <Check className="size-4 shrink-0 text-[#8b5cf6]" />
                  <span className="text-[#17131f]">{x}</span>
                </div>
              ))}
            </div>
          </div>
        </div>
      </section>

      {/* ── FAQ ── */}
      <section className="bg-[#f8f7fc]" id="faq">
        <div className="mx-auto max-w-[1280px] px-4 py-24 md:px-6 md:py-28">
          <div className="mb-16 text-center">
            <h2 className="text-3xl font-extrabold tracking-tight text-[#17131f] md:text-4xl">
              {content.faqTitle}
            </h2>
          </div>
          <div className="mx-auto max-w-2xl divide-y divide-[#e5e7eb] overflow-hidden rounded-xl border border-[#e5e7eb] bg-white">
            {content.faqs.map(([question, answer]) => (
              <div key={question} className="p-10">
                <h3 className="text-base font-bold text-[#17131f]">{question}</h3>
                <p className="mt-4 text-sm leading-6 text-[#62666d]">{answer}</p>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* ── CTA ── */}
      <section className="bg-[#f8f7fc]">
        <div className="mx-auto max-w-[1280px] px-4 py-24 md:px-6 md:py-28">
          <div className="relative overflow-hidden rounded-xl border border-black bg-[#17131f] p-16 text-center md:p-24">
            <div className="relative z-10">
              <h2 className="text-3xl font-extrabold tracking-tight text-white md:text-4xl">
                {content.ctaTitle}
              </h2>
              <p className="mx-auto mt-5 max-w-md text-base leading-7 text-white/65">
                {content.ctaDescription}
              </p>
              <div className="mt-10 flex flex-wrap justify-center gap-3">
                <Button asChild size="lg" className="h-12 rounded-lg bg-[#7c3aed] px-6 text-base font-semibold text-[#17131f] hover:bg-[#6d28d9]">
                  <Link href="/projects?entry=dramaforge">
                    {content.createNow} <ArrowRight className="ml-1.5 size-4" />
                  </Link>
                </Button>
              </div>
            </div>
          </div>
        </div>
      </section>

      {/* ── Footer ── */}
      <footer className="border-t border-[#e5e7eb] bg-white">
        <div className="mx-auto max-w-[1280px] px-4 py-16 md:px-6">
          <div className="grid gap-8 sm:grid-cols-2">
            <div className="space-y-3 text-sm text-[#62666d]">
              <div>
                <div className="text-lg font-extrabold text-[#17131f]">{APP_NAME}</div>
                <div className="mt-0.5">{content.footerTagline}</div>
              </div>
              <div>
                <div className="font-medium text-[#17131f]">{content.officeAddress}</div>
                {content.addressLines.map((line) => <div key={line}>{line}</div>)}
              </div>
            </div>
            <div className="space-y-3 text-sm text-[#62666d] sm:text-right">
              <div className="text-lg font-extrabold text-[#17131f]">{content.contact}</div>
              <div>
                <div className="font-medium text-[#17131f]">{content.contact}</div>
                <div>{content.customerService}: <a href="mailto:hello@dreamreel.ai" className="transition hover:text-[#17131f]">hello@dreamreel.ai</a></div>
                <div>{content.landline}: {locale === "zh" ? "09:00–18:00（工作日）" : "Mon–Fri 09:00–18:00"}</div>
                <div>{content.email}: <a href="mailto:hello@dreamreel.ai" className="transition hover:text-[#17131f]">hello@dreamreel.ai</a></div>
              </div>
            </div>
          </div>
          <div className="mt-12 flex flex-col items-center gap-3 border-t border-[#e5e7eb] pt-10 text-xs text-[#62666d]">
            <div className="flex flex-wrap items-center justify-center gap-x-6 gap-y-2">
              <a href="https://beian.miit.gov.cn/#/Integrated/index" target="_blank" rel="noreferrer" className="transition hover:text-[#17131f]">
                {content.icp}
              </a>
              <a href="https://beian.mps.gov.cn/#/query/webSearch" target="_blank" rel="noreferrer" className="inline-flex items-center gap-1 transition hover:text-[#17131f]">
                {/* eslint-disable-next-line @next/next/no-img-element */}
                <img src="https://beian.mps.gov.cn/img/logo01.dd7ff50e.png" alt="" className="inline-block h-4 w-4" />
                {content.publicSecurity}
              </a>
            </div>
            <span>&copy; 2025–2026 {APP_NAME}</span>
          </div>
        </div>
      </footer>
    </div>
  );
}
