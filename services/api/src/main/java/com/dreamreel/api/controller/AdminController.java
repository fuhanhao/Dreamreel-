package com.dreamreel.api.controller;

import com.dreamreel.api.common.ApiResponse;
import com.dreamreel.api.domain.GenerationMediaType;
import com.dreamreel.api.dto.AdminStatsResponse;
import com.dreamreel.api.dto.GenerationJobResponse;
import com.dreamreel.api.dto.PageResponse;
import com.dreamreel.api.dto.ProjectResponse;
import com.dreamreel.api.dto.UpdateUserRequest;
import com.dreamreel.api.dto.UserResponse;
import com.dreamreel.api.service.AdminService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/stats")
    public ApiResponse<AdminStatsResponse> stats() {
        return ApiResponse.ok(adminService.stats());
    }

    @GetMapping("/users")
    public ApiResponse<PageResponse<UserResponse>> users(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(adminService.listUsers(page, size, keyword));
    }

    @PatchMapping("/users/{id}")
    public ApiResponse<UserResponse> updateUser(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateUserRequest request) {
        return ApiResponse.ok(adminService.updateUser(id, request));
    }

    @GetMapping("/projects")
    public ApiResponse<PageResponse<ProjectResponse>> projects(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(adminService.listProjects(page, size));
    }

    @GetMapping("/generations")
    public ApiResponse<PageResponse<GenerationJobResponse>> generations(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) GenerationMediaType mediaType) {
        return ApiResponse.ok(adminService.listGenerations(page, size, mediaType));
    }
}
