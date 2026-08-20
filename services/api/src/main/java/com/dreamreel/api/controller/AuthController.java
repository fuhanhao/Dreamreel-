package com.dreamreel.api.controller;

import com.dreamreel.api.common.ApiResponse;
import com.dreamreel.api.dto.AuthResponse;
import com.dreamreel.api.dto.CreationStatsResponse;
import com.dreamreel.api.dto.LoginRequest;
import com.dreamreel.api.dto.RegisterRequest;
import com.dreamreel.api.dto.UpdateArkKeyRequest;
import com.dreamreel.api.dto.UpdateTokenfreeKeyRequest;
import com.dreamreel.api.dto.UserResponse;
import com.dreamreel.api.service.AuthService;
import com.dreamreel.api.service.CreationStatsService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final CreationStatsService creationStatsService;

    public AuthController(AuthService authService, CreationStatsService creationStatsService) {
        this.authService = authService;
        this.creationStatsService = creationStatsService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.ok(authService.register(request));
    }

    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(authService.login(request));
    }

    @GetMapping("/me")
    public ApiResponse<UserResponse> me() {
        return ApiResponse.ok(authService.me());
    }

    @GetMapping("/me/creation-stats")
    public ApiResponse<CreationStatsResponse> creationStats() {
        return ApiResponse.ok(creationStatsService.todayStats());
    }

    @PutMapping("/me/tokenfree-key")
    public ApiResponse<UserResponse> updateTokenfreeKey(@Valid @RequestBody UpdateTokenfreeKeyRequest request) {
        return ApiResponse.ok(authService.updateTokenfreeKey(request));
    }

    @PutMapping("/me/ark-key")
    public ApiResponse<UserResponse> updateArkKey(@Valid @RequestBody UpdateArkKeyRequest request) {
        return ApiResponse.ok(authService.updateArkKey(request));
    }
}
