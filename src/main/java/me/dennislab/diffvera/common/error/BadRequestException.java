package me.dennislab.diffvera.common.error;

import org.springframework.http.HttpStatus;

/**
 * Thrown when incoming request data is malformed, invalid, or cannot be processed.
 */
public class BadRequestException extends DiffveraException {

    public BadRequestException(String detail) {
        super(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "Bad Request", detail);
    }

    public BadRequestException(String errorCode, String detail) {
        super(HttpStatus.BAD_REQUEST, errorCode, "Bad Request", detail);
    }
}
