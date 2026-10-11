package me.dennislab.diffvera.review.gemini;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

/**
 * Structured finding item extracted from Google Gemini model response.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class GeminiFindingItem {

    // "architecture", "security", or "correctness"
    private String category;

    // "high", "medium", or "low"
    private String severity;

    private String title;
    private String summary;
    private String filePath;
    private int lineStart;
    private int lineEnd;
    private String codeExcerpt;
    private String explanation;
    private String suggestedCorrection;

    // Optional rule name if this finding violates an active architecture boundary rule
    private String ruleName;
}
