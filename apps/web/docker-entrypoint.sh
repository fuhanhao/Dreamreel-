#!/bin/sh
set -e

# 容器启动时根据环境变量生成前端运行时配置（无需重建镜像）
node <<'NODE'
const fs = require("fs");
const path = "/app/apps/web/public/runtime-env.js";
const config = {
  API_BASE_URL: process.env.API_BASE_URL ?? "",
};
fs.mkdirSync(require("path").dirname(path), { recursive: true });
fs.writeFileSync(
  path,
  `window.__RUNTIME_CONFIG__=${JSON.stringify(config)};`,
);
NODE

exec "$@"
