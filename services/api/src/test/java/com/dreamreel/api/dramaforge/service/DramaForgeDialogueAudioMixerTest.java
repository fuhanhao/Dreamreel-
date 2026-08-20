package com.dreamreel.api.dramaforge.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DramaForgeDialogueAudioMixerTest {

    @Test
    void parseSpeechWindow_leadingSilenceThenSpeech() {
        var log = """
                Duration: 00:00:05.00, start: 0.000000, bitrate: 128 kb/s
                [silencedetect @ 0x1] silence_start: 0
                [silencedetect @ 0x1] silence_end: 1.25 | silence_duration: 1.25
                [silencedetect @ 0x1] silence_start: 3.80
                """;
        var window = DramaForgeDialogueAudioMixer.parseSpeechWindow(log);
        assertEquals(1250L, window.startMs());
        assertEquals(3800L, window.endMs());
    }

    @Test
    void parseSpeechWindow_speechFromStart() {
        var log = """
                Duration: 00:00:04.00, start: 0.000000, bitrate: 128 kb/s
                [silencedetect @ 0x1] silence_start: 3.2
                """;
        var window = DramaForgeDialogueAudioMixer.parseSpeechWindow(log);
        assertEquals(0L, window.startMs());
        assertEquals(3200L, window.endMs());
    }

    @Test
    void buildAlignedMixFilter_includesDelayAndTempo() {
        var filter = DramaForgeDialogueAudioMixer.buildAlignedMixFilter(0.025, 3.0, 1200, 1.25);
        assertTrue(filter.contains("adelay=1200|1200"));
        assertTrue(filter.contains("atempo=1.2500"));
        assertTrue(filter.contains("normalize=0"));
    }

    @Test
    void tempoForDurations_speedsUpLongTts() {
        var tempo = DramaForgeDialogueAudioMixer.tempoForDurations(2000, 3000);
        assertEquals(1.5, tempo, 0.01);
    }

    @Test
    void bestDelayByCrossCorrelation_findsEmbeddedTone() {
        int rate = 8000;
        var longSig = new short[rate * 3];
        var shortSig = new short[rate / 2];
        for (int i = 0; i < shortSig.length; i++) {
            shortSig[i] = (short) (Math.sin(2 * Math.PI * 440 * i / rate) * 12000);
        }
        int embedAt = rate; // 1.0s
        System.arraycopy(shortSig, 0, longSig, embedAt, shortSig.length);
        var result = DramaForgeDialogueAudioMixer.bestDelayByCrossCorrelation(longSig, shortSig, rate);
        assertEquals(1000L, result[0], 20L);
        assertTrue(result[1] > 80); // score*1000
    }

    @Test
    void energyOnsetMs_detectsRiseAfterQuiet() {
        int rate = 8000;
        var samples = new short[rate * 2];
        // 前 0.8s 安静，之后响
        for (int i = (int) (rate * 0.8); i < samples.length; i++) {
            samples[i] = (short) (Math.sin(2 * Math.PI * 500 * i / (double) rate) * 10000);
        }
        var onset = DramaForgeDialogueAudioMixer.energyOnsetMs(samples, rate);
        assertTrue(onset >= 700 && onset <= 1000, "onset=" + onset);
    }

    @Test
    void speechOnsetsMs_findsMultipleBursts() {
        int rate = 8000;
        var samples = new short[rate * 5];
        // 前段留静音估噪声底，再给两段人声突发
        for (int i = (int) (rate * 0.8); i < rate * 1.2; i++) {
            samples[i] = (short) (Math.sin(2 * Math.PI * 300 * i / (double) rate) * 10000);
        }
        for (int i = (int) (rate * 2.8); i < rate * 3.6; i++) {
            samples[i] = (short) (Math.sin(2 * Math.PI * 500 * i / (double) rate) * 12000);
        }
        var onsets = DramaForgeDialogueAudioMixer.speechOnsetsMs(samples, rate);
        assertTrue(onsets.size() >= 2, "onsets=" + onsets);
        assertTrue(onsets.get(onsets.size() - 1) >= 2500, "late=" + onsets);
    }

    @Test
    void pickBestDelayMs_prefersStrongerSpeechOverlap() {
        int rate = 8000;
        var longSig = new short[rate * 4];
        var template = new short[rate / 2];
        for (int i = 0; i < template.length; i++) {
            template[i] = (short) (Math.sin(2 * Math.PI * 440 * i / (double) rate) * 10000);
        }
        // 弱匹配在 0.2s，强匹配在 2.0s
        System.arraycopy(template, 0, longSig, (int) (rate * 0.2), template.length);
        for (int i = 0; i < template.length; i++) {
            longSig[(int) (rate * 2.0) + i] = (short) (template[i] * 1.2);
        }
        var picked = DramaForgeDialogueAudioMixer.pickBestDelayMs(
                longSig,
                template,
                rate,
                List.of(200L, 2000L),
                4000L,
                500L);
        assertTrue(Math.abs(picked - 2000L) <= 50L, "picked=" + picked);
    }

    @Test
    void mainSpeechBurstStartMs_returnsStartOfLongLoudRun() {
        int rate = 8000;
        var samples = new short[rate * 5];
        // 短促杂音
        for (int i = (int) (rate * 0.4); i < (int) (rate * 0.55); i++) {
            samples[i] = (short) (Math.sin(2 * Math.PI * 200 * i / (double) rate) * 8000);
        }
        // 主人声 1.5s–3.0s
        for (int i = (int) (rate * 1.5); i < (int) (rate * 3.0); i++) {
            samples[i] = (short) (Math.sin(2 * Math.PI * 300 * i / (double) rate) * 14000);
        }
        var start = DramaForgeDialogueAudioMixer.mainSpeechBurstStartMs(samples, rate, 1200L, 5000L);
        assertTrue(start >= 1400 && start <= 1700, "burstStart=" + start);
    }

    @Test
    void pickBestDelayMs_doesNotPreferWeakLateXcorrOverMainSpeech() {
        int rate = 8000;
        var longSig = new short[rate * 5];
        var template = new short[rate]; // 1s TTS
        for (int i = 0; i < template.length; i++) {
            template[i] = (short) (Math.sin(2 * Math.PI * 300 * i / (double) rate) * 8000);
        }
        // 主对白人声 1.6s–2.8s
        for (int i = (int) (rate * 1.6); i < (int) (rate * 2.8); i++) {
            longSig[i] = (short) (Math.sin(2 * Math.PI * 300 * i / (double) rate) * 14000);
        }
        // 片尾弱噪声 3.5s 起（模拟弱互相关误匹配）
        for (int i = (int) (rate * 3.5); i < (int) (rate * 4.2); i++) {
            longSig[i] = (short) (Math.sin(2 * Math.PI * 900 * i / (double) rate) * 3000);
        }
        var picked = DramaForgeDialogueAudioMixer.pickBestDelayMs(
                longSig,
                template,
                rate,
                List.of(1600L, 3500L),
                5000L,
                1200L);
        assertTrue(picked <= 2000L, "picked late=" + picked);
    }
}
