ALTER TABLE dramaforge_shots ADD COLUMN IF NOT EXISTS last_frame_url VARCHAR(2000);
ALTER TABLE dramaforge_shots ADD COLUMN IF NOT EXISTS storyboard_prompt VARCHAR(8000);

-- 旧数据：仅有 storyboard_url 时视作首帧
UPDATE dramaforge_shots
SET first_frame_url = storyboard_url
WHERE (first_frame_url IS NULL OR first_frame_url = '')
  AND storyboard_url IS NOT NULL
  AND storyboard_url != '';
