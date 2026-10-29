package com.splitbuys.backend.common.error;

/** The thing asked for does not exist, or the caller is not allowed to know that it does. */
public class NotFoundException extends DomainException {
    public NotFoundException(String message) {
        super("NOT_FOUND", message);
    }
}
