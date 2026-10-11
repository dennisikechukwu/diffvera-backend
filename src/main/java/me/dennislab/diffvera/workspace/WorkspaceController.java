package me.dennislab.diffvera.workspace;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import me.dennislab.diffvera.user.CurrentUserService;
import me.dennislab.diffvera.user.User;
import me.dennislab.diffvera.workspace.dto.WorkspaceSnapshotDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller providing aggregated workspace snapshots for the frontend.
 */
@RestController
@RequestMapping("/api/v1/workspace")
@Tag(name = "Workspace", description = "Aggregated workspace state snapshot")
public class WorkspaceController {

    private final WorkspaceService workspaceService;
    private final CurrentUserService currentUserService;

    public WorkspaceController(WorkspaceService workspaceService, CurrentUserService currentUserService) {
        this.workspaceService = workspaceService;
        this.currentUserService = currentUserService;
    }

    @Operation(summary = "Get workspace snapshot", description = "Returns full aggregated snapshot of account, connected repositories, pull requests, reviews, and architecture rules.")
    @ApiResponse(responseCode = "200", description = "Aggregated workspace snapshot",
        content = @Content(schema = @Schema(implementation = WorkspaceSnapshotDto.class)))
    @GetMapping
    public ResponseEntity<WorkspaceSnapshotDto> getWorkspaceSnapshot() {
        User user = currentUserService.getCurrentUserOrDevFallback();
        return ResponseEntity.ok(workspaceService.getWorkspaceSnapshot(user));
    }
}
