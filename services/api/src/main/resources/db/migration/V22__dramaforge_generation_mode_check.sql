-- Allow all DramaForge generation modes, including STORYBOARD_TO_VIDEO.
-- The legacy CHECK constraint only allowed IMAGE_TO_VIDEO / GRID_TO_VIDEO / REFERENCE_TO_VIDEO,
-- which made saving generationMode=storyboard_to_video fail with "数据保存失败".
ALTER TABLE dramaforge_configs
    DROP CONSTRAINT IF EXISTS dramaforge_configs_generation_mode_check;

ALTER TABLE dramaforge_configs
    ADD CONSTRAINT dramaforge_configs_generation_mode_check
        CHECK (generation_mode::text = ANY (ARRAY[
            'STORYBOARD_TO_VIDEO'::character varying,
            'IMAGE_TO_VIDEO'::character varying,
            'GRID_TO_VIDEO'::character varying,
            'REFERENCE_TO_VIDEO'::character varying
        ]::text[]));