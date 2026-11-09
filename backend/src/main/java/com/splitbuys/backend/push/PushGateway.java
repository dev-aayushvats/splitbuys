package com.splitbuys.backend.push;

/** Sends one encrypted message to one device. A seam so tests can capture messages instead of calling the internet. */
public interface PushGateway {
    enum Outcome {
        SENT,
        /** The push service says this subscription is permanently dead (uninstalled, permission revoked): forget it. */
        GONE,
        /** Anything else (network, throttling, server error). Notifications are best effort, so no retry. */
        FAILED
    }

    Outcome send(String endpoint, String p256dh, String authSecret, byte[] payload);
}
