package com.agriva.identity.service;

public interface PasswordResetService {

    /**
     * Generates a one-time token, stores it in Redis, and emails it to the user.
     * Returns the raw token so callers can optionally expose it in dev.
     * Silently no-ops when the email does not exist (avoids account enumeration).
     */
    String initiateReset(String email);

    void resetPassword(String token, String newPassword);
}