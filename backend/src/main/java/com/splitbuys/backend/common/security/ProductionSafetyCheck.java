package com.splitbuys.backend.common.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Under the {@code prod} profile the app refuses to start with development-only settings. */
@Component
@Profile("prod")
class ProductionSafetyCheck {
    ProductionSafetyCheck(@Value("${app.mail.enabled}") boolean mailEnabled,
                          @Value("${app.cors.allowed-origins}") String origins) {
        if (!mailEnabled) {
            throw new IllegalStateException("prod requires MAIL_ENABLED=true (sign-in codes must not go to the log)");
        }
        if (origins.contains("*")) {
            throw new IllegalStateException("prod requires CORS_ALLOWED_ORIGINS to list the real web origin(s), not *");
        }
    }
}
