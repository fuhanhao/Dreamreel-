-- Global queue scan used by the concurrent worker claim query.
CREATE INDEX IF NOT EXISTS idx_dramaforge_jobs_claim
    ON dramaforge_jobs (status, lease_until, created_at);

-- Final database guard for the one-running-job-per-project invariant.
CREATE UNIQUE INDEX IF NOT EXISTS uq_dramaforge_jobs_running_project
    ON dramaforge_jobs (project_id)
    WHERE status = 'RUNNING';
