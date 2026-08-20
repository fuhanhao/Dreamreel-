ALTER TABLE dramaforge_configs
    ADD COLUMN IF NOT EXISTS project_summary TEXT,
    ADD COLUMN IF NOT EXISTS worldview TEXT;
