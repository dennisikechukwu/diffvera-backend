package me.dennislab.diffvera.diff.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Response payload from GitHub Compare API:
 * GET /repos/{owner}/{repo}/compare/{base}...{head}
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class GitHubCompareResponse {

    private String status;

    @JsonProperty("ahead_by")
    private int aheadBy;

    @JsonProperty("behind_by")
    private int behindBy;

    @JsonProperty("total_commits")
    private int totalCommits;

    @Builder.Default
    private List<GitHubFileDiffDto> files = new ArrayList<>();
}
