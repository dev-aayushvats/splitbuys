package com.splitbuys.backend.push;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** The real sender: encrypts the message for the device and POSTs it to the browser vendor's push service. */
@Component
class HttpPushGateway implements PushGateway {
    private static final Logger log = LoggerFactory.getLogger(HttpPushGateway.class);

    private final PushSettings settings;
    // redirects are never followed: an endpoint was checked against the allow-list, wherever it might redirect to was not
    private final HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).connectTimeout(Duration.ofSeconds(5)).build();

    HttpPushGateway(PushSettings settings) {
        this.settings = settings;
    }

    @Override
    public Outcome send(String endpoint, String p256dh, String authSecret, byte[] payload) {
        try {
            PushEndpoints.require(endpoint); // checked again at send time, not only when it was stored
            byte[] body = WebPushCrypto.encrypt(payload, Base64.getUrlDecoder().decode(p256dh), Base64.getUrlDecoder().decode(authSecret));
            HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint)).timeout(Duration.ofSeconds(10))
                    .header("Authorization", settings.vapid().authorization(endpoint))
                    .header("Content-Encoding", "aes128gcm")
                    .header("Content-Type", "application/octet-stream")
                    .header("TTL", "86400") // undelivered messages are dropped after a day: yesterday's expense is not news
                    .header("Urgency", "normal")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body)).build();
            int status = http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
            if (status >= 200 && status < 300) {
                return Outcome.SENT;
            }
            if (status == 404 || status == 410) {
                return Outcome.GONE;
            }
            log.warn("Push service answered {} for {}", status, URI.create(endpoint).getHost());
            return Outcome.FAILED;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Outcome.FAILED;
        } catch (Exception e) {
            log.warn("Push delivery failed: {}", e.toString());
            return Outcome.FAILED;
        }
    }
}
