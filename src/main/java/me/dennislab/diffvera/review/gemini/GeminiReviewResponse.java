package me.dennislab.diffvera.review.gemini;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Top-level JSON wrapper expected from Gemini's structured output.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class GeminiReviewResponse {

    @Builder.Default
    private List<GeminiFindingItem> findings = new ArrayList<>();
}
