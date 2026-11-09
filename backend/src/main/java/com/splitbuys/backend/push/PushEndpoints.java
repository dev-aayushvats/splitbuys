package com.splitbuys.backend.push;

import com.splitbuys.backend.common.error.InvalidRequestException;
import java.net.URI;
import java.util.List;

/**
 * The server will POST to whatever endpoint a client registers, so an unchecked endpoint would let any user make this
 * server call internal addresses (SSRF). Only the real push services are accepted: https, default port, no credentials,
 * and a host that is, or is a subdomain of, a known push service.
 */
final class PushEndpoints {
    private PushEndpoints() {
    }

    private static final List<String> SERVICES = List.of(
            "fcm.googleapis.com",                // Chrome, Edge, Brave, Samsung Internet, Opera (Android and desktop)
            "push.apple.com",                    // Safari and iOS home-screen apps (web.push.apple.com)
            "push.services.mozilla.com",         // Firefox
            "notify.windows.com");               // legacy Edge / Windows

    static final int MAX_LENGTH = 2048;

    /** @return the endpoint unchanged, or throws */
    static String require(String endpoint) {
        if (endpoint == null || endpoint.length() > MAX_LENGTH) {
            throw rejected();
        }
        URI uri;
        try {
            uri = new URI(endpoint);
        } catch (Exception e) {
            throw rejected();
        }
        String host = uri.getHost();
        if (!"https".equals(uri.getScheme()) || host == null || uri.getUserInfo() != null || uri.getFragment() != null
                || (uri.getPort() != -1 && uri.getPort() != 443)) {
            throw rejected();
        }
        String h = host.toLowerCase(java.util.Locale.ROOT);
        if (SERVICES.stream().noneMatch(s -> h.equals(s) || h.endsWith("." + s))) {
            throw rejected();
        }
        return endpoint;
    }

    private static InvalidRequestException rejected() {
        return new InvalidRequestException("Not a supported push notification service");
    }
}
