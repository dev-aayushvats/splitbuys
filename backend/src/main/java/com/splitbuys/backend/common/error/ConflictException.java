package com.splitbuys.backend.common.error;

/** The request is valid but not allowed in the current state (e.g. leaving with an unsettled balance). */
public class ConflictException extends DomainException {
    public ConflictException(String code, String message) {
        super(code, message);
    }
}
