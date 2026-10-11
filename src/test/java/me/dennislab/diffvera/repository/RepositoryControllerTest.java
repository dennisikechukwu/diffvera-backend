package me.dennislab.diffvera.repository;

import me.dennislab.diffvera.repository.dto.RepositoryDto;
import me.dennislab.diffvera.repository.dto.RepositorySyncResultDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class RepositoryControllerTest {

    private MockMvc mockMvc;

    @Mock
    private RepositorySyncService repositorySyncService;

    @BeforeEach
    void setUp() {
        RepositoryController controller = new RepositoryController(repositorySyncService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    @DisplayName("GET /api/v1/repositories should return list of connected repositories")
    void shouldReturnRepositories() throws Exception {
        RepositoryDto repo = RepositoryDto.builder()
            .id(UUID.randomUUID())
            .name("core-service")
            .fullName("acme/core-service")
            .isPrivate(false)
            .language("Java")
            .defaultBranch("main")
            .activeRuleCount(3)
            .openPrCount(1)
            .build();

        when(repositorySyncService.getAllRepositories()).thenReturn(List.of(repo));

        mockMvc.perform(get("/api/v1/repositories"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].name").value("core-service"))
            .andExpect(jsonPath("$[0].fullName").value("acme/core-service"))
            .andExpect(jsonPath("$[0].activeRuleCount").value(3))
            .andExpect(jsonPath("$[0].openPrCount").value(1));
    }

    @Test
    @DisplayName("POST /api/v1/repositories/sync should trigger repository synchronization")
    void shouldTriggerSync() throws Exception {
        RepositorySyncResultDto result = RepositorySyncResultDto.builder()
            .status("completed")
            .syncedRepositoriesCount(5)
            .message("Synchronized 5 repositories.")
            .build();

        when(repositorySyncService.syncAllInstallations()).thenReturn(result);

        mockMvc.perform(post("/api/v1/repositories/sync"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("completed"))
            .andExpect(jsonPath("$.syncedRepositoriesCount").value(5));
    }
}
