package com.agriva.identity.utils;

import com.agriva.shared.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

@Component
@Slf4j
public class PasswordValidator {

    private static final RestClient HIBP = RestClient.builder()
            .baseUrl("https://api.pwnedpasswords.com")
            .build();

    @Value("${agriva.security.password.min-length:12}")
    private int minLength;

    @Value("${agriva.security.password.check-hibp:true}")
    private boolean checkHibp;

    @Value("${agriva.security.password.require-symbol:true}")
    private boolean requireSymbol;

    public void validatePasswordStrength(String password) {
        if (password == null || password.length() < minLength) {
            throw new BusinessException(
                    "Password must be at least " + minLength + " characters long");
        }

        boolean hasUpper = false, hasLower = false, hasDigit = false, hasSymbol = false;
        for (char c : password.toCharArray()) {
            if (Character.isUpperCase(c))      hasUpper  = true;
            else if (Character.isLowerCase(c)) hasLower  = true;
            else if (Character.isDigit(c))     hasDigit  = true;
            else                               hasSymbol = true;
        }

        StringBuilder missing = new StringBuilder();
        if (!hasUpper)                    missing.append("uppercase, ");
        if (!hasLower)                    missing.append("lowercase, ");
        if (!hasDigit)                    missing.append("digit, ");
        if (requireSymbol && !hasSymbol)  missing.append("symbol, ");
        if (missing.length() > 0) {
            String list = missing.substring(0, missing.length() - 2);
            throw new BusinessException("Password must contain at least one " + list);
        }

        if (checkHibp && isPwned(password)) {
            throw new BusinessException(
                    "This password has appeared in a known data breach. Please choose a different one.");
        }
    }

    /** HIBP k-anonymity — only the first 5 chars of the SHA-1 leave the JVM. */
    private boolean isPwned(String password) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-1")
                    .digest(password.getBytes(StandardCharsets.UTF_8));
            String sha1   = HexFormat.of().formatHex(hash).toUpperCase();
            String prefix = sha1.substring(0, 5);
            String suffix = sha1.substring(5);

            String body = HIBP.get()
                    .uri("/range/{prefix}", prefix)
                    .retrieve()
                    .body(String.class);

            if (body == null) return false;
            return body.lines()
                    .map(l -> l.split(":", 2)[0])
                    .anyMatch(suffix::equals);
        } catch (Exception e) {
            log.warn("HIBP check skipped (fail-open): {}", e.getMessage());
            return false;
        }
    }
}