package com.splitbuys.backend.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/** Issues access tokens. Validation is done by Spring Security's resource server, not here. */
@Service
class TokenService {
    private static final String ISSUER = "splitbuys";

    private final JwtEncoder encoder;
    private final Clock clock;
    private final Duration ttl;

    TokenService(JwtEncoder encoder, Clock clock, @Value("${app.jwt.ttl-days}") long ttlDays) {
        this.encoder = encoder;
        this.clock = clock;
        this.ttl = Duration.ofDays(ttlDays);
    }

    String issue(long userId) {
        Instant now = clock.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .subject(String.valueOf(userId))
                .issuedAt(now)
                .expiresAt(now.plus(ttl))
                .build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }
}
