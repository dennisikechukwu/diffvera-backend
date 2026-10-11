package me.dennislab.diffvera.installation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Payload indicating whether the organization/user has completed GitHub App installation onboarding.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "GitHub App onboarding and connection status")
public class OnboardingStatusDto {

    @Schema(description = "Current status: ready (installed) or needs_installation", example = "ready")
    private String status;

    @Schema(description = "Count of active GitHub App installations", example = "1")
    private int installationCount;

    @Schema(description = "Total accessible repositories", example = "5")
    private int repositoryCount;

    @Schema(description = "Direct URL to install or configure the GitHub App", example = "https://github.com/apps/diffvera/installations/new")
    private String installUrl;
}
