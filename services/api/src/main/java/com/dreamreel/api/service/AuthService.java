package com.dreamreel.api.service;

import com.dreamreel.api.domain.User;
import com.dreamreel.api.domain.UserRole;
import com.dreamreel.api.domain.UserStatus;
import com.dreamreel.api.dto.AuthResponse;
import com.dreamreel.api.dto.LoginRequest;
import com.dreamreel.api.dto.RegisterRequest;
import com.dreamreel.api.dto.UpdateArkKeyRequest;
import com.dreamreel.api.dto.UpdateTokenfreeKeyRequest;
import com.dreamreel.api.dto.UserResponse;
import com.dreamreel.api.repository.UserRepository;
import com.dreamreel.api.security.CurrentUserService;
import com.dreamreel.api.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final CurrentUserService currentUserService;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            CurrentUserService currentUserService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.currentUserService = currentUserService;
    }

    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmailIgnoreCase(request.email())) {
            throw new IllegalStateException("该邮箱已注册");
        }

        var user = new User();
        user.setEmail(request.email().trim().toLowerCase());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setDisplayName(request.displayName().trim());
        user.setRole(UserRole.USER);
        user.setStatus(UserStatus.ACTIVE);
        userRepository.save(user);

        return buildAuthResponse(user);
    }

    public AuthResponse login(LoginRequest request) {
        var user = userRepository.findByEmailIgnoreCase(request.email().trim())
                .orElseThrow(() -> new IllegalStateException("邮箱或密码错误"));

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new IllegalStateException("账号已被禁用");
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new IllegalStateException("邮箱或密码错误");
        }

        return buildAuthResponse(user);
    }

    @Transactional(readOnly = true)
    public UserResponse me() {
        return UserResponse.from(currentUserService.requireUserEntity());
    }

    public UserResponse updateTokenfreeKey(UpdateTokenfreeKeyRequest request) {
        var user = currentUserService.requireUserEntity();
        var apiKey = request.apiKey();
        user.setTokenfreeApiKey(apiKey == null || apiKey.isBlank() ? null : apiKey.trim());
        return UserResponse.from(userRepository.save(user));
    }

    public UserResponse updateArkKey(UpdateArkKeyRequest request) {
        var user = currentUserService.requireUserEntity();
        var apiKey = request.apiKey();
        user.setArkApiKey(apiKey == null || apiKey.isBlank() ? null : apiKey.trim());
        return UserResponse.from(userRepository.save(user));
    }

    private AuthResponse buildAuthResponse(User user) {
        return new AuthResponse(jwtService.generateToken(user), UserResponse.from(user));
    }
}
