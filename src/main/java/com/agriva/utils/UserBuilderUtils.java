package com.agriva.utils;

import com.agriva.model.dto.MasterRegisterRequest;
import com.agriva.model.dto.SubUserRegisterRequest;
import com.agriva.model.entity.User;
import com.agriva.repository.AuthRepository;
import com.agriva.security.RoleConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class UserBuilderUtils {

    private final AuthRepository authRepository;

    public User createMasterUser(MasterRegisterRequest request, UUID farmId, PasswordEncoder passwordEncoder) {
        User user = User.builder()
                .email(request.getEmail())
                .username(normalize(request.getUsername()))
                .password(passwordEncoder.encode(request.getPassword()))
                .role(RoleConstants.MASTER)
                .farmId(farmId)
                .build();
        return authRepository.save(user);
    }

    public User createSubUser(SubUserRegisterRequest request, PasswordEncoder passwordEncoder, String role) {
        User user = User.builder()
                .email(request.getEmail())
                .username(normalize(request.getUsername()))
                .password(passwordEncoder.encode(request.getPassword()))
                .role(role)
                .farmId(request.getFarmId())
                .build();
        return authRepository.save(user);
    }

    private String normalize(String username) {
        if (username == null) return null;
        String trimmed = username.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}