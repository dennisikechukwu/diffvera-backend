package me.dennislab.diffvera.pullrequest;

import me.dennislab.diffvera.pullrequest.dto.PullRequestDto;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PullRequestControllerTest {

    private MockMvc mockMvc;

    @Mock
    private PullRequestService pullRequestService;

    @BeforeEach
    void setUp() {
        PullRequestController controller = new PullRequestController(pullRequestService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    @DisplayName("GET /api/v1/repositories/{repoId}/pull-requests should return PRs")
    void shouldReturnPullRequests() throws Exception {
        UUID repoId = UUID.randomUUID();
        PullRequestDto pr = PullRequestDto.builder()
            .id(UUID.randomUUID())
            .repositoryId(repoId)
            .number(42)
            .title("Add order details endpoint")
            .author("alex-morgan")
            .status("open")
            .headSha("f9b21a7")
            .build();

        when(pullRequestService.getPullRequestsByRepository(repoId, null)).thenReturn(List.of(pr));

        mockMvc.perform(get("/api/v1/repositories/{repoId}/pull-requests", repoId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].number").value(42))
            .andExpect(jsonPath("$[0].title").value("Add order details endpoint"))
            .andExpect(jsonPath("$[0].author").value("alex-morgan"));
    }

    @Test
    @DisplayName("GET /api/v1/pull-requests/{id} should return PR detail")
    void shouldReturnPullRequestById() throws Exception {
        UUID prId = UUID.randomUUID();
        PullRequestDto pr = PullRequestDto.builder()
            .id(prId)
            .number(17)
            .title("Fix retry delay")
            .status("merged")
            .build();

        when(pullRequestService.getPullRequestById(prId)).thenReturn(pr);

        mockMvc.perform(get("/api/v1/pull-requests/{id}", prId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.number").value(17))
            .andExpect(jsonPath("$.status").value("merged"));
    }
}
