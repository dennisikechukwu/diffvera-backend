package me.dennislab.diffvera.rule.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Request payload for defining a new architectural governance rule.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Request to create an architectural rule")
public class CreateRuleRequestDto {

    @NotBlank(message = "Rule name is required")
    @Size(max = 200, message = "Rule name cannot exceed 200 characters")
    @Schema(description = "Human-readable rule title", example = "Controllers must not import Services directly")
    private String name;

    @NotBlank(message = "Rule boundary constraint is required")
    @Schema(description = "Natural-language architectural boundary description for AI evaluation", example = "Controllers must only interact with application facades and DTOs; never direct database models or repositories.")
    private String boundary;
}
