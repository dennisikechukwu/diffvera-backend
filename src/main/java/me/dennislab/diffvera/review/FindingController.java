package me.dennislab.diffvera.review;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import me.dennislab.diffvera.review.dto.FindingFeedbackRequestDto;
import me.dennislab.diffvera.review.dto.FindingResponseDto;
import me.dennislab.diffvera.user.CurrentUserService;
import me.dennislab.diffvera.user.User;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST controller for inspecting findings and submitting developer feedback.
 */
@RestController
@RequestMapping("/api/v1/findings")
@Tag(name = "Findings", description = "AI review findings evaluation and feedback")
public class FindingController {

    private final ReviewService reviewService;
    private final CurrentUserService currentUserService;

    public FindingController(ReviewService reviewService, CurrentUserService currentUserService) {
        this.reviewService = reviewService;
        this.currentUserService = currentUserService;
    }

    @Operation(summary = "Get finding by ID", description = "Retrieves an individual code review finding.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Finding detail",
            content = @Content(schema = @Schema(implementation = FindingResponseDto.class))),
        @ApiResponse(responseCode = "404", description = "Finding not found")
    })
    @GetMapping("/{findingId}")
    public ResponseEntity<FindingResponseDto> getFindingById(
        @Parameter(description = "Finding UUID") @PathVariable UUID findingId
    ) {
        return ResponseEntity.ok(reviewService.getFindingById(findingId));
    }

    @Operation(summary = "Submit feedback on finding", description = "Submits or updates developer assessment (useful, false-positive, resolved) for a finding.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Feedback updated",
            content = @Content(schema = @Schema(implementation = FindingResponseDto.class))),
        @ApiResponse(responseCode = "400", description = "Invalid feedback value"),
        @ApiResponse(responseCode = "404", description = "Finding not found")
    })
    @PostMapping("/{findingId}/feedback")
    public ResponseEntity<FindingResponseDto> submitFeedback(
        @Parameter(description = "Finding UUID") @PathVariable UUID findingId,
        @Valid @RequestBody FindingFeedbackRequestDto request
    ) {
        User user = currentUserService.getCurrentUserOrDevFallback();
        FindingResponseDto updated = reviewService.submitFindingFeedback(findingId, request, user);
        return ResponseEntity.ok(updated);
    }
}
