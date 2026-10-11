package me.dennislab.diffvera.installation.github.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Access token payload returned by GitHub POST /app/installations/{id}/access_tokens.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GitHubInstallationTokenResponse {

    @JsonProperty("token")
    private String token;

    @JsonProperty("expires_at")
    private String expiresAt;
}
