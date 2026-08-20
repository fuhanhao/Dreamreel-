package com.dreamreel.api.dramaforge.domain;

public enum DramaForgeGenerationMode {
    /** 分镜图 → 视频（TagoMovie 主路径） */
    STORYBOARD_TO_VIDEO,
    IMAGE_TO_VIDEO,
    GRID_TO_VIDEO,
    /** 设计图直出视频（高级模式） */
    REFERENCE_TO_VIDEO
}
