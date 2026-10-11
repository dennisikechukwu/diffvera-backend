package me.dennislab.diffvera.common.error;

import org.springframework.http.HttpStatus;

/**
 * Thrown when a requested resource (repository, pull request, rule, review) is not found.
 */
public class ResourceNotFoundException extends DiffveraException {

    public ResourceNotFoundException(String detail) {
        super(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Resource Not Found", detail);
    }

    public ResourceNotFoundException(String resourceType, Object identifier) {
        super(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Resource Not Found",
            String.format("%s with identifier '%s' was not found.", resourceType, identifier));
    }
}
