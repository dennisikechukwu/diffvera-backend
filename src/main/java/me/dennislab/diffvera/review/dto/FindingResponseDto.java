package me.dennislab.diffvera.review.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Data transfer object representing a review finding with developer feedback.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Individual AI review finding anchored to code")
public class FindingResponseDto {

    @Schema(description = "Unique finding UUID", example = "d1e2f3a4-b5c6-7890-abcd-ef1234567890")
    private UUID id;

    @Schema(description = "Parent review run UUID")
    private UUID reviewRunId;

    @Schema(description = "Associated architecture rule version UUID (null for security/correctness)")
    private UUID ruleVersionId;

    @Schema(description = "Associated architecture rule UUID (null for security/correctness)")
    private UUID ruleId;

    @Schema(description = "Rule name if architecture finding")
    private String ruleName;

    @Schema(description = "Category: security, architecture, correctness", example = "security")
    private String category;

    @Schema(description = "Severity: high, medium, low", example = "high")
    private String severity;

    @Schema(description = "Headline title", example = "An order ID isn't an ownership check.")
    private String title;

    @Schema(description = "Summary explanation")
    private String summary;

    @Schema(description = "Relative file path", example = "src/services/orders.ts")
    private String filePath;

    @Schema(description = "Relative file path alias for frontend compatibility", example = "src/services/orders.ts")
    private String file;

    @Schema(description = "1-based starting line number", example = "25")
    private Integer lineStart;

    @Schema(description = "1-based starting line alias for frontend compatibility", example = "25")
    private Integer startLine;

    @Schema(description = "1-based ending line number", example = "28")
    private Integer lineEnd;

    @Schema(description = "1-based ending line alias for frontend compatibility", example = "28")
    private Integer endLine;

    @Schema(description = "Code excerpt from diff")
    private String codeExcerpt;

    @Schema(description = "Technical explanation of the problem")
    private String explanation;

    @Schema(description = "Actionable suggestion to resolve")
    private String suggestion;

    @Schema(description = "Suggested code correction")
    private String correction;

    @Schema(description = "Suggested replacement code snippet")
    private String suggestedCorrection;

    @Schema(description = "Developer feedback: useful, false-positive, resolved, or null", example = "useful")
    private String feedback;

    @Schema(description = "Creation timestamp")
    private OffsetDateTime createdAt;
}
