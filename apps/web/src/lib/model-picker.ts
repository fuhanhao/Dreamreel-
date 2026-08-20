type GenerationMode = "video" | "image" | "prompt";

const PREFERRED_MODELS: Record<GenerationMode, string[]> = {
  prompt: [
    "gpt-4o-mini",
    "gpt-4o",
    "gpt-4.1-mini",
    "deepseek-chat",
    "qwen",
    "glm",
  ],
  image: [
    "nano-banana",
    "dall-e",
    "flux",
    "gpt-image",
  ],
  video: [
    "seedance",
    "kling",
    "wan",
    "sora",
  ],
};

function isExcludedModel(model: string, mode: GenerationMode): boolean {
  const lower = model.toLowerCase();
  if (mode === "prompt") {
    return lower.includes("codex") || lower.includes("embedding");
  }
  return false;
}

export function pickDefaultModel(models: string[], mode: GenerationMode): string {
  const available = models.filter((m) => !isExcludedModel(m, mode));
  if (available.length === 0) return models[0] ?? "";

  for (const preferred of PREFERRED_MODELS[mode]) {
    const match = available.find((m) => m.toLowerCase().includes(preferred));
    if (match) return match;
  }

  return available[0];
}
