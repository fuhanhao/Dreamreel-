#!/usr/bin/env node

import { spawnSync } from "node:child_process";
import path from "node:path";

function git(args) {
  const result = spawnSync("git", args, {
    cwd: process.cwd(),
    encoding: "utf8",
    shell: false,
  });

  if (result.error) {
    console.error(`[AI review] 无法执行 git：${result.error.message}`);
    process.exit(1);
  }

  return result;
}

function stagedFiles() {
  const result = git(["diff", "--cached", "--name-only", "--diff-filter=ACMR"]);
  if (result.status !== 0) {
    process.stderr.write(result.stderr);
    process.exit(result.status ?? 1);
  }
  return result.stdout.split(/\r?\n/u).filter(Boolean);
}

const files = stagedFiles();

if (files.length === 0) {
  console.log("[AI review] 暂存区为空，无需审查。");
  process.exit(0);
}

const findings = [];
const sensitiveFilePatterns = [
  /(^|\/)\.env(?:\.|$)/iu,
  /(^|\/)(?:credentials?|secrets?)\.(?:json|ya?ml|toml)$/iu,
  /\.(?:p12|pfx|pem|key)$/iu,
];

for (const file of files) {
  const normalized = file.replaceAll("\\", "/");
  if (sensitiveFilePatterns.some((pattern) => pattern.test(normalized))) {
    findings.push(`${file}: 不应提交凭据或密钥文件`);
  }
}

const whitespace = git(["diff", "--cached", "--check"]);
if (whitespace.status !== 0) {
  const message = (whitespace.stdout || whitespace.stderr).trim();
  findings.push(`补丁格式检查失败${message ? `：\n${message}` : ""}`);
}

const diffResult = git([
  "diff",
  "--cached",
  "--no-color",
  "--unified=0",
  "--diff-filter=ACMR",
]);

if (diffResult.status !== 0) {
  process.stderr.write(diffResult.stderr);
  process.exit(diffResult.status ?? 1);
}

let currentFile = "";
for (const rawLine of diffResult.stdout.split(/\r?\n/u)) {
  if (rawLine.startsWith("+++ b/")) {
    currentFile = rawLine.slice(6);
    continue;
  }
  if (!rawLine.startsWith("+") || rawLine.startsWith("+++")) continue;

  const line = rawLine.slice(1);
  if (/^(?:<{7}|={7}|>{7})(?:\s|$)/u.test(line)) {
    findings.push(`${currentFile || "暂存补丁"}: 存在未解决的合并冲突标记`);
  }
  if (/-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----/u.test(line)) {
    findings.push(`${currentFile || "暂存补丁"}: 检测到私钥内容`);
  }
  if (/\bAKIA[0-9A-Z]{16}\b/u.test(line)) {
    findings.push(`${currentFile || "暂存补丁"}: 检测到疑似 AWS Access Key`);
  }

  const looksLikePlaceholder =
    /\b(?:example|placeholder|your[_-]?|dummy|test|process\.env|import\.meta\.env)\b/iu.test(line);
  const secretAssignment =
    /\b(?:api[_-]?key|secret|access[_-]?token|private[_-]?key|password)\b\s*[:=]\s*["'`][A-Za-z0-9_./+=-]{20,}["'`]/iu;
  if (!looksLikePlaceholder && secretAssignment.test(line)) {
    findings.push(`${currentFile || "暂存补丁"}: 检测到疑似硬编码密钥`);
  }
}

if (findings.length > 0) {
  console.error("[AI review] 暂存区审查未通过：");
  for (const finding of [...new Set(findings)]) {
    console.error(`- ${finding}`);
  }
  process.exit(1);
}

const extensions = new Map();
for (const file of files) {
  const extension = path.extname(file).toLowerCase() || "(无扩展名)";
  extensions.set(extension, (extensions.get(extension) ?? 0) + 1);
}
const summary = [...extensions.entries()]
  .map(([extension, count]) => `${extension} ${count}`)
  .join("，");

console.log(`[AI review] 审查通过：${files.length} 个文件（${summary}）。`);
