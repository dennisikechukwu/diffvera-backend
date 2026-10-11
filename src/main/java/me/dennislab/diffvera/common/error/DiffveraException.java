package me.dennislab.diffvera.common.error;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Base abstract exception for all domain-specific errors in Diffvera.
 */
@Getter
public abstract class DiffveraException extends RuntimeException {

    private final HttpStatus status;
    private final String errorCode;
    private final String title;

    protected DiffveraException(HttpStatus status, String errorCode, String title, String detail) {
        super(detail);
        this.status = status;
        this.errorCode = errorCode;
        this.title = title;
    }
}
