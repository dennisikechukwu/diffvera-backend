package me.dennislab.diffvera.installation;

import me.dennislab.diffvera.repository.GitRepoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class OnboardingControllerTest {

    private MockMvc mockMvc;

    @Mock
    private InstallationRepository installationRepository;

    @Mock
    private GitRepoRepository gitRepoRepository;

    @BeforeEach
    void setUp() {
        OnboardingController controller = new OnboardingController(
            installationRepository,
            gitRepoRepository,
            "diffvera"
        );
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    @DisplayName("GET /api/v1/onboarding/status should report needs_installation when no installations exist")
    void shouldReportNeedsInstallation() throws Exception {
        when(installationRepository.count()).thenReturn(0L);
        when(gitRepoRepository.count()).thenReturn(0L);

        mockMvc.perform(get("/api/v1/onboarding/status"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("needs_installation"))
            .andExpect(jsonPath("$.installationCount").value(0))
            .andExpect(jsonPath("$.repositoryCount").value(0))
            .andExpect(jsonPath("$.installUrl").value("https://github.com/apps/diffvera/installations/new"));
    }

    @Test
    @DisplayName("GET /api/v1/onboarding/status should report ready when installations and repositories exist")
    void shouldReportReady() throws Exception {
        when(installationRepository.count()).thenReturn(1L);
        when(gitRepoRepository.count()).thenReturn(3L);

        mockMvc.perform(get("/api/v1/onboarding/status"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("ready"))
            .andExpect(jsonPath("$.installationCount").value(1))
            .andExpect(jsonPath("$.repositoryCount").value(3));
    }
}
