package com.agriva.service.impl;

import com.agriva.service.EmailService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile({"dev", "test"})
@Slf4j
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