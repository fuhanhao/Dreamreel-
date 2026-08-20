package com.dreamreel.api.dramaforge.service;

import java.util.regex.Pattern;

/** 清洗小说原文并截取适合送入大模型的片段，降低内容安全误拦概率。 */
final class DramaForgeSourcePreparer {

    private static final Pattern URL_PATTERN = Pattern.compile("https?://\\S+");
    private static final Pattern WWW_PATTERN = Pattern.compile("www\\.\\S+");
    private static final Pattern SITE_PATTERN = Pattern.compile("(?i)txt\\d+\\.com");
    private static final Pattern BLANK_LINES = Pattern.compile("\\n{3,}");

    private DramaForgeSourcePreparer() {
    }

    static String prepare(String sourceText, int maxChars) {
        return prepare(sourceText, maxChars, 0);
    }

    static String prepare(String sourceText, int maxChars, int skipChars) {
        var sanitized = sanitize(sourceText);
        if (sanitized.isBlank()) {
            sanitized = sourceText.trim();
        }
        var start = Math.min(Math.max(skipChars, 0), Math.max(sanitized.length() - 1, 0));
        if (sanitized.length() <= start) {
            start = 0;
        }
        var body = sanitized.substring(start);
        if (body.length() <= maxChars) {
            return body;
        }
        return body.substring(0, maxChars)
                + "\n\n…（已截取 " + maxChars + " 字供 AI 分析，原文共 " + sourceText.length() + " 字）";
    }

    static String sanitize(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        var result = text;
        // 去掉常见盗版站声明头
        var marker = "用户上传之内容开始";
        var idx = result.indexOf(marker);
        if (idx >= 0) {
            result = result.substring(idx + marker.length());
        } else {
            var declareIdx = result.indexOf("声明：");
            if (declareIdx >= 0 && declareIdx < 800) {
                var afterDeclare = result.indexOf('\n', declareIdx);
                if (afterDeclare > 0 && afterDeclare < 1500) {
                    result = result.substring(afterDeclare);
                }
            }
        }
        // 优先从书名/简介后开始
        for (var anchor : new String[]{"内容简介", "正文", "第一章", "第1章", "楔子", "序章"}) {
            var pos = result.indexOf(anchor);
            if (pos > 0 && pos < 3000) {
                result = result.substring(pos);
                break;
            }
        }
        result = URL_PATTERN.matcher(result).replaceAll("");
        result = WWW_PATTERN.matcher(result).replaceAll("");
        result = SITE_PATTERN.matcher(result).replaceAll("");
        result = BLANK_LINES.matcher(result).replaceAll("\n\n");
        return result.trim();
    }

    static boolean isModerationError(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        var lower = message.toLowerCase();
        return lower.contains("inappropriate content")
                || lower.contains("content filter")
                || lower.contains("content policy")
                || message.contains("内容安全")
                || message.contains("违规")
                || message.contains("敏感");
    }

    static String moderationHint(String action) {
        return action + "：内容被模型安全策略拦截。请去掉文首声明/广告，仅保留 1～3 章正文后重试，或在「资产库」手动添加角色/场景。";
    }
}
