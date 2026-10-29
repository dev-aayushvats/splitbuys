package com.splitbuys.backend.auth;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.Instant;

@Entity
class OtpCode {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String email;
    private String codeHash;
    private Instant createdAt;
    private Instant expiresAt;
    private int attempts;

    protected OtpCode() {
    }

    OtpCode(String email, String codeHash, Instant createdAt, Instant expiresAt) {
        this.email = email;
        this.codeHash = codeHash;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    String getCodeHash() {
        return codeHash;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    Instant getExpiresAt() {
        return expiresAt;
    }

    int getAttempts() {
        return attempts;
    }

    void recordFailedAttempt() {
        attempts++;
    }
}
