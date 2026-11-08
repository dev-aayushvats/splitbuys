package com.splitbuys.backend.common.security;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * A signed token proves who issued it, not that the account still exists, and the token lives for weeks. This rejects the
 * tokens of deleted accounts.
 *
 * Asking the database on every request would add a query to all of them, so a "still active" answer is remembered for a
 * short time. Closing an account drops its entry at once, after the transaction commits (earlier, a request could look the
 * account up again before the delete is visible and cache the stale answer). Another server instance would take up to the
 * cache lifetime to notice; there is one instance today, and the bound is explicit.
 */
@Component
class ActiveAccountValidator implements OAuth2TokenValidator<Jwt> {
    private static final int MAX_ENTRIES = 10_000;
    private static final OAuth2Error INVALID = new OAuth2Error("invalid_token", "The account no longer exists", null);

    private final ActiveAccounts accounts;
    private final long cacheNanos;
    /** user id -> System.nanoTime() until which "active" may be trusted */
    private final Map<Long, Long> trustedUntil = new ConcurrentHashMap<>();

    ActiveAccountValidator(ActiveAccounts accounts, @Value("${app.auth.active-cache-seconds:15}") long cacheSeconds) {
        this.accounts = accounts;
        this.cacheNanos = Duration.ofSeconds(cacheSeconds).toNanos();
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        long userId;
        try {
            userId = Long.parseLong(jwt.getSubject());
        } catch (NumberFormatException e) {
            return OAuth2TokenValidatorResult.failure(INVALID);
        }
        long now = System.nanoTime();
        Long until = trustedUntil.get(userId);
        if (until != null && now - until < 0) {
            return OAuth2TokenValidatorResult.success();
        }
        if (!accounts.isActive(userId)) {
            trustedUntil.remove(userId);
            return OAuth2TokenValidatorResult.failure(INVALID);
        }
        if (cacheNanos > 0) {
            if (trustedUntil.size() >= MAX_ENTRIES) {
                trustedUntil.clear();
            }
            trustedUntil.put(userId, now + cacheNanos);
        }
        return OAuth2TokenValidatorResult.success();
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onAccountClosed(AccountClosed event) {
        trustedUntil.remove(event.userId());
    }
}
