package com.agriva.identity.service.impl;

import com.agriva.shared.exception.InvalidTokenException;
import com.agriva.identity.models.entity.User;
import com.agriva.identity.repository.AuthRepository;
import com.agriva.notification.EmailService;
import com.agriva.identity.service.PasswordResetService;
import com.agriva.identity.utils.PasswordValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PasswordResetServiceImpl implements PasswordResetService {

    private static final Duration TOKEN_TTL = Duration.ofMinutes(15);
    private static final String KEY_PREFIX = "agriva:pwd-reset:";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int TOKEN_BYTES = 48;

    private final AuthRepository authRepository;
    private final StringRedisTemplate redisTemplate;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final PasswordValidator passwordValidator;

    @Override
    public String initiateReset(String email) {
        Optional<User> userOpt = authRepository.findByEmail(email);

        if (userOpt.isEmpty()) {
            log.info("Password reset requested for unknown email: {}", email);
            return null;
        }

        User user = userOpt.get();
        String token = generateToken();
        String key = KEY_PREFIX + token;

        redisTemplate.opsForValue().set(key, user.getId().toString(), TOKEN_TTL);

        log.info("Password reset token generated for userId={} email={}",
                user.getId(), user.getEmail());

        emailService.sendPasswordResetEmail(user.getEmail(), token);
        return token;
    }

    @Override
    @Transactional
    public void resetPassword(String token, String newPassword) {
        String key = KEY_PREFIX + token;
        String userIdStr = redisTemplate.opsForValue().get(key);

        if (userIdStr == null) {
            log.warn("Invalid or expired password reset token used");
            throw new InvalidTokenException("Reset token is invalid or has expired");
        }

        UUID userId;
        try {
            userId = UUID.fromString(userIdStr);
        } catch (IllegalArgumentException e) {
            redisTemplate.delete(key);
            throw new InvalidTokenException("Reset token is invalid or has expired");
        }

        User user = authRepository.findById(userId)
                .orElseThrow(() -> new InvalidTokenException("Reset token is invalid or has expired"));

        passwordValidator.validatePasswordStrength(newPassword);

        if (passwordEncoder.matches(newPassword, user.getPassword())) {
            throw new InvalidTokenException("New password must be different from the current one");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        authRepository.save(user);
        redisTemplate.delete(key);

        log.info("Password reset successful for userId={} email={}",
                user.getId(), user.getEmail());
    }

    private String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}