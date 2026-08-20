package com.dreamreel.api.security;

import com.dreamreel.api.exception.ResourceNotFoundException;
import com.dreamreel.api.repository.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class CurrentUserService {

    private final UserRepository userRepository;

    public CurrentUserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public UserPrincipal requirePrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            throw new AccessDeniedException("请先登录");
        }
        return principal;
    }

    public UUID requireUserId() {
        return requirePrincipal().getId();
    }

    public void requireOwnerOrAdmin(UUID ownerId) {
        var principal = requirePrincipal();
        if (principal.isAdmin()) {
            return;
        }
        if (ownerId == null || !ownerId.equals(principal.getId())) {
            throw new AccessDeniedException("无权访问该资源");
        }
    }

    public void requireAdmin() {
        if (!requirePrincipal().isAdmin()) {
            throw new AccessDeniedException("需要管理员权限");
        }
    }

    public com.dreamreel.api.domain.User requireUserEntity() {
        var userId = requireUserId();
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("用户不存在"));
    }
}
