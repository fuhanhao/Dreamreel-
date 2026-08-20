-- 全局画面比例配置（分镜图 / 视频统一）
ALTER TABLE dramaforge_configs ADD COLUMN IF NOT EXISTS aspect_ratio VARCHAR(8);
