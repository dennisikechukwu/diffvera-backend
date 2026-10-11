package me.dennislab.diffvera.review;

import com.fasterxml.jackson.databind.ObjectMapper;
import me.dennislab.diffvera.review.dto.FindingFeedbackRequestDto;
import me.dennislab.diffvera.review.dto.FindingResponseDto;
import me.dennislab.diffvera.user.CurrentUserService;
import me.dennislab.diffvera.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class FindingControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private ReviewService reviewService;

    @Mock
    private CurrentUserService currentUserService;

    @BeforeEach
    void setUp() {
        FindingController controller = new FindingController(reviewService, currentUserService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    @DisplayName("GET /api/v1/findings/{id} should return finding detail")
    void shouldReturnFinding() throws Exception {
        UUID findingId = UUID.randomUUID();
        FindingResponseDto finding = FindingResponseDto.builder()
            .id(findingId)
            .category("architecture")
            .severity("medium")
            .title("Service imports controller")
            .filePath("src/services/orders.ts")
            .build();

        when(reviewService.getFindingById(findingId)).thenReturn(finding);

        mockMvc.perform(get("/api/v1/findings/{id}", findingId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.category").value("architecture"))
            .andExpect(jsonPath("$.title").value("Service imports controller"));
    }

    @Test
    @DisplayName("POST /api/v1/findings/{id}/feedback should record feedback and return updated finding")
    void shouldSubmitFeedback() throws Exception {
        UUID findingId = UUID.randomUUID();
        User user = User.builder().id(UUID.randomUUID()).login("dev").build();
        when(currentUserService.getCurrentUserOrDevFallback()).thenReturn(user);

        FindingFeedbackRequestDto req = FindingFeedbackRequestDto.builder().feedback("useful").build();
        FindingResponseDto updated = FindingResponseDto.builder()
            .id(findingId)
            .feedback("useful")
            .build();

        when(reviewService.submitFindingFeedback(eq(findingId), any(FindingFeedbackRequestDto.class), any(User.class)))
            .thenReturn(updated);

        mockMvc.perform(post("/api/v1/findings/{id}/feedback", findingId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.feedback").value("useful"));
    }
}
