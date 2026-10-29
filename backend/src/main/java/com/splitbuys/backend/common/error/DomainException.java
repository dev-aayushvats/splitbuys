package com.splitbuys.backend.common.error;

/**
 * Base for every error the application raises on purpose. It carries a stable machine-readable {@code code} and a
 * message that is safe to show to users. Mapping to HTTP is the web layer's job, not the domain's.
 */
public abstract class DomainException extends RuntimeException {
    private final String code;

    protected DomainException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
