package me.dennislab.diffvera.common.error;

import org.springframework.http.HttpStatus;

/**
 * Thrown when an unauthenticated or invalid session attempts a protected operation.
 */
public class UnauthorizedException extends DiffveraException {

    public UnauthorizedException(String detail) {
        super(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authentication Required", detail);
    }

    public UnauthorizedException() {
        this("Authentication is required to access this resource. Please sign in with GitHub.");
    }
}
