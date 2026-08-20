-- 单镜头视频生成任务类型
ALTER TABLE dramaforge_jobs DROP CONSTRAINT IF EXISTS dramaforge_jobs_job_type_check;

ALTER TABLE dramaforge_jobs ADD CONSTRAINT dramaforge_jobs_job_type_check CHECK (job_type IN (
    'EXTRACT_ASSETS',
    'GENERATE_SCRIPT',
    'ASSET_DESIGN',
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
