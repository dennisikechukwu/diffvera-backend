package me.dennislab.diffvera.rule.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Request payload for modifying an existing architectural rule.
 * Updating either name or boundary creates an immutable RuleVersion snapshot.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Request to update an architectural rule")
public class UpdateRuleRequestDto {

    @Size(max = 200, message = "Rule name cannot exceed 200 characters")
    @Schema(description = "Updated rule name (optional if keeping existing)", example = "Controllers must not import Services directly")
    private String name;

    @Schema(description = "Updated natural-language boundary constraint (creates new immutable version)", example = "Updated constraint description...")
    private String boundary;

    @Schema(description = "Whether the rule is enabled or disabled", example = "true")
    private Boolean isEnabled;
}
