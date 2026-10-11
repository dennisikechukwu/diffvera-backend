package me.dennislab.diffvera.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Data Transfer Object representing the authenticated user's session profile.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Authenticated user profile response")
public class UserSessionDto {

    @Schema(description = "Internal unique user UUID", example = "123e4567-e89b-12d3-a456-426614174000")
    private UUID id;

    @Schema(description = "GitHub numeric user ID", example = "12345678")
    private Long githubUserId;

    @Schema(description = "GitHub handle / login username", example = "octocat")
    private String login;

    @Schema(description = "User's display name", example = "Monalisa Octocat")
    private String name;

    @Schema(description = "User's primary email address", example = "octocat@github.com")
    private String email;

    @Schema(description = "User's avatar URL", example = "https://avatars.githubusercontent.com/u/583231?v=4")
    private String avatarUrl;
}
