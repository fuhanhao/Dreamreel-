#!/usr/bin/env node

import { readFileSync, writeFileSync } from "node:fs";
import { spawnSync } from "node:child_process";

const [messageFile, source = ""] = process.argv.slice(2);

if (!messageFile || ["merge", "squash", "commit"].includes(source)) {
  process.exit(0);
}

const current = readFileSync(messageFile, "utf8");
const meaningfulLines = current
  .split(/\r?\n/u)
  .map((line) => line.trim())
  .filter((line) => line && !line.startsWith("#"));

// Preserve explicit messages supplied with `git commit -m`.
if (meaningfulLines.length > 0) {
  process.exit(0);
}

const result = spawnSync(
  "git",
  ["diff", "--cached", "--name-only", "--diff-filter=ACMR"],
  { cwd: process.cwd(), encoding: "utf8", shell: false },
);

if (result.status !== 0) {
  process.exit(0);
}

const files = result.stdout.split(/\r?\n/u).filter(Boolean);
if (files.length === 0) {
  process.exit(0);
}

const frontendFiles = files.filter((file) => file.startsWith("apps/web/"));
const summary =
  frontendFiles.length === files.length
    ? "Update frontend"
    : frontendFiles.length > 0
      ? "Update frontend and supporting files"
      : "Update project files";

writeFileSync(messageFile, `${summary}\n`, "utf8");
