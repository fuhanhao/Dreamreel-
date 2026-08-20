package com.dreamreel.api.dramaforge.service;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 短剧多机位预设：同场景仅改机位/景别，锁定人物与光影。 */
public final class DramaForgeCameraTemplates {

    private DramaForgeCameraTemplates() {
    }

    public record CameraPreset(String id, String label, String cameraNote) {
    }

    private static final Map<String, List<CameraPreset>> TEMPLATES = Map.of(
            "dialogue", List.of(
                    new CameraPreset("wide", "全景", "全景广角，交代完整场景与人物站位，固定光影与穿搭"),
                    new CameraPreset("medium_a", "中景A", "角色A 45° 斜侧中景，日常对话机位"),
                    new CameraPreset("ots", "过肩", "A 过肩拍 B，反打机位，保持空间轴线"),
                    new CameraPreset("closeup", "特写", "面部特写/眼部近景，放大微表情")),
            "action", List.of(
                    new CameraPreset("wide", "全景", "全景，交代动作空间与环境"),
                    new CameraPreset("medium", "中景", "中景跟拍主体动作，稳定构图"),
                    new CameraPreset("closeup", "特写", "手部或面部特写，强调动作细节")),
            "emotion", List.of(
                    new CameraPreset("medium", "中景", "纯侧 45° 中景，克制构图"),
                    new CameraPreset("closeup", "特写", "面部特写，侧光氛围"),
                    new CameraPreset("detail", "细节", "眼部或手部极近特写，情绪高潮")));

    public static List<CameraPreset> resolve(String templateId) {
        if (templateId == null || templateId.isBlank()) {
            return TEMPLATES.get("dialogue");
        }
        var key = templateId.trim().toLowerCase(Locale.ROOT);
        return TEMPLATES.getOrDefault(key, TEMPLATES.get("dialogue"));
    }

    public static String templateLabel(String templateId) {
        if (templateId == null || templateId.isBlank()) {
            return "对白";
        }
        return switch (templateId.trim().toLowerCase(Locale.ROOT)) {
            case "action" -> "动作";
            case "emotion" -> "情绪";
            default -> "对白";
        };
    }

    public record TemplateDetail(String id, String label, List<CameraPreset> presets) {
    }

    public static List<TemplateDetail> listAll() {
        return TEMPLATES.entrySet().stream()
                .map(e -> new TemplateDetail(e.getKey(), templateLabel(e.getKey()), List.copyOf(e.getValue())))
                .toList();
    }

    public static List<String> listTemplateIds() {
        return List.copyOf(TEMPLATES.keySet());
    }
}
