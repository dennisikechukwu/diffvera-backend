package me.dennislab.diffvera.common.error;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Representation of an individual invalid input field error.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Details regarding a specific invalid input field")
public class FieldErrorItem {

    @Schema(description = "The name of the field that failed validation", example = "name")
    private String field;

    @Schema(description = "Human-readable explanation of why the value is invalid", example = "Rule name must not be blank")
    private String message;

    @Schema(description = "The value that was submitted and rejected", example = "")
    private Object rejectedValue;
}
