package com.dreamreel.api.service;

import com.dreamreel.api.domain.Project;
import com.dreamreel.api.domain.ProjectType;
import com.dreamreel.api.dto.CreateProjectRequest;
import com.dreamreel.api.dto.ProjectResponse;
import com.dreamreel.api.dto.UpdateCanvasRequest;
import com.dreamreel.api.dto.UpdateProjectRequest;
import com.dreamreel.api.exception.ResourceNotFoundException;
import com.dreamreel.api.repository.ProjectRepository;
import com.dreamreel.api.security.CurrentUserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class ProjectService {

    private static final String DEFAULT_CANVAS = """
            {
              "nodes": [
                {"id":"text-1","type":"drama","position":{"x":80,"y":120},"data":{"label":"剧本","nodeType":"text","status":"idle"}},
                {"id":"script-1","type":"drama","position":{"x":360,"y":120},"data":{"label":"分镜脚本","nodeType":"script","status":"idle"}},
                {"id":"image-1","type":"drama","position":{"x":640,"y":80},"data":{"label":"分镜图","nodeType":"image","status":"idle"}},
                {"id":"video-1","type":"drama","position":{"x":640,"y":240},"data":{"label":"镜头视频","nodeType":"video","status":"idle"}},
                {"id":"compose-1","type":"drama","position":{"x":920,"y":160},"data":{"label":"合成导出","nodeType":"compose","status":"idle"}}
              ],
              "edges": [
                {"id":"e1","source":"text-1","target":"script-1","animated":true},
                {"id":"e2","source":"script-1","target":"image-1"},
                {"id":"e3","source":"script-1","target":"video-1"},
                {"id":"e4","source":"image-1","target":"video-1"},
                {"id":"e5","source":"video-1","target":"compose-1","animated":true}
              ]
            }
            """;

    private final ProjectRepository projectRepository;
    private final CurrentUserService currentUserService;

    public ProjectService(ProjectRepository projectRepository, CurrentUserService currentUserService) {
        this.projectRepository = projectRepository;
        this.currentUserService = currentUserService;
    }

    @Transactional(readOnly = true)
    public List<ProjectResponse> list() {
        var userId = currentUserService.requireUserId();
        return projectRepository.findByUserIdOrderByUpdatedAtDesc(userId).stream()
                .map(ProjectResponse::summary)
                .toList();
    }

    @Transactional(readOnly = true)
    public ProjectResponse getById(UUID id) {
        return ProjectResponse.from(findOwnedProject(id));
    }

    @Transactional(readOnly = true)
    public ProjectResponse getSummaryById(UUID id) {
        var userId = currentUserService.requireUserId();
        var principal = currentUserService.requirePrincipal();
        var summary = principal.isAdmin()
                ? projectRepository.findSummaryById(id)
                : projectRepository.findSummaryByIdAndUserId(id, userId);
        var view = summary.orElseThrow(() -> new ResourceNotFoundException("项目不存在: " + id));
        return new ProjectResponse(
                view.getId(),
                view.getName(),
                view.getType(),
                view.getDescription(),
                null,
                view.getCreatedAt(),
                view.getUpdatedAt());
    }

    public ProjectResponse create(CreateProjectRequest request) {
        var project = new Project();
        project.setUserId(currentUserService.requireUserId());
        project.setName(request.name());
        project.setType(request.type() != null ? request.type() : ProjectType.SHORT_DRAMA);
        project.setDescription(request.description());
        project.setCanvasData(DEFAULT_CANVAS);
        return ProjectResponse.from(projectRepository.save(project));
    }

    public ProjectResponse update(UUID id, UpdateProjectRequest request) {
        var project = findOwnedProject(id);
        if (request.name() != null) {
            project.setName(request.name());
        }
        if (request.type() != null) {
            project.setType(request.type());
        }
        if (request.description() != null) {
            project.setDescription(request.description());
        }
        return ProjectResponse.from(projectRepository.save(project));
    }

    public ProjectResponse updateCanvas(UUID id, UpdateCanvasRequest request) {
        var project = findOwnedProject(id);
        project.setCanvasData(request.canvasData());
        return ProjectResponse.from(projectRepository.save(project));
    }

    public void delete(UUID id) {
        var project = findOwnedProject(id);
        projectRepository.delete(project);
    }

    private Project findOwnedProject(UUID id) {
        var userId = currentUserService.requireUserId();
        var principal = currentUserService.requirePrincipal();
        if (principal.isAdmin()) {
            return projectRepository.findById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("项目不存在: " + id));
        }
        return projectRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("项目不存在: " + id));
    }
}
