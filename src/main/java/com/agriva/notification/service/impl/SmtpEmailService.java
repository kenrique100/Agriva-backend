package com.agriva.notification.service.impl;

import com.agriva.notification.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Real SMTP delivery.
 *
 * Actives ONLY when {@code agriva.mail.enabled=true}. When false (the
 * default), {@link LoggingEmailService} handles password-reset mails by
 * writing them to the log — this is what lets the app boot in Docker
 * without an SMTP server.
 *
 * To enable in production, set on the runtime environment:
 *   AGRIVA_MAIL_ENABLED=true
 *   SPRING_MAIL_HOST=smtp.yourhost.com
 *   SPRING_MAIL_PORT=587
 *   SPRING_MAIL_USERNAME=...
 *   SPRING_MAIL_PASSWORD=...
 *   SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH=true
 *   SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE=true
 *   AGRIVA_MAIL_FROM=no-reply@yourhost.com
 */
@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "agriva.mail.enabled", havingValue = "true")
public class SmtpEmailService implements EmailService {

    private final JavaMailSender mailSender;

    @Value("${agriva.app.frontend-url:http://localhost:3000}")
    private String frontendUrl;

    @Value("${agriva.mail.from:no-reply@agriva.local}")
    private String from;

    @Override
    public void sendPasswordResetEmail(String to, String token) {
        String resetLink = frontendUrl + "/reset-password?token=" + token;

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject("Agriva Password Reset");
        message.setText("Use the link below to reset your password:\n\n"
                + resetLink
                + "\n\nThis link expires in 15 minutes.");

        try {
            mailSender.send(message);
            log.info("Password reset email sent to {}", to);
        } catch (Exception e) {
            log.error("Failed to send password reset email to {}: {}", to, e.getMessage(), e);
        }
    }
}