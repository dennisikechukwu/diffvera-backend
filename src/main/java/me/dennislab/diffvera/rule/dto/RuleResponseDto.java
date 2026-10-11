package me.dennislab.diffvera.rule.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Data transfer object representing an architecture rule with its current version boundary.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Architecture governance rule")
public class RuleResponseDto {

    @Schema(description = "Unique rule ID", example = "a1b2c3d4-e5f6-7890-abcd-ef1234567890")
    private UUID id;

    @Schema(description = "Repository ID this rule belongs to", example = "b2c3d4e5-f6a7-8901-bcde-f12345678901")
    private UUID repositoryId;

    @Schema(description = "Rule name / headline", example = "Controllers must not import Services directly")
    private String name;

    @Schema(description = "Current architectural boundary evaluated by the AI", example = "Modules in the controller package must communicate with domain services solely through orchestrator interfaces.")
    private String boundary;

    @Schema(description = "Whether the rule is active for upcoming reviews", example = "true")
    private Boolean isEnabled;

    @Schema(description = "Current version number of this rule", example = "1")
    private Integer currentVersion;

    @Schema(description = "Rule creation timestamp")
    private OffsetDateTime createdAt;

    @Schema(description = "Rule last modification timestamp")
    private OffsetDateTime updatedAt;
}
