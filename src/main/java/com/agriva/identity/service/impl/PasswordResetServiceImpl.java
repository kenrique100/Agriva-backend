package com.agriva.identity.service.impl;

import com.agriva.shared.exception.InvalidTokenException;
import com.agriva.identity.models.entity.User;
import com.agriva.identity.repository.AuthRepository;
import com.agriva.identity.service.RefreshTokenService;
import com.agriva.identity.service.TokenBlacklistService;
import com.agriva.notification.EmailService;
import com.agriva.identity.service.PasswordResetService;
import com.agriva.identity.utils.PasswordValidator;
import com.agriva.shared.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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
    private static final String KEY_PREFIX  = "agriva:pwd-reset:";
    private static final String REQ_PREFIX  = "agriva:pwd-reset-req:";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int TOKEN_BYTES = 48;

    /** Per-email rate limit to complement the per-IP limit in the filter. */
    @Value("${agriva.rate-limit.forgot-password-per-hour-per-email:3}")
    private int forgotPerHourPerEmail;

    private final AuthRepository authRepository;
    private final StringRedisTemplate redisTemplate;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final PasswordValidator passwordValidator;
    private final TokenBlacklistService tokenBlacklistService;
    private final RefreshTokenService refreshTokenService;
    private final JwtUtil jwtUtil;

    @Override
    public String initiateReset(String email) {
        String reqKey = REQ_PREFIX + email.toLowerCase();
        try {
            Long n = redisTemplate.opsForValue().increment(reqKey);
            if (n != null && n == 1L) redisTemplate.expire(reqKey, Duration.ofHours(1));
            if (n != null && n > forgotPerHourPerEmail) {
                log.warn("Forgot-password per-email throttle hit for {}", email);
                return null;
            }
        } catch (Exception e) {
            log.warn("Forgot-password throttle lookup failed: {}", e.getMessage());
        }

        Optional<User> userOpt = authRepository.findByEmailLive(email);
        if (userOpt.isEmpty()) {
            log.info("Password reset requested for unknown email: {}", email);
            return null;
        }

        User user = userOpt.get();
        String token = generateToken();
        redisTemplate.opsForValue().set(KEY_PREFIX + token, user.getId().toString(), TOKEN_TTL);

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

        User user = authRepository.findByIdLive(userId)
                .orElseThrow(() -> new InvalidTokenException(
                        "Reset token is invalid or has expired"));

        passwordValidator.validatePasswordStrength(newPassword);

        if (passwordEncoder.matches(newPassword, user.getPassword())) {
            throw new InvalidTokenException("New password must be different from the current one");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        authRepository.save(user);
        redisTemplate.delete(key);

        tokenBlacklistService.blacklistAllForUser(userId, jwtUtil.getAccessTtlSeconds());
        refreshTokenService.revokeAllForUser(userId);

        log.info("Password reset successful for userId={} email={}; all sessions revoked",
                user.getId(), user.getEmail());
    }

    private String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}