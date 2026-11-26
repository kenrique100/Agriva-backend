package com.agriva.identity.service.impl;

import com.agriva.identity.models.entity.User;
import com.agriva.identity.repository.AuthRepository;
import com.agriva.shared.security.RoleConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminBootstrapService {

    private final AuthRepository authRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void createAdminIfAbsent(String email, String username, String rawPassword) {
        if (authRepository.findByEmail(email).isPresent()) {
            log.info("Admin already exists with email: {}", email);
            return;
        }
        User admin = User.builder()
                .email(email)
                .username(username)
                .password(passwordEncoder.encode(rawPassword))
                .role(RoleConstants.ADMIN)
                .farmId(null)
                .build();
        authRepository.save(admin);
        log.info("Default admin created with email: {}", email);
    }

    public long countByRole(String role) {
        return authRepository.findByRole(role).size();
    }
}