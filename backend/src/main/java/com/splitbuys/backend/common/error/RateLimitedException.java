package com.splitbuys.backend.common.error;

public class RateLimitedException extends DomainException {
    public RateLimitedException(String message) {
        super("TOO_MANY_REQUESTS", message);
    }
}
