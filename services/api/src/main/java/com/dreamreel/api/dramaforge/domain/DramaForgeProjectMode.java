package com.dreamreel.api.dramaforge.domain;

/** TagoMovie 对标：整剧连载 vs 单集创作 */
public enum DramaForgeProjectMode {
    /** 长篇连载：AI 分集规划 + 左侧剧集目录树 */
    SERIES,
    /** 单集短剧：一集一创作，不展示多集管理 */
    SINGLE_EPISODE
}
