package com.agriva.notification;

public interface EmailService {

    void sendPasswordResetEmail(String to, String token);
}