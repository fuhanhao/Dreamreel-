package com.dreamreel.api.service;

import com.dreamreel.api.dramaforge.repository.DramaForgeAssetRepository;
import com.dreamreel.api.domain.GenerationJob;
import com.dreamreel.api.dto.GenerationJobResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 补全视频任务的多图参考 URL：旧任务只存了首图时，从提示词【参考图映射】反查项目资产设计图。
 */
@Component
public class GenerationReferenceImageEnricher {

    private static final Pattern IMAGE_MAP = Pattern.compile(
            "\\[Image\\s*\\d+\\]\\s*=\\s*(?:角色|场景|道具)「([^」]+)」");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final DramaForgeAssetRepository assetRepository;

    public GenerationReferenceImageEnricher(DramaForgeAssetRepository assetRepository) {
        this.assetRepository = assetRepository;
    }

    /** 解析多图 URL；详情页可选择回填。列表只读解析、不写库。 */
    public List<String> resolve(GenerationJob job) {
        return resolveInternal(job, false);
    }

    public List<String> resolveAndMaybeBackfill(GenerationJob job) {
        return resolveInternal(job, true);
    }

    private List<String> resolveInternal(GenerationJob job, boolean backfill) {
        var stored = GenerationJobResponse.resolveReferenceImageUrls(job);
        var mappedNames = extractMappedAssetNames(job.getPrompt());
        if (mappedNames.size() <= 1 || stored.size() >= mappedNames.size()) {
            return stored;
        }
        if (job.getProjectId() == null) {
            return stored;
        }

        var assets = assetRepository.findByProjectIdOrderBySortOrderAscNameAsc(job.getProjectId());
        var urls = new ArrayList<String>();
        var seen = new LinkedHashSet<String>();
        for (var name : mappedNames) {
            assets.stream()
                    .filter(a -> a.getName().equalsIgnoreCase(name))
                    .filter(a -> a.getReferenceImageUrl() != null && !a.getReferenceImageUrl().isBlank())
                    .findFirst()
                    .ifPresent(asset -> {
                        var url = asset.getReferenceImageUrl().trim();
                        if (seen.add(url)) {
                            urls.add(url);
                        }
                    });
        }
        if (urls.size() <= stored.size()) {
            return stored.isEmpty() ? urls : stored;
        }
        if (backfill) {
            try {
                job.setReferenceImageUrl(urls.getFirst());
                job.setReferenceImageUrls(MAPPER.writeValueAsString(urls));
            } catch (Exception ignored) {
                job.setReferenceImageUrl(urls.getFirst());
            }
        }
        return urls;
    }

    static List<String> extractMappedAssetNames(String prompt) {
        var names = new ArrayList<String>();
        if (prompt == null || prompt.isBlank()) {
            return names;
        }
        var matcher = IMAGE_MAP.matcher(prompt);
        while (matcher.find()) {
            var name = matcher.group(1).trim();
            if (!name.isEmpty() && !names.contains(name)) {
                names.add(name);
            }
        }
        return names;
    }
}
