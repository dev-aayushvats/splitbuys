package com.splitbuys.backend.auth;

/** Port for delivering sign-in codes. Implementations must throw a {@code DependencyFailureException} on failure. */
public interface EmailSender {
    void sendOtp(String email, String code);
}
