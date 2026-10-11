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
 * Data transfer object representing an immutable historical version snapshot of an architecture rule.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Immutable historical version of an architecture rule")
public class RuleVersionDto {

    @Schema(description = "Unique ID of this version snapshot", example = "d1e2f3a4-b5c6-7890-abcd-ef1234567890")
    private UUID id;

    @Schema(description = "Sequential version number", example = "2")
    private Integer versionNumber;

    @Schema(description = "Rule name at this version", example = "Controllers must not import Services directly")
    private String name;

    @Schema(description = "Architectural boundary at this version", example = "Controllers must only interact with application facades.")
    private String boundary;

    @Schema(description = "Timestamp when this version snapshot was committed")
    private OffsetDateTime createdAt;
}
