package com.splitbuys.backend.common.error;

/** Credentials are missing, wrong or expired. */
public class UnauthorizedException extends DomainException {
    public UnauthorizedException(String message) {
        super("UNAUTHORIZED", message);
    }
}
