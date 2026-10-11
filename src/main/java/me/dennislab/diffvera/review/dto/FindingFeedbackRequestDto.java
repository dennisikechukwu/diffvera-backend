package me.dennislab.diffvera.review.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Request payload for submitting developer assessment on a finding.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Developer feedback on a code review finding")
public class FindingFeedbackRequestDto {

    @NotBlank(message = "Feedback status is required")
    @Schema(description = "Assessment: useful, false_positive (or false-positive), resolved", example = "useful")
    private String feedback;
}
