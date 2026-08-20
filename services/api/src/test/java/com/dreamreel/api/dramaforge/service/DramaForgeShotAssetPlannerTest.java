package com.dreamreel.api.dramaforge.service;

import com.dreamreel.api.dramaforge.domain.DramaForgeAsset;
import com.dreamreel.api.dramaforge.domain.DramaForgeAssetType;
import com.dreamreel.api.dramaforge.domain.DramaForgeShot;
import com.dreamreel.api.dramaforge.repository.DramaForgeAssetRepository;
import com.dreamreel.api.dramaforge.repository.DramaForgeConfigRepository;
import com.dreamreel.api.dramaforge.repository.DramaForgeShotRepository;
import com.dreamreel.api.client.TokenFreeClient;
import com.dreamreel.api.config.TokenFreeProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DramaForgeShotAssetPlannerTest {

    @Mock DramaForgeShotRepository shotRepository;
    @Mock DramaForgeAssetRepository assetRepository;
    @Mock DramaForgeConfigRepository configRepository;
    @Mock TokenFreeClient tokenFreeClient;
    @Mock TokenFreeProperties tokenFreeProperties;

    private DramaForgeShotAssetPlanner planner;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final UUID projectId = UUID.randomUUID();
    private final UUID episodeId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        planner = new DramaForgeShotAssetPlanner(
                shotRepository,
                assetRepository,
                configRepository,
                tokenFreeClient,
                tokenFreeProperties,
                objectMapper);
    }

    @Test
    void explicitEmptyCharacterRefsOnlyWhenJsonArray() {
        assertFalse(DramaForgeShotAssetPlanner.isExplicitEmptyCharacterRefs(null));
        assertFalse(DramaForgeShotAssetPlanner.isExplicitEmptyCharacterRefs(""));
        assertFalse(DramaForgeShotAssetPlanner.isExplicitEmptyCharacterRefs("[\"陈国安\"]"));
        assertTrue(DramaForgeShotAssetPlanner.isExplicitEmptyCharacterRefs("[]"));
        assertTrue(DramaForgeShotAssetPlanner.isExplicitEmptyCharacterRefs(" [] "));
    }

    @Test
    void inheritedSceneWithNullCharactersStillMatchesNamedCharacter() {
        var shot = shot(1, "陈国安走在夜晚的城市街道上", "夜晚的城市街道", null);
        var assets = List.of(character("陈国安"), scene("夜晚的城市街道"));
        stubPlanLocally(List.of(shot), assets);

        planner.planEpisodeShotsLocally(projectId, episodeId, assets);

        var saved = captureLastSavedShot();
        assertEquals("夜晚的城市街道", saved.getSceneRef());
        assertTrue(saved.getCharacterRefsJson().contains("陈国安"));
    }

    @Test
    void explicitEmptyCharactersWithSceneArePreserved() {
        var shot = shot(1, "陈国安走在夜晚的城市街道上", "夜晚的城市街道", "[]");
        var assets = List.of(character("陈国安"), scene("夜晚的城市街道"));
        stubPlanLocally(List.of(shot), assets);

        planner.planEpisodeShotsLocally(projectId, episodeId, assets);

        var saved = captureLastSavedShot();
        assertEquals("[]", saved.getCharacterRefsJson());
        assertEquals("夜晚的城市街道", saved.getSceneRef());
    }

    @Test
    void emptyBodyWithSceneDoesNotInventCharacters() {
        var shot = shot(1, "夜晚城市空镜，无人物出镜", "夜晚的城市街道", null);
        var assets = List.of(character("陈国安"), scene("夜晚的城市街道"));
        stubPlanLocally(List.of(shot), assets);

        planner.planEpisodeShotsLocally(projectId, episodeId, assets);

        var saved = captureLastSavedShot();
        assertEquals("[]", saved.getCharacterRefsJson());
    }

    private void stubPlanLocally(List<DramaForgeShot> shots, List<DramaForgeAsset> assets) {
        when(configRepository.findByProjectId(projectId)).thenReturn(Optional.empty());
        when(shotRepository.findByEpisodeIdOrderByShotNumberAsc(episodeId)).thenReturn(shots);
        when(shotRepository.save(any(DramaForgeShot.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private DramaForgeShot captureLastSavedShot() {
        var captor = ArgumentCaptor.forClass(DramaForgeShot.class);
        verify(shotRepository, times(1)).save(captor.capture());
        return captor.getValue();
    }

    private DramaForgeShot shot(int number, String description, String sceneRef, String characterRefsJson) {
        var shot = new DramaForgeShot();
        shot.setId(UUID.randomUUID());
        shot.setEpisodeId(episodeId);
        shot.setShotNumber(number);
        shot.setDescription(description);
        shot.setSceneRef(sceneRef);
        shot.setCharacterRefsJson(characterRefsJson);
        return shot;
    }

    private static DramaForgeAsset character(String name) {
        var asset = new DramaForgeAsset();
        asset.setId(UUID.randomUUID());
        asset.setType(DramaForgeAssetType.CHARACTER);
        asset.setName(name);
        asset.setReferenceImageUrl("https://example.com/" + name + ".png");
        return asset;
    }

    private static DramaForgeAsset scene(String name) {
        var asset = new DramaForgeAsset();
        asset.setId(UUID.randomUUID());
        asset.setType(DramaForgeAssetType.SCENE);
        asset.setName(name);
        asset.setReferenceImageUrl("https://example.com/scene.png");
        return asset;
    }
}
