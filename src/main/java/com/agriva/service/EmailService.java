package com.agriva.service;

public interface EmailService {

    /**
     * Sends a password reset email containing the reset token / link.
     *
     * @param to    recipient email
     * @param token opaque reset token
     */
    void sendPasswordResetEmail(String to, String token);
}