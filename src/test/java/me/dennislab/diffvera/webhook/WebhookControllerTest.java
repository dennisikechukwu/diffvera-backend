package me.dennislab.diffvera.webhook;

import me.dennislab.diffvera.common.error.GlobalExceptionHandler;
import me.dennislab.diffvera.idempotency.IdempotencyService;
import me.dennislab.diffvera.webhook.dto.WebhookResponseDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class WebhookControllerTest {

    private MockMvc mockMvc;

    @Mock
    private WebhookSignatureVerifier signatureVerifier;

    @Mock
    private IdempotencyService idempotencyService;

    @Mock
    private WebhookDispatcherService dispatcherService;

    @BeforeEach
    void setUp() {
        WebhookController controller = new WebhookController(
            signatureVerifier,
            idempotencyService,
            dispatcherService
        );
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    }

    @Test
    @DisplayName("POST /api/v1/webhooks/github should process valid webhook and return 200 OK")
    void shouldProcessValidWebhook() throws Exception {
        when(signatureVerifier.verify(any(), eq("sha256=validsig"))).thenReturn(true);
        when(idempotencyService.tryAcquire("delivery-123")).thenReturn(true);

        WebhookResponseDto responseDto = WebhookResponseDto.builder()
            .status("processed")
            .event("pull_request")
            .delivery("delivery-123")
            .message("Queued review run.")
            .build();

        when(dispatcherService.dispatch(eq("pull_request"), eq("delivery-123"), any())).thenReturn(responseDto);

        mockMvc.perform(post("/api/v1/webhooks/github")
                .header("X-GitHub-Event", "pull_request")
                .header("X-GitHub-Delivery", "delivery-123")
                .header("X-Hub-Signature-256", "sha256=validsig")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"opened\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("processed"))
            .andExpect(jsonPath("$.event").value("pull_request"))
            .andExpect(jsonPath("$.delivery").value("delivery-123"));

        verify(dispatcherService).dispatch(eq("pull_request"), eq("delivery-123"), any());
    }

    @Test
    @DisplayName("POST /api/v1/webhooks/github should drop duplicate delivery and return 200 OK without dispatching")
    void shouldDropDuplicateDelivery() throws Exception {
        when(signatureVerifier.verify(any(), eq("sha256=validsig"))).thenReturn(true);
        when(idempotencyService.tryAcquire("delivery-dup-999")).thenReturn(false);

        mockMvc.perform(post("/api/v1/webhooks/github")
                .header("X-GitHub-Event", "pull_request")
                .header("X-GitHub-Delivery", "delivery-dup-999")
                .header("X-Hub-Signature-256", "sha256=validsig")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"opened\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("ignored"))
            .andExpect(jsonPath("$.message").value("Duplicate delivery dropped by idempotency guard."));

        verify(dispatcherService, never()).dispatch(any(), any(), any());
    }

    @Test
    @DisplayName("POST /api/v1/webhooks/github should return 401 Unauthorized when signature check fails")
    void shouldRejectInvalidSignature() throws Exception {
        when(signatureVerifier.verify(any(), eq("sha256=bad-sig"))).thenReturn(false);

        mockMvc.perform(post("/api/v1/webhooks/github")
                .header("X-GitHub-Event", "pull_request")
                .header("X-GitHub-Delivery", "delivery-401")
                .header("X-Hub-Signature-256", "sha256=bad-sig")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"opened\"}"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.status").value(401))
            .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
    }
}
