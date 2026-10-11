package me.dennislab.diffvera.workspace;

import me.dennislab.diffvera.user.CurrentUserService;
import me.dennislab.diffvera.user.User;
import me.dennislab.diffvera.workspace.dto.WorkspaceSnapshotDto;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class WorkspaceControllerTest {

    private MockMvc mockMvc;

    @Mock
    private WorkspaceService workspaceService;

    @Mock
    private CurrentUserService currentUserService;

    @BeforeEach
    void setUp() {
        WorkspaceController controller = new WorkspaceController(workspaceService, currentUserService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    @DisplayName("GET /api/v1/workspace should return aggregated workspace snapshot")
    void shouldReturnWorkspaceSnapshot() throws Exception {
        User user = User.builder().id(UUID.randomUUID()).login("alex-morgan").name("Alex Morgan").build();
        when(currentUserService.getCurrentUserOrDevFallback()).thenReturn(user);

        WorkspaceSnapshotDto.WorkspaceAccountDto account = WorkspaceSnapshotDto.WorkspaceAccountDto.builder()
            .id(user.getId().toString())
            .login("alex-morgan")
            .name("Alex Morgan")
            .build();

        WorkspaceSnapshotDto.WorkspaceRepositoryDto repo = WorkspaceSnapshotDto.WorkspaceRepositoryDto.builder()
            .id(UUID.randomUUID().toString())
            .fullName("northstar/order-service")
            .language("TypeScript")
            .visibility("private")
            .access("available")
            .build();

        WorkspaceSnapshotDto snapshot = WorkspaceSnapshotDto.builder()
            .account(account)
            .repositories(List.of(repo))
            .build();

        when(workspaceService.getWorkspaceSnapshot(any())).thenReturn(snapshot);

        mockMvc.perform(get("/api/v1/workspace"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.account.login").value("alex-morgan"))
            .andExpect(jsonPath("$.repositories[0].fullName").value("northstar/order-service"))
            .andExpect(jsonPath("$.repositories[0].language").value("TypeScript"));
    }
}
