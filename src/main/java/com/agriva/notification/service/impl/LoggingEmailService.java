package com.agriva.notification.service.impl;

import com.agriva.notification.EmailService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Default EmailService — active whenever {@link SmtpEmailService} is not.
 * <p>
 * Writes password-reset links to the application log so that dev, test
 * and docker environments work end-to-end without an SMTP server.
 */
@Service
@Slf4j
@ConditionalOnProperty(
        name = "agriva.mail.enabled",
        havingValue = "false",
        matchIfMissing = true
)
public class LoggingEmailService implements EmailService {

    @Value("${agriva.app.frontend-url:http://localhost:3000}")
    private String frontendUrl;

    @Override
    public void sendPasswordResetEmail(String to, String token) {
        String resetLink = frontendUrl + "/reset-password?token=" + token;
        log.info("---------- PASSWORD RESET EMAIL ----------");
        log.info("To:          {}", to);
        log.info("Reset link:  {}", resetLink);
        log.info("Raw token:   {}", token);
        log.info("Expires in:  15 minutes");
        log.info("------------------------------------------");
    }
}