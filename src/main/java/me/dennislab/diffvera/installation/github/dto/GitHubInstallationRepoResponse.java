package me.dennislab.diffvera.installation.github.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * Top-level response from GitHub GET /installation/repositories.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GitHubInstallationRepoResponse {

    @JsonProperty("total_count")
    private Integer totalCount;

    @Builder.Default
    @JsonProperty("repositories")
    private List<GitHubRepoItemDto> repositories = new ArrayList<>();
}
