package me.dennislab.diffvera.common.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Standard RFC 7807 Problem Details response payload for all Diffvera API errors.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Schema(description = "Standardized RFC 7807 Problem Details error payload")
public class ErrorResponse {

    @Schema(description = "A URI reference identifying the specific error category", example = "https://api.diffvera.com/errors/resource-not-found")
    private String type;

    @Schema(description = "Short human-readable summary of the problem type", example = "Resource Not Found")
    private String title;

    @Schema(description = "HTTP status code", example = "404")
    private int status;

    @Schema(description = "Human-readable explanation specific to this occurrence of the problem", example = "Repository 'facebook/react' was not found.")
    private String detail;

    @Schema(description = "URI path of the request that triggered the error", example = "/api/v1/repositories/123")
    private String instance;

    @Schema(description = "Machine-readable domain error code for frontend programmatic handling", example = "REPOSITORY_NOT_FOUND")
    private String errorCode;

    @Builder.Default
    @Schema(description = "UTC timestamp when the error occurred")
    private OffsetDateTime timestamp = OffsetDateTime.now();

    @Builder.Default
    @Schema(description = "List of invalid parameters when the error represents a validation failure")
    private List<FieldErrorItem> fieldErrors = new ArrayList<>();
}
