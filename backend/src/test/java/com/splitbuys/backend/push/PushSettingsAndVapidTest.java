package com.splitbuys.backend.push;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jwt.SignedJWT;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class PushSettingsAndVapidTest {
    /** A throwaway pair generated for tests only. */
    static final String PUBLIC = "BOE1mX0-1nR2Fpl2m_f6pERQ2Ilm1qzWGr451KPvE5Z5HErgrD5kiB8QYOUPxuXb_vjkao0NEue6OYgK8DezJ8U";
    static final String PRIVATE = "cc_grNCYzqYSmtw-XxYGNDyp_YcadiK798pfQkvDrXI";
    private static final Clock NOW = Clock.fixed(Instant.parse("2026-11-09T10:00:00Z"), ZoneOffset.UTC);

    @Test
    void offWhenNothingIsConfigured() {
        assertFalse(new PushSettings("", "", "", NOW).enabled());
    }

    @Test
    void failsFastOnAPartialOrBrokenConfiguration() {
        assertThrows(IllegalStateException.class, () -> new PushSettings(PUBLIC, "", "mailto:a@b.dev", NOW));
        assertThrows(IllegalStateException.class, () -> new PushSettings(PUBLIC, PRIVATE, "", NOW));
        assertThrows(IllegalStateException.class, () -> new PushSettings(PUBLIC, PRIVATE, "ops@b.dev", NOW)); // needs mailto: or https:
        assertThrows(IllegalStateException.class, () -> new PushSettings("not-a-key", PRIVATE, "mailto:a@b.dev", NOW));
        assertThrows(IllegalStateException.class, () -> new PushSettings(PUBLIC, "###", "mailto:a@b.dev", NOW));
    }

    @Test
    void theAuthorizationHeaderIsASignedVapidTokenForTheEndpointsOrigin() throws Exception {
        PushSettings settings = new PushSettings(PUBLIC, PRIVATE, "mailto:ops@splitbuys.dev", NOW);
        assertTrue(settings.enabled());

        String header = settings.vapid().authorization("https://fcm.googleapis.com/fcm/send/abc");

        assertTrue(header.startsWith("vapid t="), header);
        assertTrue(header.endsWith(", k=" + PUBLIC), header);
        SignedJWT jwt = SignedJWT.parse(header.substring("vapid t=".length(), header.indexOf(", k=")));
        assertTrue(jwt.verify(new ECDSAVerifier(WebPushCrypto.publicKey(java.util.Base64.getUrlDecoder().decode(PUBLIC)))), "signature must verify with the public key");
        assertEquals("ES256", jwt.getHeader().getAlgorithm().getName());
        assertEquals(java.util.List.of("https://fcm.googleapis.com"), jwt.getJWTClaimsSet().getAudience());
        assertEquals("mailto:ops@splitbuys.dev", jwt.getJWTClaimsSet().getSubject());
        long ttl = jwt.getJWTClaimsSet().getExpirationTime().getTime() - NOW.millis();
        assertTrue(ttl > 0 && ttl <= 24 * 3600 * 1000L, "expires within 24 hours, as the RFC requires");
    }
}
