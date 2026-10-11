package me.dennislab.diffvera.common.error;

import org.springframework.http.HttpStatus;

/**
 * Thrown when a request conflicts with current server state (e.g. duplicate review in progress).
 */
public class ConflictException extends DiffveraException {

    public ConflictException(String detail) {
        super(HttpStatus.CONFLICT, "STATE_CONFLICT", "Conflict", detail);
    }

    public ConflictException(String errorCode, String detail) {
        super(HttpStatus.CONFLICT, errorCode, "Conflict", detail);
    }
}
