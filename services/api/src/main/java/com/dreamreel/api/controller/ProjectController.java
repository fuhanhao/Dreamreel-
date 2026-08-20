package com.dreamreel.api.controller;

import com.dreamreel.api.common.ApiResponse;
import com.dreamreel.api.dto.CreateProjectRequest;
import com.dreamreel.api.dto.ProjectResponse;
import com.dreamreel.api.dto.UpdateCanvasRequest;
import com.dreamreel.api.dto.UpdateProjectRequest;
import com.dreamreel.api.service.ProjectService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/projects")
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @GetMapping
    public ApiResponse<List<ProjectResponse>> list() {
        return ApiResponse.ok(projectService.list());
    }

    @GetMapping("/{id}")
    public ApiResponse<ProjectResponse> get(
            @PathVariable UUID id,
            @RequestParam(required = false, defaultValue = "false") boolean summary) {
        return ApiResponse.ok(summary ? projectService.getSummaryById(id) : projectService.getById(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ProjectResponse> create(@Valid @RequestBody CreateProjectRequest request) {
        return ApiResponse.ok(projectService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<ProjectResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateProjectRequest request) {
        return ApiResponse.ok(projectService.update(id, request));
    }

    @PatchMapping("/{id}/canvas")
    public ApiResponse<ProjectResponse> updateCanvas(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateCanvasRequest request) {
        return ApiResponse.ok(projectService.updateCanvas(id, request));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        projectService.delete(id);
    }
}
