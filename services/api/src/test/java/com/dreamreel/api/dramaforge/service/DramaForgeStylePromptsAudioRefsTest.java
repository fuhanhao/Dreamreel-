package com.dreamreel.api.dramaforge.service;

import com.dreamreel.api.dramaforge.domain.DramaForgeAsset;
import com.dreamreel.api.dramaforge.domain.DramaForgeAssetType;
import com.dreamreel.api.dramaforge.domain.DramaForgeConfig;
import com.dreamreel.api.dramaforge.domain.DramaForgeContentMode;
import com.dreamreel.api.dramaforge.domain.DramaForgeShot;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DramaForgeStylePromptsAudioRefsTest {

    @Test
    void blankDialogueYieldsNoAudioRefs() {
        var shot = new DramaForgeShot();
        shot.setDescription("【镜头】陈国安抬头看后视镜。【角色线索】陈国安");
        shot.setDialogue("");
        shot.setCharacterRefsJson("[\"陈国安\"]");

        var asset = new DramaForgeAsset();
        asset.setId(UUID.randomUUID());
        asset.setType(DramaForgeAssetType.CHARACTER);
        asset.setName("陈国安");
        asset.setVoiceSampleUrl("https://example.com/voice.mp3");
        asset.setVoiceLabel("中年男");

        var refs = DramaForgeStylePrompts.resolveAudioRefs(shot, List.of("陈国安"), List.of(asset));
        assertTrue(refs.urls().isEmpty());
        assertTrue(refs.labels().isEmpty());
    }

    @Test
    void dialogueBindsSpeakerAudio() {
        var shot = new DramaForgeShot();
        shot.setDescription("【镜头】陈国安回头喊了一声。【角色线索】陈国安");
        shot.setDialogue("小朋友，到终点站了。");
        shot.setCharacterRefsJson("[\"陈国安\"]");

        var asset = new DramaForgeAsset();
        asset.setId(UUID.randomUUID());
        asset.setType(DramaForgeAssetType.CHARACTER);
        asset.setName("陈国安");
        asset.setVoiceSampleUrl("https://example.com/voice.mp3");

        var refs = DramaForgeStylePrompts.resolveAudioRefs(shot, List.of("陈国安"), List.of(asset));
        assertTrue(refs.urls().size() == 1);
        assertTrue(refs.labels().getFirst().startsWith("voice:"));
    }

    @Test
    void dialoguePromptPrioritizesClearSpeechMix() {
        var config = new DramaForgeConfig();
        config.setContentMode(DramaForgeContentMode.DRAMA);
        var shot = new DramaForgeShot();
        shot.setDescription("车厢内陈国安看向小孩。");
        shot.setDialogue("小朋友，一个人坐车呀");
        shot.setCharacterRefsJson("[\"陈国安\"]");

        var prompt = DramaForgeStylePrompts.videoPromptFromAssets(
                config,
                shot,
                List.of("陈国安"),
                List.of(),
                List.of(),
                List.of("角色:陈国安"),
                List.of("dialogue:小朋友，一个人坐车呀"));

        assertTrue(prompt.contains("【对白锁定】"));
        assertTrue(prompt.contains("【音频负面】"));
        assertTrue(prompt.contains("对白清晰") || prompt.contains("清晰可辨"));
        assertTrue(prompt.contains("【Seedance字幕】"));
        assertTrue(prompt.contains("小朋友，一个人坐车呀"));
        assertFalse(prompt.contains("【音色锁定】"));
    }

    @Test
    void smartMultiFramePromptUsesOmniReferenceFirstLastEffect() {
        var config = new DramaForgeConfig();
        config.setContentMode(DramaForgeContentMode.DRAMA);
        var shot = new DramaForgeShot();
        shot.setDescription("陈国安继续开车。");
        shot.setDialogue("慢一点。");
        shot.setCharacterRefsJson("[\"陈国安\"]");

        var prompt = DramaForgeStylePrompts.videoPromptFromAssets(
                config,
                shot,
                List.of("陈国安"),
                List.of(),
                List.of(),
                List.of("上一镜尾帧", "镜头尾帧", "角色:陈国安"),
                List.of());

        assertTrue(prompt.contains("【全能参考·首尾帧效果】"));
        assertTrue(prompt.contains("@Image1=上一镜尾帧"));
        assertTrue(prompt.contains("@Image2=镜头尾帧"));
        assertTrue(prompt.contains("reference_image") || prompt.contains("不用首尾帧 API"));
        assertTrue(prompt.contains("慢一点"));
        assertFalse(prompt.contains("【资产参考生视频】"));
    }

    @Test
    void smartMultiFramePromptIncludesConcreteFrameUrls() {
        var config = new DramaForgeConfig();
        config.setContentMode(DramaForgeContentMode.DRAMA);
        var shot = new DramaForgeShot();
        shot.setDescription("陈国安继续开车。");
        shot.setDialogue("");
        shot.setCharacterRefsJson("[\"陈国安\"]");

        var firstUrl = "https://example.com/prev-tail.jpg";
        var lastUrl = "https://example.com/shot-last.jpg";
        var prompt = DramaForgeStylePrompts.videoPromptFromAssets(
                config,
                shot,
                List.of("陈国安"),
                List.of(),
                List.of(),
                List.of("上一镜尾帧", "镜头尾帧", "角色:陈国安"),
                List.of(),
                List.of(firstUrl, lastUrl, "https://example.com/char.jpg"));

        assertTrue(prompt.contains("【首尾帧绑定】"));
        assertTrue(prompt.contains("视频首帧=@" + firstUrl));
        assertTrue(prompt.contains("视频尾帧=@" + lastUrl));
        assertTrue(prompt.contains(
                "【首尾帧绑定】视频首帧=@" + firstUrl + "；视频尾帧=@" + lastUrl + "；"));
    }
}
