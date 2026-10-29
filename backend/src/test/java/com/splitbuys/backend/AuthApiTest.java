package com.splitbuys.backend;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.splitbuys.backend.support.ApiTest;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

class AuthApiTest extends ApiTest {
    @Autowired JwtEncoder encoder;

    @Test
    void signInIssuesATokenThatOpensProtectedEndpoints() throws Exception {
        Session s = signIn("Asha");
        get("/api/v1/me", s).andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(s.email()))
                .andExpect(jsonPath("$.name").value("Asha"));
    }

    @Test
    void newUserFlagIsTrueOnlyOnFirstSignIn() throws Exception {
        String email = uniqueEmail();
        for (boolean expectNew : new boolean[] {true, false}) {
            // verifying consumes the code, so asking again straight after is not held up by the resend gap
            post("/api/v1/auth/request-otp", null, "{\"email\":\"" + email + "\"}").andExpect(status().isOk());
            post("/api/v1/auth/verify-otp", null, "{\"email\":\"" + email + "\",\"code\":\"" + mail.codes.get(email) + "\"}")
                    .andExpect(jsonPath("$.newUser").value(expectNew));
        }
    }

    @Test
    void requestingTwiceInARowIsRateLimited() throws Exception {
        String email = uniqueEmail();
        post("/api/v1/auth/request-otp", null, "{\"email\":\"" + email + "\"}").andExpect(status().isOk());
        post("/api/v1/auth/request-otp", null, "{\"email\":\"" + email + "\"}")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"));
    }

    @Test
    void wrongCodesAreRejectedAndEventuallyLockTheCode() throws Exception {
        String email = uniqueEmail();
        post("/api/v1/auth/request-otp", null, "{\"email\":\"" + email + "\"}").andExpect(status().isOk());
        String wrong = "{\"email\":\"" + email + "\",\"code\":\"000000\"}";
        if (wrong.contains(mail.codes.get(email))) {
            wrong = wrong.replace("000000", "000001");
        }
        for (int i = 0; i < 5; i++) {
            post("/api/v1/auth/verify-otp", null, wrong).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.detail").value("Incorrect code"));
        }
        post("/api/v1/auth/verify-otp", null,
                "{\"email\":\"" + email + "\",\"code\":\"" + mail.codes.get(email) + "\"}")
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void emailFailureReturns502AndLetsTheUserRetryImmediately() throws Exception {
        String email = uniqueEmail();
        mail.failNext.set(true);
        post("/api/v1/auth/request-otp", null, "{\"email\":\"" + email + "\"}")
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("EMAIL_DELIVERY_FAILED"));
        assertNull(mail.codes.get(email));
        post("/api/v1/auth/request-otp", null, "{\"email\":\"" + email + "\"}").andExpect(status().isOk());
        assertNotNull(mail.codes.get(email));
    }

    @Test
    void malformedEmailIsRejected() throws Exception {
        post("/api/v1/auth/request-otp", null, "{\"email\":\"not-an-email\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Enter a valid email address"));
    }

    @Test
    void protectedEndpointsRequireAToken() throws Exception {
        get("/api/v1/groups", null).andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.detail").value("Sign in required"));
    }

    @Test
    void tamperedAndExpiredTokensAreRejected() throws Exception {
        Session s = signIn("Mallory");
        Session forged = new Session(s.email(), s.userId(), s.token().substring(0, s.token().length() - 4) + "AAAA");
        get("/api/v1/me", forged).andExpect(status().isUnauthorized());

        Instant past = Instant.now().minusSeconds(3600);
        JwtClaimsSet claims = JwtClaimsSet.builder().subject(String.valueOf(s.userId()))
                .issuedAt(past.minusSeconds(60)).expiresAt(past).build();
        String expired = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        get("/api/v1/me", new Session(s.email(), s.userId(), expired)).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Session expired, sign in again"));
    }

    @Test
    void errorsCarryARequestIdAndTheLegacyErrorField() throws Exception {
        get("/api/v1/me", null).andExpect(header().exists("X-Request-Id"))
                .andExpect(jsonPath("$.requestId").exists())
                .andExpect(jsonPath("$.error").value("Sign in required"));
    }

    @Test
    void healthIsPublic() throws Exception {
        get("/actuator/health", null).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
    }
}
