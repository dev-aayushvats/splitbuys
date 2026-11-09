package com.splitbuys.backend.push;

import com.splitbuys.backend.common.error.InvalidRequestException;
import com.splitbuys.backend.common.error.NotFoundException;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Who wants notifications, on which devices. Sending lives in {@link PushNotifier}. */
@Service
@Transactional
public class PushService {
    private static final int MAX_DEVICES_PER_USER = 10;

    private final PushSubscriptionRepository subscriptions;
    private final PushSettings settings;

    PushService(PushSubscriptionRepository subscriptions, PushSettings settings) {
        this.subscriptions = subscriptions;
        this.settings = settings;
    }

    public boolean enabled() {
        return settings.enabled();
    }

    String publicKey() {
        return settings.publicKey();
    }

    /**
     * Registers a device for a user. A device already registered (same endpoint) is moved to this user with fresh keys, so
     * signing in as someone else on the same phone cannot leave the previous person's notifications arriving there.
     */
    void subscribe(long userId, String endpoint, String p256dh, String authSecret) {
        if (!settings.enabled()) {
            throw new NotFoundException("Notifications are not enabled on this server");
        }
        PushEndpoints.require(endpoint);
        requireLength(p256dh, 65, "p256dh");
        requireLength(authSecret, 16, "auth");
        if (Base64.getUrlDecoder().decode(p256dh)[0] != 4) {
            throw new InvalidRequestException("Invalid notification key");
        }
        Optional<PushSubscription> existing = subscriptions.findByEndpoint(endpoint);
        if (existing.isPresent()) {
            existing.get().reassign(userId, p256dh, authSecret);
        } else {
            subscriptions.save(new PushSubscription(userId, endpoint, p256dh, authSecret));
        }
        subscriptions.flush();
        List<PushSubscription> mine = subscriptions.findByUserIdOrderByCreatedAtAscIdAsc(userId);
        for (int i = 0; i < mine.size() - MAX_DEVICES_PER_USER; i++) {
            subscriptions.delete(mine.get(i)); // oldest first: a user cannot make the table grow without limit
        }
    }

    /** Only your own devices: another user's endpoint is left alone. Removing something that is not there is fine. */
    void unsubscribe(long userId, String endpoint) {
        subscriptions.deleteByUserIdAndEndpoint(userId, endpoint);
    }

    /** Used when an account is deleted. */
    public void forgetUser(long userId) {
        subscriptions.deleteByUserId(userId);
    }

    List<PushSubscription> devicesOf(Collection<Long> userIds) {
        return userIds.isEmpty() ? List.of() : subscriptions.findByUserIdIn(userIds);
    }

    void drop(long subscriptionId) {
        subscriptions.deleteById(subscriptionId);
    }

    private static void requireLength(String base64url, int bytes, String what) {
        try {
            if (Base64.getUrlDecoder().decode(base64url).length == bytes) {
                return;
            }
        } catch (IllegalArgumentException ignored) {
            // falls through to the error below
        }
        throw new InvalidRequestException("Invalid notification key (" + what + ")");
    }
}
