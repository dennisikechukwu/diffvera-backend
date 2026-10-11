package me.dennislab.diffvera.installation.github.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubInstallationDetailDto(
    Long id,
    Account account
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Account(
        String login,
        String type
    ) {}
}
