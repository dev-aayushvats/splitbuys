package com.splitbuys.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * With CLIENT_IP_HEADER=CF-Connecting-IP (the Cloudflare setup) the client is identified by that header and nothing a visitor
 * puts in X-Forwarded-For matters. The test connects from 127.0.0.1, where a Cloudflare Tunnel would run.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "APP_ENV_FILE=target/no-such-local.env",
        "spring.datasource.url=jdbc:sqlite:target/it-cloudflare.db?foreign_keys=on",
        "app.jwt.secret=integration-test-secret-integration-test-secret",
        "app.rate-limit.auth-per-minute=3",
        "CLIENT_IP_HEADER=CF-Connecting-IP"})
class CloudflareClientAddressApiTest {
    @LocalServerPort int port;
    private final HttpClient http = HttpClient.newHttpClient();

    private int verifyOtp(String cfIp, String forwardedFor) throws Exception {
        HttpRequest.Builder req = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/auth/verify-otp"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"email\":\"a@b.co\",\"code\":\"1\"}"));
        if (cfIp != null) {
            req.header("CF-Connecting-IP", cfIp);
        }
        if (forwardedFor != null) {
            req.header("X-Forwarded-For", forwardedFor);
        }
        return http.send(req.build(), HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    @Test
    void theVisitorIsIdentifiedByCfConnectingIpAndSpoofedForwardedForIsIgnored() throws Exception {
        List<Integer> statuses = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            statuses.add(verifyOtp("203.0.113.77", "10.9." + i + ".1"));
        }
        assertEquals(List.of(400, 400, 400, 429, 429, 429), statuses);
    }

    @Test
    void differentVisitorsAreCountedSeparately() throws Exception {
        List<Integer> statuses = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            statuses.add(verifyOtp("203.0.113." + (150 + i), "10.9.0.1"));
        }
        assertEquals(List.of(400, 400, 400, 400, 400, 400), statuses);
    }
}
