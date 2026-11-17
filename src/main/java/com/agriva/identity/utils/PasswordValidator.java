package com.agriva.identity.utils;

import com.agriva.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class PasswordValidator {

    private final HibpService hibpService;

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

        if (checkHibp && hibpService.isPwned(password)) {
            throw new BusinessException(
                    "This password has appeared in a known data breach. " +
                            "Please choose a different one.");
        }
    }
}