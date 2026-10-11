package me.dennislab.diffvera.review;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import me.dennislab.diffvera.review.dto.ReviewDetailDto;
import me.dennislab.diffvera.review.dto.ReviewSummaryDto;
import me.dennislab.diffvera.review.dto.ReviewTriggerRequestDto;
import me.dennislab.diffvera.user.CurrentUserService;
import me.dennislab.diffvera.user.User;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for managing AI code review runs and review histories.
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Reviews", description = "AI code review execution, history, and detailed results")
public class ReviewController {

    private final ReviewService reviewService;
    private final CurrentUserService currentUserService;

    public ReviewController(ReviewService reviewService, CurrentUserService currentUserService) {
        this.reviewService = reviewService;
        this.currentUserService = currentUserService;
    }

    @Operation(summary = "Get review details by ID", description = "Retrieves full review results including pull request info, findings, and parsed file diffs.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Review details",
            content = @Content(schema = @Schema(implementation = ReviewDetailDto.class))),
        @ApiResponse(responseCode = "404", description = "Review not found")
    })
    @GetMapping("/reviews/{id}")
    public ResponseEntity<ReviewDetailDto> getReviewById(
        @Parameter(description = "Review Run UUID") @PathVariable UUID id
    ) {
        return ResponseEntity.ok(reviewService.getReviewById(id));
    }

    @Operation(summary = "List review history", description = "Returns review runs, optionally filtered by repository and status.")
    @ApiResponse(responseCode = "200", description = "List of reviews",
        content = @Content(array = @ArraySchema(schema = @Schema(implementation = ReviewSummaryDto.class))))
    @GetMapping("/reviews")
    public ResponseEntity<List<ReviewSummaryDto>> getReviews(
        @Parameter(description = "Optional filter by Repository UUID") @RequestParam(required = false) UUID repositoryId,
        @Parameter(description = "Optional filter by status (queued, running, completed, failed)") @RequestParam(required = false) String status,
        @Parameter(description = "Maximum results to return") @RequestParam(defaultValue = "20") int limit
    ) {
        return ResponseEntity.ok(reviewService.getRecentReviews(repositoryId, status, limit));
    }

    @Operation(summary = "List reviews for a pull request", description = "Returns all historical review runs executed on a given pull request.")
    @ApiResponse(responseCode = "200", description = "List of reviews for pull request",
        content = @Content(array = @ArraySchema(schema = @Schema(implementation = ReviewSummaryDto.class))))
    @GetMapping("/pull-requests/{pullRequestId}/reviews")
    public ResponseEntity<List<ReviewSummaryDto>> getReviewsByPullRequest(
        @Parameter(description = "Pull Request UUID") @PathVariable UUID pullRequestId
    ) {
        return ResponseEntity.ok(reviewService.getReviewsByPullRequest(pullRequestId));
    }

    @Operation(summary = "Trigger AI code review for a pull request", description = "Queues and dispatches an asynchronous AI code review run.")
    @ApiResponses({
        @ApiResponse(responseCode = "202", description = "Review queued/accepted",
            content = @Content(schema = @Schema(implementation = ReviewDetailDto.class))),
        @ApiResponse(responseCode = "400", description = "Validation error"),
        @ApiResponse(responseCode = "404", description = "Pull request not found")
    })
    @PostMapping("/reviews/trigger")
    public ResponseEntity<ReviewDetailDto> triggerReview(
        @Valid @RequestBody ReviewTriggerRequestDto request
    ) {
        User user = currentUserService.getCurrentUserOrDevFallback();
        ReviewDetailDto review = reviewService.triggerReview(request.getPullRequestId(), user);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(review);
    }
}
