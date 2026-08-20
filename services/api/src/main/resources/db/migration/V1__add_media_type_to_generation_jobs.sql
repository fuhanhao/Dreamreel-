-- Fix missing media_type column on generation_jobs
ALTER TABLE generation_jobs ADD COLUMN IF NOT EXISTS media_type VARCHAR(16);
UPDATE generation_jobs SET media_type = 'VIDEO' WHERE media_type IS NULL;
ALTER TABLE generation_jobs ALTER COLUMN media_type SET DEFAULT 'VIDEO';
ALTER TABLE generation_jobs ALTER COLUMN media_type SET NOT NULL;
