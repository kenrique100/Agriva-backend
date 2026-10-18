package com.agriva.service;

public interface EmailService {

    void sendPasswordResetEmail(String to, String token);
}