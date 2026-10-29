package com.splitbuys.backend.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
class OtpCleanupJob {
    private static final Logger log = LoggerFactory.getLogger(OtpCleanupJob.class);

    private final OtpService otp;

    OtpCleanupJob(OtpService otp) {
        this.otp = otp;
    }

    @Scheduled(fixedDelayString = "PT1H", initialDelayString = "PT1M")
    void purge() {
        int removed = otp.purgeExpired();
        if (removed > 0) {
            log.info("Removed {} expired sign-in codes", removed);
        }
    }
}
