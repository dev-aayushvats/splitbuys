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
 * Runs against a real embedded Tomcat (MockMvc has no servlet container, so it cannot exercise forwarded-header handling).
 * The test client plays the part of the reverse proxy: it connects from 127.0.0.1, a trusted proxy, and sends the header
 * the way nginx would after appending the real client's address.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "APP_ENV_FILE=target/no-such-local.env",
        "spring.datasource.url=jdbc:sqlite:target/it-clientaddr.db?foreign_keys=on",
        "app.jwt.secret=integration-test-secret-integration-test-secret",
        "app.rate-limit.auth-per-minute=3"})
class ClientAddressApiTest {
    @LocalServerPort int port;
    private final HttpClient http = HttpClient.newHttpClient();

    private int verifyOtp(String forwardedFor) throws Exception {
        HttpRequest.Builder req = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/auth/verify-otp"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"email\":\"a@b.co\",\"code\":\"1\"}"));
        if (forwardedFor != null) {
            req.header("X-Forwarded-For", forwardedFor);
        }
        return http.send(req.build(), HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    private List<Integer> burst(int count, java.util.function.IntFunction<String> header) throws Exception {
        List<Integer> statuses = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            statuses.add(verifyOtp(header.apply(i)));
        }
        return statuses;
    }

    @Test
    void aSpoofedForwardedForPrefixCannotEscapeTheLimit() throws Exception {
        // the attacker varies what they put in the header; the proxy appends the one address it really saw
        List<Integer> statuses = burst(6, i -> "10.9." + i + ".1, 203.0.113.50");
        assertEquals(List.of(400, 400, 400, 429, 429, 429), statuses, "one real client, one bucket, however it decorates the header");
    }

    @Test
    void differentRealClientsBehindTheProxyAreCountedSeparately() throws Exception {
        List<Integer> statuses = burst(6, i -> "203.0.113." + (100 + i));
        assertEquals(List.of(400, 400, 400, 400, 400, 400), statuses, "six distinct clients, none over the limit");
    }
}
