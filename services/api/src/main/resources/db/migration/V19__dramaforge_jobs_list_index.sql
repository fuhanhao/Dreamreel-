-- Speed up /jobs limited listing (project_id + created_at DESC).
CREATE INDEX IF NOT EXISTS idx_dramaforge_jobs_project_created
    ON dramaforge_jobs (project_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_dramaforge_jobs_project_status_created
    ON dramaforge_jobs (project_id, status, created_at DESC);
