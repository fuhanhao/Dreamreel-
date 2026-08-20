package com.dreamreel.api.config;

import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

@Component
public class ArkApiKeyResolver {

    private static final Pattern ARK_PREFIXED = Pattern.compile(
            "(?i)ark-([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})(-[0-9a-f]+)?");
    private static final Pattern PLAIN_UUID = Pattern.compile(
            "(?i)([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})");

    private final ArkProperties properties;

    public ArkApiKeyResolver(ArkProperties properties) {
        this.properties = properties;
    }

    /**
     * 优先级：显式传入 / 线程上下文 → 用户保存的 Key → 服务端环境变量。
     * 脏粘贴（api-key-时间戳+UUID+尾缀 / curl）会抽出 UUID，抽不出则回退下一级。
     */
    public String resolve(String explicitOrHeader, String userApiKey) {
        var explicit = sanitize(explicitOrHeader);
        if (explicit != null) {
            return explicit;
        }
        var fromContext = sanitize(ArkApiKeyContext.get());
        if (fromContext != null) {
            return fromContext;
        }
        var fromUser = sanitize(userApiKey);
        if (fromUser != null) {
            return fromUser;
        }
        return sanitize(properties.apiKey());
    }

    public boolean isConfigured(String explicitOrHeader, String userApiKey) {
        var key = resolve(explicitOrHeader, userApiKey);
        return key != null && !key.isBlank();
    }

    /** 清洗方舟 Key；无法识别返回 null。 */
    public static String sanitize(String raw) {
        if (raw == null) {
            return null;
        }
        var trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        var ark = ARK_PREFIXED.matcher(trimmed);
        if (ark.find()) {
            return "ark-" + ark.group(1) + (ark.group(2) != null ? ark.group(2) : "");
        }
        var uuid = PLAIN_UUID.matcher(trimmed);
        if (uuid.find()) {
            return uuid.group(1);
        }
        return null;
    }
}
