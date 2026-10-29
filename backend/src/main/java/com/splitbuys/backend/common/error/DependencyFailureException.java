package com.splitbuys.backend.common.error;

/** A system we depend on (SMTP, ...) failed. */
public class DependencyFailureException extends DomainException {
    public DependencyFailureException(String code, String message, Throwable cause) {
        super(code, message);
        initCause(cause);
    }
}
