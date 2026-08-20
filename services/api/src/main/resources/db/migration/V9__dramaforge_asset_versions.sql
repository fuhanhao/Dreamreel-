-- 资产设计图版本历史 + 单资产重新生成任务类型
CREATE TABLE IF NOT EXISTS dramaforge_asset_versions (
    id UUID PRIMARY KEY,
    asset_id UUID NOT NULL,
    version_no INT NOT NULL,
    reference_image_url VARCHAR(2000),
    design_prompt VARCHAR(4000),
    active BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_dramaforge_asset_versions_asset ON dramaforge_asset_versions(asset_id);

ALTER TABLE dramaforge_jobs DROP CONSTRAINT IF EXISTS dramaforge_jobs_job_type_check;

ALTER TABLE dramaforge_jobs ADD CONSTRAINT dramaforge_jobs_job_type_check CHECK (job_type IN (
    'EXTRACT_ASSETS',
    'GENERATE_SCRIPT',
    'ASSET_DESIGN',
    'ASSET_DESIGN_SINGLE',
    'STORYBOARD',
    'SHOT_STORYBOARD',
    'SHOT_VIDEO',
    'GRID_STORYBOARD',
    'VIDEO',
    'SYNC_VIDEOS',
    'COMPOSE',
    'EXPORT_PROJECT',
    'EXPORT_JIANYING',
    'WORKFLOW_RUN'
));
