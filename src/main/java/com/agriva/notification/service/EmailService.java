package com.agriva.notification.service;

public interface EmailService {

    void sendPasswordResetEmail(String to, String token);
}