package com.splitbuys.backend.auth;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Minimal HS256 JWT: just a subject (user id) and an expiry. */
@Service
public class JwtService {
    private static final Base64.Encoder ENC = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DEC = Base64.getUrlDecoder();
    private static final String HEADER = ENC.encodeToString("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
    private static final Pattern SUB = Pattern.compile("\"sub\":\"(\\d+)\"");
    private static final Pattern EXP = Pattern.compile("\"exp\":(\\d+)");

    private final byte[] secret;
    private final long ttlSeconds;

    public JwtService(@Value("${app.jwt.secret}") String secret, @Value("${app.jwt.ttl-days}") long ttlDays) {
        if (secret.length() < 32) {
            throw new IllegalStateException("APP_JWT_SECRET must be at least 32 characters");
        }
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.ttlSeconds = ttlDays * 24 * 3600;
    }

    public String issue(long userId) {
        String payload = "{\"sub\":\"" + userId + "\",\"exp\":" + (Instant.now().getEpochSecond() + ttlSeconds) + "}";
        String unsigned = HEADER + "." + ENC.encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        return unsigned + "." + ENC.encodeToString(sign(unsigned));
    }

    /** @return the user id, or empty if the token is malformed, tampered with or expired */
    public Optional<Long> verify(String token) {
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            return Optional.empty();
        }
        try {
            byte[] expected = sign(parts[0] + "." + parts[1]);
            if (!MessageDigest.isEqual(expected, DEC.decode(parts[2]))) {
                return Optional.empty();
            }
            String payload = new String(DEC.decode(parts[1]), StandardCharsets.UTF_8);
            Matcher sub = SUB.matcher(payload);
            Matcher exp = EXP.matcher(payload);
            if (!sub.find() || !exp.find() || Long.parseLong(exp.group(1)) < Instant.now().getEpochSecond()) {
                return Optional.empty();
            }
            return Optional.of(Long.parseLong(sub.group(1)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private byte[] sign(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
