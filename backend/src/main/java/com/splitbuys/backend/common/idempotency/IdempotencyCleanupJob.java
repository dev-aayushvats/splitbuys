package com.splitbuys.backend.common.idempotency;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
class IdempotencyCleanupJob {
    private static final Logger log = LoggerFactory.getLogger(IdempotencyCleanupJob.class);

    private final IdempotencyService idempotency;

    IdempotencyCleanupJob(IdempotencyService idempotency) {
        this.idempotency = idempotency;
    }

    @Scheduled(fixedDelayString = "PT1H", initialDelayString = "PT2M")
    void purge() {
        int removed = idempotency.purgeOld();
        if (removed > 0) {
            log.info("Removed {} expired idempotency records", removed);
        }
    }
}
