package me.dennislab.diffvera.pullrequest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import me.dennislab.diffvera.pullrequest.dto.PullRequestDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for querying pull requests in connected repositories.
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Pull Requests", description = "Pull request querying and details")
public class PullRequestController {

    private final PullRequestService pullRequestService;

    public PullRequestController(PullRequestService pullRequestService) {
        this.pullRequestService = pullRequestService;
    }

    @Operation(summary = "List pull requests for a repository", description = "Returns pull requests for a repository, optionally filtered by status ('open', 'merged', 'closed').")
    @ApiResponse(responseCode = "200", description = "List of pull requests",
        content = @Content(array = @ArraySchema(schema = @Schema(implementation = PullRequestDto.class))))
    @GetMapping("/repositories/{repositoryId}/pull-requests")
    public ResponseEntity<List<PullRequestDto>> getPullRequestsByRepository(
        @Parameter(description = "Repository UUID") @PathVariable UUID repositoryId,
        @Parameter(description = "Filter by status: open, merged, closed") @RequestParam(required = false) String status
    ) {
        return ResponseEntity.ok(pullRequestService.getPullRequestsByRepository(repositoryId, status));
    }

    @Operation(summary = "Get pull request by ID", description = "Retrieves an individual pull request by its unique UUID.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Pull request detail",
            content = @Content(schema = @Schema(implementation = PullRequestDto.class))),
        @ApiResponse(responseCode = "404", description = "Pull request not found")
    })
    @GetMapping("/pull-requests/{pullRequestId}")
    public ResponseEntity<PullRequestDto> getPullRequestById(
        @Parameter(description = "Pull Request UUID") @PathVariable UUID pullRequestId
    ) {
        return ResponseEntity.ok(pullRequestService.getPullRequestById(pullRequestId));
    }
}
