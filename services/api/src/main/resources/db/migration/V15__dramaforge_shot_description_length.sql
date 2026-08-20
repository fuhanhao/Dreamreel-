-- 镜头提示词需容纳角色/场景线索与风格段落
ALTER TABLE dramaforge_shots ALTER COLUMN description TYPE VARCHAR(16000);
