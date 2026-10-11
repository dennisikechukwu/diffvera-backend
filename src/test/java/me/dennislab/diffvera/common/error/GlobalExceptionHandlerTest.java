package me.dennislab.diffvera.common.error;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.*;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
        request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/test");
    }

    @Test
    @DisplayName("Should map ResourceNotFoundException to 404 with correct error details")
    void shouldHandleResourceNotFound() {
        ResourceNotFoundException ex = new ResourceNotFoundException("Repository", "acme/repo");
        ResponseEntity<ErrorResponse> response = handler.handleDiffveraException(ex, request);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(404, response.getBody().getStatus());
        assertEquals("Resource Not Found", response.getBody().getTitle());
        assertEquals("RESOURCE_NOT_FOUND", response.getBody().getErrorCode());
        assertEquals("Repository with identifier 'acme/repo' was not found.", response.getBody().getDetail());
        assertEquals("/api/v1/test", response.getBody().getInstance());
    }

    @Test
    @DisplayName("Should map UnauthorizedException to 401")
    void shouldHandleUnauthorized() {
        UnauthorizedException ex = new UnauthorizedException("Session token has expired.");
        ResponseEntity<ErrorResponse> response = handler.handleDiffveraException(ex, request);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(401, response.getBody().getStatus());
        assertEquals("UNAUTHORIZED", response.getBody().getErrorCode());
    }

    @Test
    @DisplayName("Should map ConflictException to 409")
    void shouldHandleConflict() {
        ConflictException ex = new ConflictException("A review is already in progress for this commit.");
        ResponseEntity<ErrorResponse> response = handler.handleDiffveraException(ex, request);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(409, response.getBody().getStatus());
        assertEquals("STATE_CONFLICT", response.getBody().getErrorCode());
    }

    @Test
    @DisplayName("Should map ExternalApiException to 502 Bad Gateway")
    void shouldHandleExternalApiException() {
        ExternalApiException ex = new ExternalApiException("GitHub API", "rate limit exceeded");
        ResponseEntity<ErrorResponse> response = handler.handleDiffveraException(ex, request);

        assertEquals(HttpStatus.BAD_GATEWAY, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(502, response.getBody().getStatus());
        assertEquals("EXTERNAL_SERVICE_ERROR", response.getBody().getErrorCode());
        assertTrue(response.getBody().getDetail().contains("GitHub API communication failed"));
    }

    @Test
    @DisplayName("Should map unhandled generic Exception to 500 without technical stack trace leak")
    void shouldHandleGenericException() {
        RuntimeException ex = new RuntimeException("Null pointer in database connection pool");
        ResponseEntity<ErrorResponse> response = handler.handleUnexpectedException(ex, request);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(500, response.getBody().getStatus());
        assertEquals("INTERNAL_SERVER_ERROR", response.getBody().getErrorCode());
        assertFalse(response.getBody().getDetail().contains("Null pointer"));
        assertTrue(response.getBody().getDetail().contains("An unexpected error occurred"));
    }
}
