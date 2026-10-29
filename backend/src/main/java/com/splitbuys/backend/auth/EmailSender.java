package com.splitbuys.backend.auth;

public interface EmailSender {
    void sendOtp(String email, String code);
}
