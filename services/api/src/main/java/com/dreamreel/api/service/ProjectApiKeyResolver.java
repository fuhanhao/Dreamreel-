package com.dreamreel.api.service;

import com.dreamreel.api.config.TokenFreeApiKeyResolver;
import com.dreamreel.api.exception.ResourceNotFoundException;
import com.dreamreel.api.repository.ProjectRepository;
import com.dreamreel.api.repository.UserRepository;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class ProjectApiKeyResolver {

    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final TokenFreeApiKeyResolver apiKeyResolver;

    public ProjectApiKeyResolver(
            ProjectRepository projectRepository,
            UserRepository userRepository,
            TokenFreeApiKeyResolver apiKeyResolver) {
        this.projectRepository = projectRepository;
        this.userRepository = userRepository;
        this.apiKeyResolver = apiKeyResolver;
    }

    public UUID resolveOwnerId(UUID projectId) {
        var project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("项目不存在: " + projectId));
        return project.getUserId();
    }

    public String resolve(UUID projectId, String headerApiKey) {
        String userApiKey = null;
        if (projectId != null) {
            userApiKey = projectRepository.findById(projectId)
                    .flatMap(project -> userRepository.findById(project.getUserId()))
                    .map(user -> user.getTokenfreeApiKey())
                    .orElse(null);
        }
        var apiKey = apiKeyResolver.resolve(headerApiKey, userApiKey);
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("请配置 TokenFree API Key（个人设置或环境变量 TOKENFREE_API_KEY）");
        }
        return apiKey;
    }

    public String resolveOwnerArkApiKey(UUID projectId) {
        if (projectId == null) {
            return null;
        }
        return projectRepository.findById(projectId)
                .flatMap(project -> userRepository.findById(project.getUserId()))
                .map(user -> user.getArkApiKey())
                .orElse(null);
    }
}
