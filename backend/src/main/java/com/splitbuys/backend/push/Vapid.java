package com.splitbuys.backend.push;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.net.URI;
import java.security.interfaces.ECPrivateKey;
import java.time.Clock;
import java.util.Date;

/**
 * VAPID (RFC 8292): the server signs a short-lived token with its own key so push services know which application is
 * sending, and can contact the operator (the subject) if it misbehaves.
 */
final class Vapid {
    private static final long LIFETIME_SECONDS = 12 * 3600; // the limit is 24 hours

    private final ECPrivateKey privateKey;
    private final String publicKey; // base64url of the 65-byte point, as browsers were given it when subscribing
    private final String subject;
    private final Clock clock;

    Vapid(ECPrivateKey privateKey, String publicKey, String subject, Clock clock) {
        this.privateKey = privateKey;
        this.publicKey = publicKey;
        this.subject = subject;
        this.clock = clock;
    }

    /** The value of the Authorization header for a message to {@code endpoint}. */
    String authorization(String endpoint) {
        URI uri = URI.create(endpoint);
        String audience = uri.getScheme() + "://" + uri.getHost() + (uri.getPort() == -1 ? "" : ":" + uri.getPort());
        long now = clock.millis();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .audience(audience).subject(subject).expirationTime(new Date(now + LIFETIME_SECONDS * 1000)).build();
        try {
            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.ES256), claims);
            jwt.sign(new ECDSASigner(privateKey));
            return "vapid t=" + jwt.serialize() + ", k=" + publicKey;
        } catch (JOSEException e) {
            throw new IllegalStateException("could not sign the VAPID token", e);
        }
    }
}
