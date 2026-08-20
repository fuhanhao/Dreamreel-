-- TagoMovie-style 6-step wizard workflow locks
ALTER TABLE dramaforge_configs
    ADD COLUMN IF NOT EXISTS assets_locked_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS storyboard_locked_at TIMESTAMPTZ;

ALTER TABLE dramaforge_episodes
    ADD COLUMN IF NOT EXISTS script_locked_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS storyboard_locked_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS timeline_json TEXT;

-- Default generation mode: storyboard i2v (TagoMovie path)
UPDATE dramaforge_configs
SET generation_mode = 'STORYBOARD_TO_VIDEO'
WHERE generation_mode = 'REFERENCE_TO_VIDEO';
