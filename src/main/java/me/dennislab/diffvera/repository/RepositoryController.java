package me.dennislab.diffvera.repository;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import me.dennislab.diffvera.repository.dto.RepositoryDto;
import me.dennislab.diffvera.repository.dto.RepositorySyncResultDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller for managing connected repositories and triggering synchronization.
 */
@RestController
@RequestMapping("/api/v1/repositories")
@Tag(name = "Repositories", description = "Connected repository management and synchronization")
@Slf4j
public class RepositoryController {

    private final RepositorySyncService repositorySyncService;
    private final me.dennislab.diffvera.user.CurrentUserService currentUserService;

    public RepositoryController(RepositorySyncService repositorySyncService) {
        this(repositorySyncService, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public RepositoryController(RepositorySyncService repositorySyncService, me.dennislab.diffvera.user.CurrentUserService currentUserService) {
        this.repositorySyncService = repositorySyncService;
        this.currentUserService = currentUserService;
    }

    @Operation(summary = "List all connected repositories", description = "Returns all repositories connected through active GitHub App installations, including rule and PR counts.")
    @ApiResponse(responseCode = "200", description = "List of connected repositories",
        content = @Content(array = @ArraySchema(schema = @Schema(implementation = RepositoryDto.class))))
    @GetMapping
    public ResponseEntity<List<RepositoryDto>> getRepositories() {
        if (currentUserService != null) {
            me.dennislab.diffvera.user.User user = currentUserService.getCurrentUserOrDevFallback();
            return ResponseEntity.ok(repositorySyncService.getRepositoriesForUser(user));
        }
        return ResponseEntity.ok(repositorySyncService.getAllRepositories());
    }

    @Operation(summary = "Synchronize repositories from GitHub", description = "Queries GitHub for all active installations and synchronizes new or updated repositories.")
    @ApiResponse(responseCode = "200", description = "Synchronization summary",
        content = @Content(schema = @Schema(implementation = RepositorySyncResultDto.class)))
    @PostMapping("/sync")
    public ResponseEntity<RepositorySyncResultDto> syncRepositories() {
        log.info("Manual repository synchronization requested.");
        RepositorySyncResultDto result = repositorySyncService.syncAllInstallations();
        return ResponseEntity.ok(result);
    }
}
