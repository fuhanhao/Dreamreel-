package com.dreamreel.api.dramaforge.domain;

/** 5 步流水线阶段 */
public enum DramaForgePipelineStage {
    /** ① 写故事：未导入原文 */
    STORY_INPUT,
    /** ② 定剧本：有原文，剧本未锁定或无镜头 */
    SCRIPT_LOCKED,
    /** ③ 建资产：剧本已锁定，资产未齐全或未确认 */
    ASSETS_LOCKED,
    /** ④ 出成片：资产已确认，视频未全部完成 */
    VIDEO_DONE,
    /** ⑤ 导出交付：已有成片 */
    COMPOSED
}
