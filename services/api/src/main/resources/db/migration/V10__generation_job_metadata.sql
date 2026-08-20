ALTER TABLE generation_jobs ADD COLUMN IF NOT EXISTS generation_mode VARCHAR(32);
ALTER TABLE generation_jobs ADD COLUMN IF NOT EXISTS reference_image_url VARCHAR(2000);
ALTER TABLE generation_jobs ADD COLUMN IF NOT EXISTS reference_video_url VARCHAR(2000);
ALTER TABLE generation_jobs ADD COLUMN IF NOT EXISTS ratio VARCHAR(16);
ALTER TABLE generation_jobs ADD COLUMN IF NOT EXISTS strength DOUBLE PRECISION;
