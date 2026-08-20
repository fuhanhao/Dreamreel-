ALTER TABLE dramaforge_configs ADD COLUMN IF NOT EXISTS image_quality VARCHAR(16) DEFAULT '720p';
ALTER TABLE dramaforge_configs ADD COLUMN IF NOT EXISTS video_quality VARCHAR(16) DEFAULT '720p';

ALTER TABLE dramaforge_assets ADD COLUMN IF NOT EXISTS voice_label VARCHAR(500);
ALTER TABLE dramaforge_assets ADD COLUMN IF NOT EXISTS voice_sample_url VARCHAR(2000);
