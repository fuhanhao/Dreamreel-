-- DramaForge module tables (also auto-created by JPA ddl-auto=update)

CREATE TABLE IF NOT EXISTS dramaforge_configs (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL UNIQUE,
    content_mode VARCHAR(16) NOT NULL DEFAULT 'DRAMA',
    generation_mode VARCHAR(24) NOT NULL DEFAULT 'IMAGE_TO_VIDEO',
    image_backend VARCHAR(128),
    video_backend VARCHAR(128),
    text_backend VARCHAR(128),
    style_prompt VARCHAR(2000),
    source_text TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS dramaforge_assets (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL,
    type VARCHAR(16) NOT NULL,
    name VARCHAR(128) NOT NULL,
    description VARCHAR(2000),
    design_prompt VARCHAR(4000),
    reference_image_url VARCHAR(2000),
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS dramaforge_episodes (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL,
    episode_number INT NOT NULL,
    title VARCHAR(200) NOT NULL,
    script_json TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS dramaforge_shots (
    id UUID PRIMARY KEY,
    episode_id UUID NOT NULL,
    shot_number INT NOT NULL,
    description VARCHAR(4000) NOT NULL,
    dialogue VARCHAR(4000),
    camera_note VARCHAR(2000),
    character_refs VARCHAR(2000) DEFAULT '[]',
    storyboard_url VARCHAR(2000),
    video_job_id UUID,
    status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_dramaforge_assets_project ON dramaforge_assets(project_id);
CREATE INDEX IF NOT EXISTS idx_dramaforge_episodes_project ON dramaforge_episodes(project_id);
CREATE INDEX IF NOT EXISTS idx_dramaforge_shots_episode ON dramaforge_shots(episode_id);
