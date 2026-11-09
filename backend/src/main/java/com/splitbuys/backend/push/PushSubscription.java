package com.splitbuys.backend.push;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** One browser or device that asked to receive notifications for a user. The endpoint is unique: a device belongs to one person. */
@Entity
@Table(name = "push_subscription")
class PushSubscription {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private long userId;

    @Column(nullable = false, length = 2048)
    private String endpoint;

    /** The device's public key, used to encrypt messages so that only it can read them. */
    @Column(nullable = false, length = 128)
    private String p256dh;

    @Column(name = "auth_secret", nullable = false, length = 64)
    private String authSecret;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    protected PushSubscription() {
    }

    PushSubscription(long userId, String endpoint, String p256dh, String authSecret) {
        this.userId = userId;
        this.endpoint = endpoint;
        this.p256dh = p256dh;
        this.authSecret = authSecret;
    }

    Long getId() { return id; }
    long getUserId() { return userId; }
    String getEndpoint() { return endpoint; }
    String getP256dh() { return p256dh; }
    String getAuthSecret() { return authSecret; }

    /** The same device, now signed in as someone else (or with fresh keys). */
    void reassign(long userId, String p256dh, String authSecret) {
        this.userId = userId;
        this.p256dh = p256dh;
        this.authSecret = authSecret;
        this.createdAt = Instant.now();
    }
}
