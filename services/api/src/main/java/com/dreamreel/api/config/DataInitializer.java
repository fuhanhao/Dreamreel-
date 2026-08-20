package com.dreamreel.api.config;

import com.dreamreel.api.config.AdminProperties;
import com.dreamreel.api.domain.User;
import com.dreamreel.api.domain.UserRole;
import com.dreamreel.api.domain.UserStatus;
import com.dreamreel.api.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final UserRepository userRepository;
    private final AdminProperties adminProperties;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(
            UserRepository userRepository,
            AdminProperties adminProperties,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.adminProperties = adminProperties;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        if (!adminProperties.autoCreate()) {
            return;
        }
        if (userRepository.existsByEmailIgnoreCase(adminProperties.email())) {
            return;
        }

        var admin = new User();
        admin.setEmail(adminProperties.email().toLowerCase());
        admin.setPasswordHash(passwordEncoder.encode(adminProperties.password()));
        admin.setDisplayName(adminProperties.displayName());
        admin.setRole(UserRole.ADMIN);
        admin.setStatus(UserStatus.ACTIVE);
        userRepository.save(admin);
        log.info("已创建默认管理员账号: {}", adminProperties.email());
    }
}
