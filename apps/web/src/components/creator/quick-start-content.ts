import { DOCS_SOP_URL } from "@/lib/brand";

export type QuickStartGuideId = "tutorial" | "guide" | "promptTips";

export interface QuickStartGuide {
  id: QuickStartGuideId;
  titleZh: string;
  titleEn: string;
  descZh: string;
  descEn: string;
  stepsZh: { title: string; body: string }[];
  stepsEn: { title: string; body: string }[];
  ctaZh?: { label: string; href: string };
  ctaEn?: { label: string; href: string };
}

export const QUICK_START_GUIDES: QuickStartGuide[] = [
  {
    id: "tutorial",
    titleZh: "新手教程",
    titleEn: "Getting started",
    descZh: "3分钟快速了解平台",
    descEn: "Learn the platform in 3 minutes",
    stepsZh: [
      {
        title: "配置 API Key",
        body: "打开创作中心右上角「API Key 设置」，保存 TokenFree（文案/图片）与火山方舟 Seedance（视频）密钥到账号。配置后顶栏会显示「API 已连接」。",
      },
      {
        title: "一次快速生成",
        body: "在创作中心输入剧情描述，选择文生视频 / 图生视频 / 文生图，点「开始生成」。任务会出现在「我的创作」列表，可预览与下载。",
      },
      {
        title: "创建短剧项目",
        body: "点「新建项目」或进入「项目管理」，创建短片/剧情项目后，可进入 DramaForge 流水线做完整分集制作。",
      },
      {
        title: "走通流水线",
        body: "DramaForge 推荐顺序：原文/剧本 → 提取资产 → 生成设计图 → 解析镜头 → 生成视频 → 合成导出。进度在任务面板实时更新。",
      },
    ],
    stepsEn: [
      {
        title: "Configure API keys",
        body: "Open API Key settings and save TokenFree (text/image) and Volcengine Ark Seedance (video) keys to your account. The header shows API connected when ready.",
      },
      {
        title: "Try a quick generation",
        body: "In Creator, enter a prompt, pick text-to-video / image-to-video / text-to-image, then generate. Jobs appear under My creations for preview and download.",
      },
      {
        title: "Create a drama project",
        body: "Create a short-film project from New project or Projects, then open DramaForge for episode-based production.",
      },
      {
        title: "Run the pipeline",
        body: "Recommended flow: source/script → extract assets → design images → parse shots → generate videos → compose & export. Progress updates live in the job panel.",
      },
    ],
    ctaZh: { label: "去配置 API Key", href: "/?openApiKey=1" },
    ctaEn: { label: "Configure API keys", href: "/?openApiKey=1" },
  },
  {
    id: "guide",
    titleZh: "创作指南",
    titleEn: "Creation guide",
    descZh: "从 0 到 1 制作短片",
    descEn: "From zero to your first short",
    stepsZh: [
      {
        title: "准备故事与风格",
        body: "在项目配置里写清世界观、风格提示词与画幅（如 9:16）。风格越具体，角色与场景越统一。",
      },
      {
        title: "建立资产库",
        body: "提取或手动添加角色 / 场景 / 道具，为每个资产生成设计图。有对白的角色请生成或上传音色样本，视频生成才能挂上 @Audio 参考。",
      },
      {
        title: "剧本 → 镜头",
        body: "生成或粘贴剧本 JSON，再「解析镜头」。每个镜头会绑定场景与角色；可微调描述、时长与参考视频模式。",
      },
      {
        title: "按序生成视频",
        body: "批量生成视频会按镜头顺序提交，并等待成片后抽取尾帧衔接下一镜，保证动作连续性。单镜失败可单独重试。",
      },
      {
        title: "合成与导出",
        body: "全部镜头完成后合成成片，可混入对白音频与 BGM，再导出工程或剪映草稿继续精剪。",
      },
    ],
    stepsEn: [
      {
        title: "Set story & style",
        body: "In project config, define worldview, style prompt, and aspect ratio (e.g. 9:16). Clear style keeps characters and scenes consistent.",
      },
      {
        title: "Build the asset library",
        body: "Extract or add characters / scenes / props and generate design images. Characters with dialogue need a voice sample so video jobs can attach @Audio references.",
      },
      {
        title: "Script → shots",
        body: "Generate or paste script JSON, then parse shots. Each shot binds scene/characters; tweak description, duration, and continuity mode as needed.",
      },
      {
        title: "Generate videos in order",
        body: "Batch video runs shot-by-shot, waits for completion, then uses the previous tail frame for continuity. Retry failed shots individually.",
      },
      {
        title: "Compose & export",
        body: "When shots are ready, compose the episode (optional dialogue mix / BGM), then export the project or Jianying draft for finishing.",
      },
    ],
    ctaZh: { label: "打开 AI 漫剧 SOP", href: DOCS_SOP_URL },
    ctaEn: { label: "Open AI drama SOP", href: DOCS_SOP_URL },
  },
  {
    id: "promptTips",
    titleZh: "提示词秘籍",
    titleEn: "Prompt tips",
    descZh: "提升生成质量的技巧",
    descEn: "Improve generation quality",
    stepsZh: [
      {
        title: "结构：主体 + 动作 + 镜头 + 氛围",
        body: "例如：「中景，女主推开铁门走入破旧厂房，冷色荧光灯，尘埃漂浮，电影感，半写实」。避免只写情绪词而不写画面。",
      },
      {
        title: "用资产名保持一致性",
        body: "镜头描述里点名资源库角色/场景名称，规划器才能正确绑定参考图。不要同一角色换多个绰号。",
      },
      {
        title: "写清运镜与时长线索",
        body: "推、拉、摇、跟、切；特写/中景/全景。对白镜头可略长；动作冲击可短促。平台单镜约 2–15 秒。",
      },
      {
        title: "减少冲突与真人脸描述",
        body: "风格、服装、年龄前后一致；避免「某某明星脸」。若触发隐私拦截，重新生成虚构半写实定妆图后再试。",
      },
      {
        title: "善用 AI 优化",
        body: "风格提示词与资产设计提示词可用「AI 优化」扩写；扩写后仍建议人工删掉与剧情无关的堆砌词。",
      },
    ],
    stepsEn: [
      {
        title: "Structure: subject + action + camera + mood",
        body: "Example: “Medium shot, heroine pushes open a steel door into a rundown factory, cool fluorescent light, floating dust, cinematic semi-realistic.” Avoid mood-only prompts.",
      },
      {
        title: "Use asset names for consistency",
        body: "Mention library character/scene names in shot text so the planner can bind reference images. Don’t rename the same character casually.",
      },
      {
        title: "Specify camera & pacing",
        body: "Push/pull/pan/track/cut; close-up / medium / wide. Dialogue shots can be longer; action beats shorter. Platform shots are roughly 2–15s.",
      },
      {
        title: "Avoid conflicts & real faces",
        body: "Keep style, costume, and age consistent. Avoid celebrity likeness. If privacy filters trigger, regenerate fictional semi-realistic designs and retry.",
      },
      {
        title: "Use AI optimize carefully",
        body: "Style and design prompts can be expanded with AI optimize—then trim fluff that doesn’t serve the story.",
      },
    ],
    ctaZh: { label: "去创作中心试试", href: "/" },
    ctaEn: { label: "Try in Creator", href: "/" },
  },
];

export function getQuickStartGuide(id: QuickStartGuideId): QuickStartGuide {
  return QUICK_START_GUIDES.find((g) => g.id === id) ?? QUICK_START_GUIDES[0];
}
