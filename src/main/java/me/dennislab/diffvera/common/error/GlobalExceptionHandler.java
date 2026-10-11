package me.dennislab.diffvera.common.error;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Centralized exception handler for all REST controllers in Diffvera.
 * Converts application exceptions into standardized RFC 7807 Problem Details responses.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    private static final String DEFAULT_TYPE = "about:blank";

    /**
     * Handles all explicit domain exceptions extending DiffveraException.
     */
    @ExceptionHandler(DiffveraException.class)
    public ResponseEntity<ErrorResponse> handleDiffveraException(
        DiffveraException ex,
        HttpServletRequest request
    ) {
        if (ex.getStatus().is5xxServerError()) {
            log.error("Internal service error [{}]: {}", ex.getErrorCode(), ex.getMessage(), ex);
        } else {
            log.warn("Handled client error [{}] on {}: {}", ex.getErrorCode(), request.getRequestURI(), ex.getMessage());
        }

        ErrorResponse response = ErrorResponse.builder()
            .type(DEFAULT_TYPE)
            .title(ex.getTitle())
            .status(ex.getStatus().value())
            .detail(ex.getMessage())
            .instance(request.getRequestURI())
            .errorCode(ex.getErrorCode())
            .timestamp(OffsetDateTime.now())
            .build();

        return ResponseEntity.status(ex.getStatus()).body(response);
    }

    /**
     * Handles Jakarta Bean Validation errors (@Valid @NotNull, @NotBlank, etc.)
     * and maps each invalid field to a FieldErrorItem.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
        MethodArgumentNotValidException ex,
        HttpServletRequest request
    ) {
        log.warn("Validation failure on {}: {} field errors", request.getRequestURI(), ex.getBindingResult().getFieldErrorCount());

        List<FieldErrorItem> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
            .map(this::mapFieldError)
            .toList();

        ErrorResponse response = ErrorResponse.builder()
            .type(DEFAULT_TYPE)
            .title("Validation Failed")
            .status(HttpStatus.BAD_REQUEST.value())
            .detail("One or more request parameters failed validation constraints.")
            .instance(request.getRequestURI())
            .errorCode("VALIDATION_ERROR")
            .timestamp(OffsetDateTime.now())
            .fieldErrors(fieldErrors)
            .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    /**
     * Handles missing required query parameters.
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParam(
        MissingServletRequestParameterException ex,
        HttpServletRequest request
    ) {
        log.warn("Missing required parameter '{}' on {}", ex.getParameterName(), request.getRequestURI());

        ErrorResponse response = ErrorResponse.builder()
            .type(DEFAULT_TYPE)
            .title("Missing Required Parameter")
            .status(HttpStatus.BAD_REQUEST.value())
            .detail(String.format("Required query parameter '%s' of type '%s' is not present.", ex.getParameterName(), ex.getParameterType()))
            .instance(request.getRequestURI())
            .errorCode("MISSING_PARAMETER")
            .timestamp(OffsetDateTime.now())
            .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    /**
     * Handles illegal argument exceptions.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(
        IllegalArgumentException ex,
        HttpServletRequest request
    ) {
        log.warn("Illegal argument on {}: {}", request.getRequestURI(), ex.getMessage());

        ErrorResponse response = ErrorResponse.builder()
            .type(DEFAULT_TYPE)
            .title("Invalid Request")
            .status(HttpStatus.BAD_REQUEST.value())
            .detail(ex.getMessage())
            .instance(request.getRequestURI())
            .errorCode("INVALID_ARGUMENT")
            .timestamp(OffsetDateTime.now())
            .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    /**
     * Handles Spring Security access denied exceptions.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(
        AccessDeniedException ex,
        HttpServletRequest request
    ) {
        log.warn("Access denied on {}: {}", request.getRequestURI(), ex.getMessage());

        ErrorResponse response = ErrorResponse.builder()
            .type(DEFAULT_TYPE)
            .title("Access Denied")
            .status(HttpStatus.FORBIDDEN.value())
            .detail("You do not have the required permissions to perform this action.")
            .instance(request.getRequestURI())
            .errorCode("FORBIDDEN")
            .timestamp(OffsetDateTime.now())
            .build();

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
    }

    /**
     * Fallback handler for all unexpected unhandled exceptions.
     * Prevents internal technical stacktraces from leaking to the frontend.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpectedException(
        Exception ex,
        HttpServletRequest request
    ) {
        log.error("Unhandled unexpected exception on {}: {}", request.getRequestURI(), ex.getMessage(), ex);

        ErrorResponse response = ErrorResponse.builder()
            .type(DEFAULT_TYPE)
            .title("Internal Server Error")
            .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
            .detail("An unexpected error occurred. Please try again or contact support if the issue persists.")
            .instance(request.getRequestURI())
            .errorCode("INTERNAL_SERVER_ERROR")
            .timestamp(OffsetDateTime.now())
            .build();

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    private FieldErrorItem mapFieldError(FieldError fieldError) {
        return FieldErrorItem.builder()
            .field(fieldError.getField())
            .message(fieldError.getDefaultMessage())
            .rejectedValue(fieldError.getRejectedValue())
            .build();
    }
}
