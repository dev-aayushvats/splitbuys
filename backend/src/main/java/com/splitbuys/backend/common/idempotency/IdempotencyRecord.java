package com.splitbuys.backend.common.idempotency;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** What a create request produced, filed under (user, client-chosen key). */
@Entity
@Table(name = "idempotency_key")
class IdempotencyRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private long userId;

    @Column(nullable = false, length = 64)
    private String keyValue;

    /** Hash of what was asked for, to catch a key that is reused for a different request. */
    @Column(nullable = false, length = 64)
    private String fingerprint;

    /** The response body that was returned the first time. */
    @Column(nullable = false, length = 16384)
    private String response;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    protected IdempotencyRecord() {
    }

    IdempotencyRecord(long userId, String keyValue, String fingerprint, String response) {
        this.userId = userId;
        this.keyValue = keyValue;
        this.fingerprint = fingerprint;
        this.response = response;
    }

    String getFingerprint() {
        return fingerprint;
    }

    String getResponse() {
        return response;
    }
}
