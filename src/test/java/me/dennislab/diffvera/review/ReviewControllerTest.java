package me.dennislab.diffvera.review;

import com.fasterxml.jackson.databind.ObjectMapper;
import me.dennislab.diffvera.review.dto.ReviewDetailDto;
import me.dennislab.diffvera.review.dto.ReviewSummaryDto;
import me.dennislab.diffvera.review.dto.ReviewTriggerRequestDto;
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

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ReviewControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private ReviewService reviewService;

    @Mock
    private CurrentUserService currentUserService;

    @BeforeEach
    void setUp() {
        ReviewController controller = new ReviewController(reviewService, currentUserService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    @DisplayName("GET /api/v1/reviews/{id} should return review detail")
    void shouldReturnReviewById() throws Exception {
        UUID reviewId = UUID.randomUUID();
        ReviewDetailDto detail = ReviewDetailDto.builder()
            .id(reviewId)
            .pullRequestNumber(42)
            .status("completed")
            .commitSha("f9b21a7")
            .build();

        when(reviewService.getReviewById(reviewId)).thenReturn(detail);

        mockMvc.perform(get("/api/v1/reviews/{id}", reviewId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.pullRequestNumber").value(42))
            .andExpect(jsonPath("$.status").value("completed"))
            .andExpect(jsonPath("$.commitSha").value("f9b21a7"));
    }

    @Test
    @DisplayName("GET /api/v1/reviews should return recent review summaries")
    void shouldReturnRecentReviews() throws Exception {
        ReviewSummaryDto summary = ReviewSummaryDto.builder()
            .id(UUID.randomUUID())
            .pullRequestNumber(42)
            .status("completed")
            .findingCount(3)
            .build();

        when(reviewService.getRecentReviews(null, null, 20)).thenReturn(List.of(summary));

        mockMvc.perform(get("/api/v1/reviews"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].pullRequestNumber").value(42))
            .andExpect(jsonPath("$[0].findingCount").value(3));
    }

    @Test
    @DisplayName("POST /api/v1/reviews/trigger should return 202 Accepted")
    void shouldTriggerReview() throws Exception {
        UUID prId = UUID.randomUUID();
        User user = User.builder().id(UUID.randomUUID()).login("dev").build();
        when(currentUserService.getCurrentUserOrDevFallback()).thenReturn(user);

        ReviewDetailDto queued = ReviewDetailDto.builder()
            .id(UUID.randomUUID())
            .pullRequestId(prId)
            .status("queued")
            .build();

        when(reviewService.triggerReview(eq(prId), any(User.class))).thenReturn(queued);

        ReviewTriggerRequestDto request = ReviewTriggerRequestDto.builder().pullRequestId(prId).build();

        mockMvc.perform(post("/api/v1/reviews/trigger")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.status").value("queued"));
    }
}
