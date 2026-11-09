package com.splitbuys.backend.push;

import java.security.GeneralSecurityException;
import java.time.Clock;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * The server's VAPID identity, from VAPID_PUBLIC_KEY, VAPID_PRIVATE_KEY and VAPID_SUBJECT (a mailto: or https: contact).
 * With none of them set the feature is simply off; with some set but not all, or with unusable values, startup fails
 * rather than quietly running without notifications. Generate a pair with deploy/GenerateVapidKeys.java.
 */
@Component
class PushSettings {
    private final Vapid vapid;
    private final String publicKey;

    PushSettings(@Value("${app.push.vapid-public-key:}") String publicKey,
                 @Value("${app.push.vapid-private-key:}") String privateKey,
                 @Value("${app.push.vapid-subject:}") String subject, Clock clock) {
        boolean any = !publicKey.isBlank() || !privateKey.isBlank() || !subject.isBlank();
        boolean all = !publicKey.isBlank() && !privateKey.isBlank() && !subject.isBlank();
        if (any && !all) {
            throw new IllegalStateException("VAPID_PUBLIC_KEY, VAPID_PRIVATE_KEY and VAPID_SUBJECT must be set together");
        }
        if (!all) {
            this.vapid = null;
            this.publicKey = null;
            return;
        }
        if (!subject.startsWith("mailto:") && !subject.startsWith("https://")) {
            throw new IllegalStateException("VAPID_SUBJECT must start with mailto: or https://");
        }
        try {
            WebPushCrypto.publicKey(Base64.getUrlDecoder().decode(publicKey)); // proves it is a real point on the curve
            this.vapid = new Vapid(WebPushCrypto.privateKey(Base64.getUrlDecoder().decode(privateKey)), publicKey, subject, clock);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("VAPID keys are not valid base64url P-256 keys", e);
        }
        this.publicKey = publicKey;
    }

    boolean enabled() {
        return vapid != null;
    }

    String publicKey() {
        return publicKey;
    }

    Vapid vapid() {
        return vapid;
    }
}
