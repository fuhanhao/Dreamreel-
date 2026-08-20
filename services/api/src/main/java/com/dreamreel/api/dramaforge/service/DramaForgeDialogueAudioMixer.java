package com.dreamreel.api.dramaforge.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 镜头音频工具：Seedance 原声增强，以及可选的 TTS 对齐混音（合成等场景仍可用）。
 */
final class DramaForgeDialogueAudioMixer {

    private static final Logger log = LoggerFactory.getLogger(DramaForgeDialogueAudioMixer.class);
    private static final Pattern SILENCE_START = Pattern.compile("silence_start:\\s*([0-9.]+)");
    private static final Pattern SILENCE_END = Pattern.compile("silence_end:\\s*([0-9.]+)");
    private static final Pattern DURATION = Pattern.compile("Duration:\\s*(\\d+):(\\d+):(\\d+(?:\\.\\d+)?)");
    private static final int PCM_RATE = 8000;
    /** 互相关峰值至少高于次峰这么多倍才采信 */
    private static final double XCORR_MIN_PEAK_RATIO = 1.35;
    private static final double XCORR_MIN_SCORE = 0.08;

    private DramaForgeDialogueAudioMixer() {
    }

    record SpeechWindow(long startMs, long endMs) {
        long durationMs() {
            return Math.max(0L, endMs - startMs);
        }
    }

    record AlignResult(long delayMs, double tempo, String method) {
    }

    static String buildAlignedMixFilter(
            double ambientVolume,
            double dialogueVolume,
            long delayMs,
            double atempo) {
        var tempo = clampTempo(atempo);
        var delay = Math.max(0L, delayMs);
        var dlgChain = new StringBuilder("[1:a]");
        if (Math.abs(tempo - 1.0) > 0.02) {
            for (var step : tempoSteps(tempo)) {
                dlgChain.append("atempo=").append(String.format(Locale.ROOT, "%.4f", step)).append(",");
            }
        }
        if (delay > 0) {
            dlgChain.append("adelay=").append(delay).append("|").append(delay).append(",");
        }
        dlgChain.append("volume=").append(dialogueVolume).append("[dlg]");
        return "[0:a]volume=" + ambientVolume + ",highpass=f=180[amb];"
                + dlgChain + ";"
                + "[amb][dlg]amix=inputs=2:duration=first:dropout_transition=0:normalize=0[a]";
    }

    static SpeechWindow detectSpeechWindow(Path video) {
        try {
            var command = List.of(
                    "ffmpeg",
                    "-hide_banner",
                    "-i", video.toString(),
                    "-af", "silencedetect=noise=-28dB:d=0.18",
                    "-f", "null",
                    "-"
            );
            var process = new ProcessBuilder(command).redirectErrorStream(true).start();
            var output = new StringBuilder();
            try (var reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append('\n');
                }
            }
            process.waitFor();
            var parsed = parseSpeechWindow(output.toString());
            if (parsed != null) {
                return parsed;
            }
        } catch (Exception ex) {
            log.warn("Speech window detect failed: {}", ex.getMessage());
        }
        return new SpeechWindow(0L, 0L);
    }

    static long probeDurationMs(Path media) {
        try {
            var command = List.of(
                    "ffprobe",
                    "-v", "error",
                    "-show_entries", "format=duration",
                    "-of", "default=noprint_wrappers=1:nokey=1",
                    media.toString()
            );
            var process = new ProcessBuilder(command).redirectErrorStream(true).start();
            var line = "";
            try (var reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                line = reader.readLine();
            }
            process.waitFor();
            if (line != null && !line.isBlank()) {
                var sec = Double.parseDouble(line.trim());
                if (sec > 0 && Double.isFinite(sec)) {
                    return Math.round(sec * 1000.0);
                }
            }
        } catch (Exception ignored) {
        }
        return 0L;
    }

    static SpeechWindow parseSpeechWindow(String ffmpegLog) {
        if (ffmpegLog == null || ffmpegLog.isBlank()) {
            return null;
        }
        var silenceStarts = new ArrayList<Double>();
        var silenceEnds = new ArrayList<Double>();
        for (var line : ffmpegLog.split("\n")) {
            var startMatcher = SILENCE_START.matcher(line);
            if (startMatcher.find()) {
                silenceStarts.add(Double.parseDouble(startMatcher.group(1)));
            }
            var endMatcher = SILENCE_END.matcher(line);
            if (endMatcher.find()) {
                silenceEnds.add(Double.parseDouble(endMatcher.group(1)));
            }
        }
        var mediaDurationSec = parseDurationSeconds(ffmpegLog);

        double speechStartSec = 0;
        if (!silenceStarts.isEmpty() && silenceStarts.get(0) <= 0.05 && !silenceEnds.isEmpty()) {
            speechStartSec = silenceEnds.get(0);
        }

        double speechEndSec = mediaDurationSec > 0 ? mediaDurationSec : speechStartSec;
        if (!silenceStarts.isEmpty()) {
            var lastStart = silenceStarts.get(silenceStarts.size() - 1);
            if (mediaDurationSec <= 0 || lastStart < mediaDurationSec - 0.05) {
                if (mediaDurationSec > 0 && lastStart >= mediaDurationSec - 1.5) {
                    speechEndSec = lastStart;
                } else if (mediaDurationSec <= 0) {
                    speechEndSec = lastStart;
                }
            }
        }
        if (speechEndSec <= speechStartSec + 0.12) {
            return new SpeechWindow(Math.round(speechStartSec * 1000.0), Math.round(speechStartSec * 1000.0));
        }
        return new SpeechWindow(Math.round(speechStartSec * 1000.0), Math.round(speechEndSec * 1000.0));
    }

    static double tempoForDurations(long speechDurationMs, long ttsDurationMs) {
        if (speechDurationMs < 200 || ttsDurationMs < 200) {
            return 1.0;
        }
        var ratio = (double) ttsDurationMs / (double) speechDurationMs;
        return clampTempo(ratio);
    }

    static double clampTempo(double tempo) {
        if (!Double.isFinite(tempo) || tempo <= 0) {
            return 1.0;
        }
        return Math.max(0.5, Math.min(2.0, tempo));
    }

    static List<Double> tempoSteps(double tempo) {
        var steps = new ArrayList<Double>();
        var remaining = tempo;
        while (remaining > 2.0 + 1e-6) {
            steps.add(2.0);
            remaining /= 2.0;
        }
        while (remaining < 0.5 - 1e-6) {
            steps.add(0.5);
            remaining /= 0.5;
        }
        steps.add(remaining);
        return steps;
    }

    /**
     * 在 longSignal 上滑动 shortSignal，返回最佳延迟（毫秒）与归一化峰值分。
     * 仅搜索 delay≥0（TTS 不早于原片）。
     */
    static long[] bestDelayByCrossCorrelation(short[] longSignal, short[] shortSignal, int sampleRate) {
        if (longSignal == null || shortSignal == null
                || longSignal.length < 16 || shortSignal.length < 16
                || shortSignal.length > longSignal.length) {
            return new long[] {0L, 0L};
        }
        var nShort = shortSignal.length;
        var maxLag = longSignal.length - nShort;
        if (maxLag < 0) {
            return new long[] {0L, 0L};
        }

        double shortEnergy = 0;
        for (short s : shortSignal) {
            shortEnergy += (double) s * s;
        }
        if (shortEnergy < 1e-6) {
            return new long[] {0L, 0L};
        }
        var shortNorm = Math.sqrt(shortEnergy);

        double best = Double.NEGATIVE_INFINITY;
        double second = Double.NEGATIVE_INFINITY;
        int bestLag = 0;
        // 步进 4 样本（0.5ms@8k）加速；精细再扫邻域
        for (int lag = 0; lag <= maxLag; lag += 4) {
            double dot = 0;
            double longEnergy = 0;
            for (int i = 0; i < nShort; i++) {
                double lv = longSignal[lag + i];
                double sv = shortSignal[i];
                dot += lv * sv;
                longEnergy += lv * lv;
            }
            if (longEnergy < 1e-6) {
                continue;
            }
            var score = dot / (Math.sqrt(longEnergy) * shortNorm);
            if (score > best) {
                second = best;
                best = score;
                bestLag = lag;
            } else if (score > second) {
                second = score;
            }
        }
        // 局部细化
        int from = Math.max(0, bestLag - 8);
        int to = Math.min(maxLag, bestLag + 8);
        for (int lag = from; lag <= to; lag++) {
            double dot = 0;
            double longEnergy = 0;
            for (int i = 0; i < nShort; i++) {
                double lv = longSignal[lag + i];
                double sv = shortSignal[i];
                dot += lv * sv;
                longEnergy += lv * lv;
            }
            if (longEnergy < 1e-6) {
                continue;
            }
            var score = dot / (Math.sqrt(longEnergy) * shortNorm);
            if (score > best) {
                second = best;
                best = score;
                bestLag = lag;
            }
        }
        var delayMs = Math.round(bestLag * 1000.0 / sampleRate);
        var peakRatioScaled = second > 1e-9 ? Math.round((best / second) * 1000.0) : 9999L;
        var scoreScaled = Math.round(best * 1000.0);
        return new long[] {delayMs, scoreScaled, peakRatioScaled};
    }

    /** 语音频段能量首次持续抬升的时刻（ms） */
    static long energyOnsetMs(short[] samples, int sampleRate) {
        var onsets = speechOnsetsMs(samples, sampleRate);
        return onsets.isEmpty() ? 0L : onsets.get(0);
    }

    /** 所有「静 → 人声」抬升点（ms），用于短对白避开片头杂音误触发 */
    static List<Long> speechOnsetsMs(short[] samples, int sampleRate) {
        var onsets = new ArrayList<Long>();
        if (samples == null || samples.length < sampleRate / 5) {
            return onsets;
        }
        int win = Math.max(80, sampleRate / 50); // 20ms
        int hop = Math.max(40, win / 2);
        var energies = new ArrayList<Double>();
        for (int i = 0; i + win < samples.length; i += hop) {
            double e = 0;
            for (int j = 0; j < win; j++) {
                double v = samples[i + j];
                e += v * v;
            }
            energies.add(e / win);
        }
        if (energies.size() < 8) {
            return onsets;
        }
        int noiseN = Math.max(3, energies.size() / 7);
        double noiseSum = 0;
        for (int i = 0; i < noiseN; i++) {
            noiseSum += energies.get(i);
        }
        double noiseFloor = noiseSum / noiseN;
        double threshold = Math.max(noiseFloor * 4.5, 1.0e6);
        int need = 3;
        boolean inSpeech = false;
        for (int i = 0; i < energies.size() - need; i++) {
            boolean ok = true;
            for (int k = 0; k < need; k++) {
                if (energies.get(i + k) < threshold) {
                    ok = false;
                    break;
                }
            }
            if (ok && !inSpeech) {
                onsets.add(Math.round((i * (long) hop) * 1000.0 / sampleRate));
                inSpeech = true;
            } else if (!ok) {
                inSpeech = false;
            }
        }
        return onsets;
    }

    /** 在指定 lag（样本）处计算归一化互相关分 */
    static double scoreAtLag(short[] longSignal, short[] shortSignal, int lagSamples) {
        if (longSignal == null || shortSignal == null || lagSamples < 0
                || lagSamples + shortSignal.length > longSignal.length) {
            return Double.NEGATIVE_INFINITY;
        }
        double shortEnergy = 0;
        for (short s : shortSignal) {
            shortEnergy += (double) s * s;
        }
        if (shortEnergy < 1e-6) {
            return Double.NEGATIVE_INFINITY;
        }
        double dot = 0;
        double longEnergy = 0;
        for (int i = 0; i < shortSignal.length; i++) {
            double lv = longSignal[lagSamples + i];
            double sv = shortSignal[i];
            dot += lv * sv;
            longEnergy += lv * lv;
        }
        if (longEnergy < 1e-6) {
            return Double.NEGATIVE_INFINITY;
        }
        return dot / (Math.sqrt(longEnergy) * Math.sqrt(shortEnergy));
    }

    /**
     * 找原片主人声连续段的起点（ms）。短对白应对齐段首（开口），而非最响中段。
     */
    static long mainSpeechBurstStartMs(short[] samples, int sampleRate, long ttsMs, long videoMs) {
        if (samples == null || samples.length < sampleRate / 5 || sampleRate <= 0) {
            return 0L;
        }
        int win = Math.max(80, sampleRate / 50);
        int hop = Math.max(40, win / 2);
        var energies = new ArrayList<Double>();
        for (int i = 0; i + win < samples.length; i += hop) {
            double e = 0;
            for (int j = 0; j < win; j++) {
                double v = samples[i + j];
                e += v * v;
            }
            energies.add(e / win);
        }
        if (energies.size() < 8) {
            return 0L;
        }
        int noiseN = Math.max(3, energies.size() / 7);
        double noiseSum = 0;
        for (int i = 0; i < noiseN; i++) {
            noiseSum += energies.get(i);
        }
        double noiseFloor = noiseSum / noiseN;
        double threshold = Math.max(noiseFloor * 4.5, 1.0e6);

        // 允许约 350ms 能量谷，避免一句对白被拆成多段后锚到中后段
        int gapMax = Math.max(2, (int) Math.round(0.35 * sampleRate / hop));
        long bestStart = 0L;
        double bestScore = -1;
        int i = 0;
        while (i < energies.size()) {
            if (energies.get(i) < threshold) {
                i++;
                continue;
            }
            int start = i;
            double integral = 0;
            int lastLoud = i;
            while (i < energies.size()) {
                if (energies.get(i) >= threshold * 0.55) {
                    integral += energies.get(i);
                    lastLoud = i;
                    i++;
                    continue;
                }
                // 短间隙：向前看是否很快回到人声
                int look = i;
                int gap = 0;
                while (look < energies.size() && energies.get(look) < threshold * 0.55 && gap < gapMax) {
                    look++;
                    gap++;
                }
                if (gap < gapMax && look < energies.size() && energies.get(look) >= threshold * 0.55) {
                    while (i < look) {
                        integral += energies.get(i);
                        i++;
                    }
                    continue;
                }
                break;
            }
            int end = lastLoud + 1;
            i = Math.max(i, end);
            long startMs = Math.round((start * (long) hop) * 1000.0 / sampleRate);
            long endMs = Math.round((end * (long) hop) * 1000.0 / sampleRate);
            long dur = Math.max(0L, endMs - startMs);
            // 过短杂音 / 片尾残响丢弃
            if (dur < 400) {
                continue;
            }
            if (videoMs > 0 && startMs >= videoMs - 200) {
                continue;
            }
            // 更长、更响的连续人声段优先；明显偏好更早段（对齐开口）
            double durScore = Math.min(dur, Math.max(ttsMs * 2, 2000L));
            double earlyBonus = videoMs > 0 ? (1.15 - 0.45 * (startMs / (double) videoMs)) : 1.0;
            double score = integral * (0.35 + 0.65 * (durScore / Math.max(ttsMs, 800L))) * earlyBonus;
            if (score > bestScore) {
                bestScore = score;
                bestStart = startMs;
            }
        }
        return bestStart;
    }

    /** 窗口内均方能量；用于判断对白是否叠在真正的张嘴/人声时段上。 */
    static double speechEnergyInWindow(short[] samples, int sampleRate, long startMs, long durationMs) {
        if (samples == null || samples.length == 0 || sampleRate <= 0 || durationMs <= 0) {
            return 0;
        }
        int from = (int) Math.max(0L, Math.round(startMs * sampleRate / 1000.0));
        int len = (int) Math.max(1L, Math.round(durationMs * sampleRate / 1000.0));
        if (from >= samples.length) {
            return 0;
        }
        int to = Math.min(samples.length, from + len);
        double sum = 0;
        int n = to - from;
        for (int i = from; i < to; i++) {
            double v = samples[i];
            sum += v * v;
        }
        return n > 0 ? sum / n : 0;
    }

    /**
     * 在多个候选延迟中选最佳：优先「TTS 时段与原片人声能量重叠」，其次互相关；
     * 仅轻微偏后，避免把对白推到口型结束之后。
     */
    static long pickBestDelayMs(
            short[] videoSamples,
            short[] template,
            int sampleRate,
            List<Long> candidateDelaysMs,
            long videoMs) {
        return pickBestDelayMs(videoSamples, template, sampleRate, candidateDelaysMs, videoMs, 0L);
    }

    static long pickBestDelayMs(
            short[] videoSamples,
            short[] template,
            int sampleRate,
            List<Long> candidateDelaysMs,
            long videoMs,
            long ttsMs) {
        long windowMs = ttsMs > 80 ? ttsMs : (template != null
                ? Math.round(template.length * 1000.0 / sampleRate)
                : 800L);
        windowMs = Math.max(200L, Math.min(windowMs, 2500L));

        record Cand(long delayMs, double score) {
        }
        var scored = new ArrayList<Cand>();
        double maxEnergy = 1e-9;
        for (Long delayObj : candidateDelaysMs) {
            if (delayObj == null) {
                continue;
            }
            long delayMs = Math.max(0L, delayObj);
            if (videoMs > 0 && delayMs >= videoMs - 80) {
                continue;
            }
            maxEnergy = Math.max(maxEnergy, speechEnergyInWindow(videoSamples, sampleRate, delayMs, windowMs));
        }

        for (Long delayObj : candidateDelaysMs) {
            if (delayObj == null) {
                continue;
            }
            long delayMs = Math.max(0L, delayObj);
            if (videoMs > 0 && delayMs >= videoMs - 80) {
                continue;
            }
            int lag = (int) Math.round(delayMs * sampleRate / 1000.0);
            double xcorr = scoreAtLag(videoSamples, template, lag);
            if (!Double.isFinite(xcorr)) {
                xcorr = 0;
            }
            double energy = speechEnergyInWindow(videoSamples, sampleRate, delayMs, windowMs);
            double energyNorm = energy / maxEnergy;
            // 开口攻击段能量：避免把 TTS 锚在人声中段/尾段（口型已动完才出声）
            double attack = speechEnergyInWindow(videoSamples, sampleRate, delayMs, Math.min(280L, windowMs));
            double attackNorm = maxEnergy > 0 ? Math.min(1.0, attack / maxEnergy) : 0;
            // 人声重叠权重大于互相关：嘈杂环境音下 xcorr 常不可靠
            double score = 0.55 * energyNorm + 0.30 * attackNorm + 0.15 * Math.max(0, xcorr);
            // 若 TTS 尾部超出片长过多，惩罚
            if (videoMs > 0 && delayMs + windowMs > videoMs + 120) {
                score *= 0.55;
            }
            scored.add(new Cand(delayMs, score));
        }
        if (scored.isEmpty()) {
            return 0L;
        }
        double bestScore = scored.stream().mapToDouble(Cand::score).max().orElse(0);
        // 分数接近时取更早延迟，让对白跟上开口而不是跟人声最响处对齐
        long bestDelay = scored.stream()
                .filter(c -> c.score() >= bestScore * 0.90)
                .mapToLong(Cand::delayMs)
                .min()
                .orElse(0L);
        return bestDelay;
    }

    static AlignResult resolveAlign(Path video, Path dialogue) throws IOException, InterruptedException {
        var work = Files.createTempDirectory("df-align-");
        try {
            var videoPcm = work.resolve("video.s16");
            var ttsPcm = work.resolve("tts.s16");
            extractSpeechBandPcm(video, videoPcm);
            extractSpeechBandPcm(dialogue, ttsPcm);
            var videoSamples = readPcmS16le(videoPcm);
            var ttsSamples = readPcmS16le(ttsPcm);

            var ttsMs = probeDurationMs(dialogue);
            var videoMs = probeDurationMs(video);
            if (videoMs <= 0 && videoSamples.length > 0) {
                videoMs = Math.round(videoSamples.length * 1000.0 / PCM_RATE);
            }

            short[] template = null;
            if (ttsSamples.length > PCM_RATE / 5) {
                // 用更长模板（最多 2s）降低与片头噪声的偶然相关
                var templateLen = Math.min(ttsSamples.length, (int) (PCM_RATE * 2.0));
                template = new short[templateLen];
                System.arraycopy(ttsSamples, 0, template, 0, templateLen);
            }

            long globalXcorrDelay = 0;
            double globalXcorrScore = 0;
            double globalXcorrPeakRatio = 0;
            if (template != null && videoSamples.length > template.length) {
                var x = bestDelayByCrossCorrelation(videoSamples, template, PCM_RATE);
                globalXcorrDelay = x[0];
                globalXcorrScore = x[1] / 1000.0;
                if (x.length > 2) {
                    globalXcorrPeakRatio = x[2] / 1000.0;
                }
            }

            var onsets = speechOnsetsMs(videoSamples, PCM_RATE);
            var silenceWindow = detectSpeechWindow(video);
            var silenceDelay = silenceWindow.startMs();
            var burstStart = mainSpeechBurstStartMs(videoSamples, PCM_RATE, ttsMs, videoMs);

            var candidates = new ArrayList<Long>();
            if (burstStart >= 80) {
                candidates.add(burstStart);
            }
            // 弱互相关（嘈杂底噪）不要当候选，否则会把对白甩到片尾口型之后
            if (globalXcorrDelay > 0
                    && globalXcorrScore >= XCORR_MIN_SCORE
                    && globalXcorrPeakRatio >= XCORR_MIN_PEAK_RATIO) {
                candidates.add(globalXcorrDelay);
            }
            // 主人声段附近的 onset 作微调候选（±0.4s）
            for (Long o : onsets) {
                if (o == null) {
                    continue;
                }
                if (burstStart <= 0 || Math.abs(o - burstStart) <= 400) {
                    candidates.add(o);
                }
            }
            if (silenceDelay >= 80 && (burstStart <= 0 || Math.abs(silenceDelay - burstStart) <= 500)) {
                candidates.add(silenceDelay);
            }
            if (candidates.isEmpty()) {
                candidates.addAll(onsets);
            }
            if (candidates.isEmpty()) {
                candidates.add(0L);
            }

            long delayMs;
            String method;
            // Seedance：口型对齐优先主人声段；片头短促杂音 onset 不可抢跑对白
            long lipLeadDelay = 0L;
            if (burstStart >= 80) {
                Long earliestNearBurst = null;
                long windowFrom = Math.max(400L, burstStart - 700);
                for (Long o : onsets) {
                    if (o == null) {
                        continue;
                    }
                    if (o >= windowFrom && o <= burstStart + 80) {
                        if (earliestNearBurst == null || o < earliestNearBurst) {
                            earliestNearBurst = o;
                        }
                    }
                }
                long burstCand = burstStart + 500;
                if (earliestNearBurst != null) {
                    long earlyCand = earliestNearBurst + 400;
                    // 最早 onset 若明显早于主人声段，用能量判断是否片头杂音
                    if (burstStart - earliestNearBurst > 350) {
                        var earlyE = speechEnergyInWindow(videoSamples, PCM_RATE, earlyCand, Math.max(ttsMs, 400));
                        var burstE = speechEnergyInWindow(videoSamples, PCM_RATE, burstCand, Math.max(ttsMs, 400));
                        if (burstE >= earlyE * 1.18) {
                            lipLeadDelay = burstCand;
                        } else {
                            lipLeadDelay = earlyCand;
                        }
                    } else {
                        lipLeadDelay = earlyCand;
                    }
                } else {
                    lipLeadDelay = Math.max(0L, burstCand);
                }
            }
            if (lipLeadDelay > 0 && lipLeadDelay < 500) {
                lipLeadDelay = 500L;
            }

            if (lipLeadDelay > 0 && ttsMs > 0 && ttsMs <= 3500) {
                // 短对白：强制跟口型提前点，能量打分常把 TTS 锚到人声最响中段导致「嘴动完才出声」
                delayMs = lipLeadDelay;
                method = "lip-lead";
            } else if (burstStart >= 80 && template == null) {
                delayMs = lipLeadDelay > 0 ? lipLeadDelay : burstStart;
                method = "lip-lead";
            } else if (template != null) {
                if (lipLeadDelay > 0) {
                    candidates.add(lipLeadDelay);
                }
                delayMs = pickBestDelayMs(videoSamples, template, PCM_RATE, candidates, videoMs, ttsMs);
                method = "multi-energy";
                if (lipLeadDelay > 0 && delayMs > lipLeadDelay + 250) {
                    delayMs = lipLeadDelay;
                    method = "lip-lead";
                } else if (burstStart >= 80 && delayMs > burstStart + 350) {
                    delayMs = lipLeadDelay > 0 ? lipLeadDelay : burstStart;
                    method = "lip-lead";
                }
                // 仅当全局互相关足够强、且明显更贴人声时才覆盖（禁止弱相关甩到片尾）
                if (globalXcorrScore >= XCORR_MIN_SCORE
                        && globalXcorrPeakRatio >= XCORR_MIN_PEAK_RATIO
                        && globalXcorrDelay > delayMs + 250
                        && globalXcorrDelay < delayMs + 1800) {
                    var earlyE = speechEnergyInWindow(videoSamples, PCM_RATE, delayMs, Math.max(ttsMs, 400));
                    var lateE = speechEnergyInWindow(videoSamples, PCM_RATE, globalXcorrDelay, Math.max(ttsMs, 400));
                    if (lateE >= earlyE * 1.15) {
                        delayMs = globalXcorrDelay;
                        method = "multi-energy+global";
                    }
                }
            } else if (!onsets.isEmpty()) {
                delayMs = lipLeadDelay > 0 ? lipLeadDelay : (burstStart >= 80 ? burstStart : onsets.get(0));
                method = lipLeadDelay > 0 ? "lip-lead" : (burstStart >= 80 ? "burst-start" : "onset-first");
            } else {
                delayMs = Math.max(silenceDelay, 0L);
                method = "silence-fallback";
            }

            // 仅当「早起点」几乎无人声、而主人声段明显更强时，才强制后移（防片头抢跑）
            if (ttsMs > 0 && ttsMs <= 4000 && burstStart >= 1000 && delayMs + 900 < burstStart) {
                var earlyE = speechEnergyInWindow(videoSamples, PCM_RATE, delayMs, ttsMs);
                var burstE = speechEnergyInWindow(videoSamples, PCM_RATE, burstStart, ttsMs);
                if (burstE > earlyE * 3.0) {
                    delayMs = lipLeadDelay > 0 ? lipLeadDelay : burstStart;
                    method = method + "+forceBurst";
                }
            }

            if (videoMs > 0 && delayMs + Math.min(ttsMs, 500) > videoMs) {
                delayMs = Math.max(0, videoMs - Math.min(ttsMs, videoMs));
            }

            var tempo = 1.0;
            log.info(
                    "Dialogue align method={} delayMs={} tempo={} burstStart={} lipLead={} globalXcorr={} peakRatio={} onsets={} videoMs={} ttsMs={}",
                    method, delayMs, tempo, burstStart, lipLeadDelay, globalXcorrScore, globalXcorrPeakRatio, onsets, videoMs, ttsMs);
            return new AlignResult(delayMs, tempo, method);
        } finally {
            deleteRecursive(work);
        }
    }

    static void extractSpeechBandPcm(Path media, Path pcmOut) throws IOException, InterruptedException {
        // 人声频带 + 归一化，削弱引擎底噪
        runFfmpeg(List.of(
                "-y",
                "-hide_banner", "-loglevel", "error",
                "-i", media.toString(),
                "-vn",
                "-ac", "1",
                "-ar", String.valueOf(PCM_RATE),
                "-af", "highpass=f=250,lowpass=f=3400,dynaudnorm=f=75:g=15",
                "-f", "s16le",
                pcmOut.toString()));
    }

    static short[] readPcmS16le(Path pcm) throws IOException {
        var bytes = Files.readAllBytes(pcm);
        var samples = new short[bytes.length / 2];
        var buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < samples.length; i++) {
            samples[i] = buf.getShort();
        }
        return samples;
    }

    private static double parseDurationSeconds(String log) {
        var matcher = DURATION.matcher(log);
        if (!matcher.find()) {
            return -1;
        }
        var h = Integer.parseInt(matcher.group(1));
        var m = Integer.parseInt(matcher.group(2));
        var s = Double.parseDouble(matcher.group(3));
        return h * 3600 + m * 60 + s;
    }

    static void mixAlignedZeroDelay(
            Path video,
            Path dialogue,
            Path output,
            double ambientVolume,
            double dialogueVolume) throws IOException, InterruptedException {
        mixWithFilter(video, dialogue, output,
                buildAlignedMixFilter(ambientVolume, dialogueVolume, 0L, 1.0));
    }

    static void mixAligned(
            Path video,
            Path dialogue,
            Path output,
            double ambientVolume,
            double dialogueVolume) throws IOException, InterruptedException {
        var align = resolveAlign(video, dialogue);
        var filter = buildAlignedMixFilter(ambientVolume, dialogueVolume, align.delayMs(), align.tempo());
        log.info("Dialogue mix align delayMs={} tempo={} method={}", align.delayMs(), align.tempo(), align.method());
        mixWithFilter(video, dialogue, output, filter);
    }

    /**
     * 保留 Seedance 原声：人声频段 EQ + 压限 + 轻度增益，环境底噪 duck；不叠 TTS。
     */
    static void enhanceOriginalVoice(Path video, Path output) throws IOException, InterruptedException {
        // full：压低作环境底；band：人声带增强后再与底轨 amix
        var filter = "[0:a]asplit=2[full][band];"
                + "[band]highpass=f=200,lowpass=f=4000,"
                + "equalizer=f=1200:t=q:w=1:g=2,"
                + "equalizer=f=2800:t=q:w=1.2:g=3,"
                + "compand=attacks=0.02:decays=0.18:points=-80/-90|-50/-40|-30/-18|-10/-6|0/-3:soft-knee=6:gain=2.5,"
                + "volume=1.35[voc];"
                + "[full]volume=0.42,highpass=f=50[amb];"
                + "[amb][voc]amix=inputs=2:duration=first:dropout_transition=0:normalize=0,"
                + "alimiter=limit=0.95:level=false[a]";
        try {
            runFfmpeg(List.of(
                    "-y",
                    "-hide_banner", "-loglevel", "error",
                    "-i", video.toString(),
                    "-filter_complex", filter,
                    "-map", "0:v:0",
                    "-map", "[a]",
                    "-c:v", "copy",
                    "-c:a", "aac",
                    "-b:a", "192k",
                    "-shortest",
                    output.toString()));
        } catch (IllegalStateException ex) {
            // 无音轨时原样复制画面
            log.warn("enhanceOriginalVoice fallback copy: {}", ex.getMessage());
            Files.copy(video, output, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return;
        }
        if (!Files.exists(output) || Files.size(output) <= 0) {
            throw new IllegalStateException("FFmpeg 原声增强未产出文件");
        }
    }

    /**
     * 将对白烧录为画面底部硬字幕（白字黑边）。无中文字体时原样复制，不失败。
     */
    static boolean burnHardSubtitles(Path video, Path output, String dialogue)
            throws IOException, InterruptedException {
        var text = normalizeSubtitleText(dialogue);
        if (text == null || text.isBlank()) {
            Files.copy(video, output, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return false;
        }
        var font = findCjkFontPath();
        if (font == null) {
            log.warn("未找到中文字体，跳过硬字幕烧录");
            Files.copy(video, output, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return false;
        }
        var workDir = video.getParent() != null ? video.getParent() : Path.of(".");
        var textFile = workDir.resolve("hardsub-text.txt");
        Files.writeString(textFile, text, java.nio.charset.StandardCharsets.UTF_8);
        var fontEsc = escapeDrawtextPath(font);
        var textEsc = escapeDrawtextPath(textFile.toAbsolutePath().toString());
        var vf = "drawtext=fontfile=" + fontEsc
                + ":textfile=" + textEsc
                + ":fontsize=h*0.048:fontcolor=white:borderw=3:bordercolor=black@0.9"
                + ":x=(w-text_w)/2:y=h-text_h-h*0.07";
        runFfmpeg(List.of(
                "-y",
                "-hide_banner", "-loglevel", "error",
                "-i", video.toString(),
                "-vf", vf,
                "-c:v", "libx264", "-preset", "fast", "-crf", "20",
                "-c:a", "copy",
                output.toString()));
        if (!Files.exists(output) || Files.size(output) <= 0) {
            throw new IllegalStateException("FFmpeg 字幕烧录未产出文件");
        }
        return true;
    }

    static String normalizeSubtitleText(String dialogue) {
        if (dialogue == null) {
            return "";
        }
        var text = dialogue.trim();
        // 去掉「角色名：」前缀，字幕只留台词
        text = text.replaceFirst("^[\\u4e00-\\u9fffA-Za-z0-9·]{1,16}[：:]\\s*", "").trim();
        return text;
    }

    static String findCjkFontPath() {
        for (var candidate : List.of(
                "/usr/share/fonts/noto/NotoSansCJK-Regular.ttc",
                "/usr/share/fonts/noto/NotoSansCJKsc-Regular.otf",
                "/usr/share/fonts/noto-cjk/NotoSansCJK-Regular.ttc",
                "/usr/share/fonts/truetype/noto/NotoSansCJK-Regular.ttc",
                "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc",
                "/usr/share/fonts/wqy-zenhei/wqy-zenhei.ttc",
                "C:\\\\Windows\\\\Fonts\\\\msyh.ttc",
                "C:\\\\Windows\\\\Fonts\\\\simhei.ttf")) {
            if (Files.isRegularFile(Path.of(candidate))) {
                return candidate;
            }
        }
        // Alpine/Debian 字体路径偶有差异，扫一遍常见目录
        for (var root : List.of("/usr/share/fonts", "/usr/local/share/fonts")) {
            var base = Path.of(root);
            if (!Files.isDirectory(base)) {
                continue;
            }
            try (var walk = Files.walk(base, 4)) {
                var found = walk
                        .filter(Files::isRegularFile)
                        .map(Path::toString)
                        .filter(p -> {
                            var lower = p.toLowerCase(Locale.ROOT);
                            return lower.contains("notosanscjk")
                                    || lower.contains("noto-sans-cjk")
                                    || lower.contains("wqy")
                                    || lower.contains("sourcehan")
                                    || lower.endsWith("msyh.ttc")
                                    || lower.endsWith("simhei.ttf");
                        })
                        .findFirst()
                        .orElse(null);
                if (found != null) {
                    return found;
                }
            } catch (IOException ignored) {
            }
        }
        return null;
    }

    /** drawtext 路径：反斜杠与冒号需转义 */
    static String escapeDrawtextPath(String path) {
        return path.replace("\\", "/").replace(":", "\\:");
    }

    private static void mixWithFilter(Path video, Path dialogue, Path output, String filter)
            throws IOException, InterruptedException {
        try {
            runFfmpeg(List.of(
                    "-y",
                    "-hide_banner", "-loglevel", "error",
                    "-i", video.toString(),
                    "-i", dialogue.toString(),
                    "-filter_complex", filter,
                    "-map", "0:v:0",
                    "-map", "[a]",
                    "-c:v", "copy",
                    "-c:a", "aac",
                    "-b:a", "192k",
                    "-shortest",
                    output.toString()));
        } catch (IllegalStateException noAudio) {
            runFfmpeg(List.of(
                    "-y",
                    "-hide_banner", "-loglevel", "error",
                    "-i", video.toString(),
                    "-i", dialogue.toString(),
                    "-map", "0:v:0",
                    "-map", "1:a:0",
                    "-c:v", "copy",
                    "-c:a", "aac",
                    "-b:a", "192k",
                    "-shortest",
                    output.toString()));
        }
        if (!Files.exists(output) || Files.size(output) <= 0) {
            throw new IllegalStateException("FFmpeg 对白混音未产出文件");
        }
    }

    private static void runFfmpeg(List<String> args) throws IOException, InterruptedException {
        var command = new ArrayList<String>();
        command.add("ffmpeg");
        command.addAll(args);
        var process = new ProcessBuilder(command).redirectErrorStream(true).start();
        var err = new StringBuilder();
        try (var reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (err.length() < 2000) {
                    err.append(line).append('\n');
                }
            }
        }
        if (process.waitFor() != 0) {
            throw new IllegalStateException("FFmpeg 执行失败: " + err);
        }
    }

    private static void deleteRecursive(Path root) {
        try (var walk = Files.walk(root)) {
            walk.sorted((a, b) -> b.compareTo(a)).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                }
            });
        } catch (IOException ignored) {
        }
    }
}
