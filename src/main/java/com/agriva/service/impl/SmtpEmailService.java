package com.agriva.service.impl;

import com.agriva.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@Profile("!dev & !test")
@RequiredArgsConstructor
@Slf4j
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

        mailSender.send(message);
        log.info("Password reset email sent to {}", to);
    }
}
