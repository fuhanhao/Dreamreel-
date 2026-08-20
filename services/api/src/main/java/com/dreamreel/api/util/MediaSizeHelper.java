package com.dreamreel.api.util;

import java.util.Locale;

public final class MediaSizeHelper {

    private MediaSizeHelper() {
    }

  /** 归一化；视频默认 480p */
  public static String normalizeResolution(String quality) {
      if (quality == null || quality.isBlank()) {
          return "480p";
      }
      var q = quality.toLowerCase(Locale.ROOT);
      if (q.contains("1080") || q.contains("4k")) {
          return "1080p";
      }
      if (q.contains("720")) {
          return "720p";
      }
      return "480p";
  }

    public static String toVideoSize(String ratio, String quality) {
        var tier = normalizeResolution(quality);
        if (ratio == null || ratio.isBlank()) {
            return switch (tier) {
                case "1080p" -> "1920x1080";
                case "480p" -> "854x480";
                default -> "1280x720";
            };
        }
        return switch (ratio) {
            case "9:16" -> switch (tier) {
                case "1080p" -> "1080x1920";
                case "480p" -> "480x854";
                default -> "720x1280";
            };
            case "1:1" -> switch (tier) {
                case "1080p" -> "1080x1080";
                case "480p" -> "480x480";
                default -> "720x720";
            };
            case "4:3" -> switch (tier) {
                case "1080p" -> "1440x1080";
                case "480p" -> "640x480";
                default -> "960x720";
            };
            case "3:4" -> switch (tier) {
                case "1080p" -> "1080x1440";
                case "480p" -> "480x640";
                default -> "720x960";
            };
            default -> switch (tier) { // 16:9
                case "1080p" -> "1920x1080";
                case "480p" -> "854x480";
                default -> "1280x720";
            };
        };
    }

    public static String toImageSize(String ratio, String quality) {
        var tier = normalizeResolution(quality);
        if (ratio == null || ratio.isBlank()) {
            return switch (tier) {
                case "1080p" -> "1080x1920";
                case "480p" -> "576x1024";
                default -> "1024x1792";
            };
        }
        return switch (ratio) {
            case "16:9" -> switch (tier) {
                case "1080p" -> "1920x1080";
                case "480p" -> "854x480";
                default -> "1792x1024";
            };
            case "9:16" -> switch (tier) {
                case "1080p" -> "1080x1920";
                case "480p" -> "576x1024";
                default -> "1024x1792";
            };
            case "1:1" -> switch (tier) {
                case "1080p" -> "1080x1080";
                case "480p" -> "512x512";
                default -> "1024x1024";
            };
            case "4:3" -> switch (tier) {
                case "1080p" -> "1440x1080";
                case "480p" -> "640x480";
                default -> "1024x768";
            };
            case "3:4" -> switch (tier) {
                case "1080p" -> "1080x1440";
                case "480p" -> "480x640";
                default -> "768x1024";
            };
            default -> toImageSize("9:16", quality);
        };
    }

    /**
     * TokenFree / Runbase 系 Seedream 4.5 的 quality 枚举是 basic(2K) / high(4K)，
     * 不是 2K/4K（后者用于 size）。
     */
    public static String toSeedreamQuality(String quality) {
        if (isHighSeedreamTier(quality)) {
            return "high";
        }
        return "basic";
    }

    /** Seedream size 字段使用 2K/4K 分辨率档位。 */
    public static String toSeedreamSize(String quality) {
        if (isHighSeedreamTier(quality)) {
            return "4K";
        }
        return "2K";
    }

    private static boolean isHighSeedreamTier(String quality) {
        if (quality == null || quality.isBlank()) {
            return false;
        }
        var q = quality.toLowerCase(Locale.ROOT);
        return q.contains("1080") || q.contains("4k") || q.equals("high");
    }

    /** Seedream 支持的宽高比；未知值回退为 9:16。 */
    public static String normalizeSeedreamAspectRatio(String ratio) {
        if (ratio == null || ratio.isBlank()) {
            return "9:16";
        }
        return switch (ratio) {
            case "1:1", "2:3", "3:2", "3:4", "4:3", "9:16", "16:9", "21:9" -> ratio;
            default -> "9:16";
        };
    }
}
