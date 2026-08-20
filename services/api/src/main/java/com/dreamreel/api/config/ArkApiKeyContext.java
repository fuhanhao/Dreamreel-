package com.dreamreel.api.config;

/**
 * 请求/任务线程内的火山方舟（Seedance）API Key。
 * 优先来自前端 Header {@code X-Ark-Api-Key} 或异步任务 payload。
 */
public final class ArkApiKeyContext {

    public static final String HEADER = "X-Ark-Api-Key";

    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private ArkApiKeyContext() {
    }

    public static void set(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            CURRENT.remove();
        } else {
            CURRENT.set(apiKey.trim());
        }
    }

    public static String get() {
        return CURRENT.get();
    }

    public static void clear() {
        CURRENT.remove();
    }
}
