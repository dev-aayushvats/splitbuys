package com.splitbuys.backend.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Dev stand-in used when MAIL_ENABLED is not true: prints the code instead of emailing it. Never active in prod. */
@Component
@Profile("!prod")
@ConditionalOnProperty(name = "app.mail.enabled", havingValue = "false", matchIfMissing = true)
class LoggingEmailSender implements EmailSender {
    private static final Logger log = LoggerFactory.getLogger(LoggingEmailSender.class);

    @Override
    public void sendOtp(String email, String code) {
        log.info("OTP for {}: {}", email, code);
    }
}
