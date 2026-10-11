package me.dennislab.diffvera.common.error;

import org.springframework.http.HttpStatus;

/**
 * Thrown when an integrated third-party service (e.g. GitHub REST API, Google Gemini AI) fails or returns an error.
 */
public class ExternalApiException extends DiffveraException {

    public ExternalApiException(String detail) {
        this("External API", detail);
    }

    public ExternalApiException(String serviceName, String detail) {
        super(HttpStatus.BAD_GATEWAY, "EXTERNAL_SERVICE_ERROR", "External Service Error",
            String.format("%s communication failed: %s", serviceName, detail));
    }
}
