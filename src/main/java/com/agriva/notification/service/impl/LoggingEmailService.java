package com.agriva.notification.service.impl;

import com.agriva.notification.EmailService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@ConditionalOnProperty(
        name = "agriva.mail.enabled",
        havingValue = "false",
        matchIfMissing = true
)
public class LoggingEmailService implements EmailService {

    @Override
    public void sendPasswordResetEmail(String to, String token) {
        String masked = token == null
                ? "<null>"
                : token.substring(0, Math.min(6, token.length())) + "…";
        log.info("Password reset email for {} (token={})", to, masked);
    }
}