package com.dreamreel.api.service;

import com.dreamreel.api.domain.GenerationMediaType;
import com.dreamreel.api.domain.GenerationStatus;
import com.dreamreel.api.dto.AdminStatsResponse;
import com.dreamreel.api.dto.GenerationJobResponse;
import com.dreamreel.api.dto.PageResponse;
import com.dreamreel.api.dto.ProjectResponse;
import com.dreamreel.api.dto.UpdateUserRequest;
import com.dreamreel.api.dto.UserResponse;
import com.dreamreel.api.exception.ResourceNotFoundException;
import com.dreamreel.api.repository.GenerationJobRepository;
import com.dreamreel.api.repository.ProjectRepository;
import com.dreamreel.api.repository.UserRepository;
import com.dreamreel.api.security.CurrentUserService;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional
public class AdminService {

    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;
    private final GenerationJobRepository generationJobRepository;
    private final CurrentUserService currentUserService;

    public AdminService(
            UserRepository userRepository,
            ProjectRepository projectRepository,
            GenerationJobRepository generationJobRepository,
            CurrentUserService currentUserService) {
        this.userRepository = userRepository;
        this.projectRepository = projectRepository;
        this.generationJobRepository = generationJobRepository;
        this.currentUserService = currentUserService;
    }

    @Transactional(readOnly = true)
    public AdminStatsResponse stats() {
        currentUserService.requireAdmin();
        return new AdminStatsResponse(
                userRepository.count(),
                userRepository.countByStatus(com.dreamreel.api.domain.UserStatus.ACTIVE),
                projectRepository.count(),
                generationJobRepository.count(),
                generationJobRepository.countByStatus(GenerationStatus.COMPLETED),
                generationJobRepository.countByStatus(GenerationStatus.FAILED)
        );
    }

    @Transactional(readOnly = true)
    public PageResponse<UserResponse> listUsers(int page, int size, String keyword) {
        currentUserService.requireAdmin();
        var pageable = PageRequest.of(page, size);
        var result = keyword == null || keyword.isBlank()
                ? userRepository.findAll(pageable)
                : userRepository.findByEmailContainingIgnoreCaseOrDisplayNameContainingIgnoreCase(
                        keyword, keyword, pageable);
        return new PageResponse<>(
                result.map(UserResponse::from).getContent(),
                result.getTotalElements(),
                page,
                size
        );
    }

    public UserResponse updateUser(UUID id, UpdateUserRequest request) {
        currentUserService.requireAdmin();
        var user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("用户不存在: " + id));
        if (request.status() != null) {
            user.setStatus(request.status());
        }
        if (request.displayName() != null && !request.displayName().isBlank()) {
            user.setDisplayName(request.displayName().trim());
        }
        return UserResponse.from(userRepository.save(user));
    }

    @Transactional(readOnly = true)
    public PageResponse<ProjectResponse> listProjects(int page, int size) {
        currentUserService.requireAdmin();
        var result = projectRepository.findAllByOrderByUpdatedAtDesc(PageRequest.of(page, size));
        return new PageResponse<>(
                result.map(ProjectResponse::summary).getContent(),
                result.getTotalElements(),
                page,
                size
        );
    }

    @Transactional(readOnly = true)
    public PageResponse<GenerationJobResponse> listGenerations(int page, int size, GenerationMediaType mediaType) {
        currentUserService.requireAdmin();
        var pageable = PageRequest.of(page, size);
        var result = mediaType == null
                ? generationJobRepository.findAllByOrderByCreatedAtDesc(pageable)
                : generationJobRepository.findByMediaTypeOrderByCreatedAtDesc(mediaType, pageable);
        return new PageResponse<>(
                result.map(GenerationJobResponse::from).getContent(),
                result.getTotalElements(),
                page,
                size
        );
    }
}
