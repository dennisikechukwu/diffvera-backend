package me.dennislab.diffvera.rule;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import me.dennislab.diffvera.rule.dto.CreateRuleRequestDto;
import me.dennislab.diffvera.rule.dto.RuleResponseDto;
import me.dennislab.diffvera.rule.dto.RuleVersionDto;
import me.dennislab.diffvera.rule.dto.UpdateRuleRequestDto;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for managing architecture governance rules and version histories.
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Architecture Rules", description = "Architecture governance rules and immutable version snapshots")
public class ArchitectureRuleController {

    private final ArchitectureRuleService ruleService;

    public ArchitectureRuleController(ArchitectureRuleService ruleService) {
        this.ruleService = ruleService;
    }

    @Operation(summary = "List rules for a repository", description = "Returns all architecture rules configured for the given repository.")
    @ApiResponse(responseCode = "200", description = "List of rules",
        content = @Content(array = @ArraySchema(schema = @Schema(implementation = RuleResponseDto.class))))
    @GetMapping("/repositories/{repositoryId}/rules")
    public ResponseEntity<List<RuleResponseDto>> getRulesByRepository(
        @Parameter(description = "Repository UUID") @PathVariable UUID repositoryId
    ) {
        return ResponseEntity.ok(ruleService.getRulesByRepository(repositoryId));
    }

    @Operation(summary = "Create an architecture rule", description = "Defines a new architecture rule with an initial immutable Version 1 snapshot.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Rule created",
            content = @Content(schema = @Schema(implementation = RuleResponseDto.class))),
        @ApiResponse(responseCode = "400", description = "Validation error"),
        @ApiResponse(responseCode = "404", description = "Repository not found")
    })
    @PostMapping("/repositories/{repositoryId}/rules")
    public ResponseEntity<RuleResponseDto> createRule(
        @Parameter(description = "Repository UUID") @PathVariable UUID repositoryId,
        @Valid @RequestBody CreateRuleRequestDto request
    ) {
        RuleResponseDto created = ruleService.createRule(repositoryId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @Operation(summary = "Get rule by ID", description = "Retrieves an individual architecture rule with its current boundary constraint.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Rule detail",
            content = @Content(schema = @Schema(implementation = RuleResponseDto.class))),
        @ApiResponse(responseCode = "404", description = "Rule not found")
    })
    @GetMapping("/rules/{ruleId}")
    public ResponseEntity<RuleResponseDto> getRuleById(
        @Parameter(description = "Rule UUID") @PathVariable UUID ruleId
    ) {
        return ResponseEntity.ok(ruleService.getRuleById(ruleId));
    }

    @Operation(summary = "Update an architecture rule", description = "Updates rule details. If boundary or name changes, an immutable Version N+1 snapshot is created.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Rule updated",
            content = @Content(schema = @Schema(implementation = RuleResponseDto.class))),
        @ApiResponse(responseCode = "404", description = "Rule not found")
    })
    @PutMapping("/rules/{ruleId}")
    public ResponseEntity<RuleResponseDto> updateRule(
        @Parameter(description = "Rule UUID") @PathVariable UUID ruleId,
        @Valid @RequestBody UpdateRuleRequestDto request
    ) {
        return ResponseEntity.ok(ruleService.updateRule(ruleId, request));
    }

    @Operation(summary = "Toggle rule enablement", description = "Enables or disables a rule for subsequent AI reviews.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Rule status updated",
            content = @Content(schema = @Schema(implementation = RuleResponseDto.class))),
        @ApiResponse(responseCode = "404", description = "Rule not found")
    })
    @PatchMapping("/rules/{ruleId}/toggle")
    public ResponseEntity<RuleResponseDto> toggleRule(
        @Parameter(description = "Rule UUID") @PathVariable UUID ruleId,
        @Parameter(description = "Whether rule is enabled") @RequestParam boolean enabled
    ) {
        return ResponseEntity.ok(ruleService.toggleRule(ruleId, enabled));
    }

    @Operation(summary = "Delete an architecture rule", description = "Deletes a rule and all its historical version snapshots.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Rule deleted"),
        @ApiResponse(responseCode = "404", description = "Rule not found")
    })
    @DeleteMapping("/rules/{ruleId}")
    public ResponseEntity<Void> deleteRule(
        @Parameter(description = "Rule UUID") @PathVariable UUID ruleId
    ) {
        ruleService.deleteRule(ruleId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Get immutable rule version history", description = "Returns all historical version snapshots for a rule.")
    @ApiResponse(responseCode = "200", description = "List of historical rule versions",
        content = @Content(array = @ArraySchema(schema = @Schema(implementation = RuleVersionDto.class))))
    @GetMapping("/rules/{ruleId}/versions")
    public ResponseEntity<List<RuleVersionDto>> getRuleVersions(
        @Parameter(description = "Rule UUID") @PathVariable UUID ruleId
    ) {
        return ResponseEntity.ok(ruleService.getRuleVersionHistory(ruleId));
    }
}
