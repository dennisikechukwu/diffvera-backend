package me.dennislab.diffvera.common.error;

import org.springframework.http.HttpStatus;

/**
 * Thrown when an authenticated user does not have permission for the requested action.
 */
public class ForbiddenException extends DiffveraException {

    public ForbiddenException(String detail) {
        super(HttpStatus.FORBIDDEN, "FORBIDDEN", "Access Denied", detail);
    }
}
