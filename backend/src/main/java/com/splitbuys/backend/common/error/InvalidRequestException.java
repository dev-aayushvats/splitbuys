package com.splitbuys.backend.common.error;

/** The request is well-formed but breaks a business rule (bad split, wrong member, ...). */
public class InvalidRequestException extends DomainException {
    public InvalidRequestException(String message) {
        super("INVALID_REQUEST", message);
    }
}
