package me.dennislab.diffvera.rule;

import com.fasterxml.jackson.databind.ObjectMapper;
import me.dennislab.diffvera.rule.dto.CreateRuleRequestDto;
import me.dennislab.diffvera.rule.dto.RuleResponseDto;
import me.dennislab.diffvera.rule.dto.RuleVersionDto;
import me.dennislab.diffvera.rule.dto.UpdateRuleRequestDto;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ArchitectureRuleControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private ArchitectureRuleService ruleService;

    @BeforeEach
    void setUp() {
        ArchitectureRuleController controller = new ArchitectureRuleController(ruleService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    @DisplayName("GET /api/v1/repositories/{repoId}/rules should return rules list")
    void shouldReturnRules() throws Exception {
        UUID repoId = UUID.randomUUID();
        RuleResponseDto rule = RuleResponseDto.builder()
            .id(UUID.randomUUID())
            .repositoryId(repoId)
            .name("Test Rule")
            .boundary("Boundary description")
            .isEnabled(true)
            .currentVersion(1)
            .build();

        when(ruleService.getRulesByRepository(repoId)).thenReturn(List.of(rule));

        mockMvc.perform(get("/api/v1/repositories/{repoId}/rules", repoId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].name").value("Test Rule"))
            .andExpect(jsonPath("$[0].boundary").value("Boundary description"))
            .andExpect(jsonPath("$[0].currentVersion").value(1));
    }

    @Test
    @DisplayName("POST /api/v1/repositories/{repoId}/rules should create rule and return 201")
    void shouldCreateRule() throws Exception {
        UUID repoId = UUID.randomUUID();
        CreateRuleRequestDto request = CreateRuleRequestDto.builder()
            .name("New Rule")
            .boundary("New boundary")
            .build();

        RuleResponseDto created = RuleResponseDto.builder()
            .id(UUID.randomUUID())
            .repositoryId(repoId)
            .name("New Rule")
            .boundary("New boundary")
            .isEnabled(true)
            .currentVersion(1)
            .build();

        when(ruleService.createRule(eq(repoId), any(CreateRuleRequestDto.class))).thenReturn(created);

        mockMvc.perform(post("/api/v1/repositories/{repoId}/rules", repoId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("New Rule"))
            .andExpect(jsonPath("$.boundary").value("New boundary"));
    }

    @Test
    @DisplayName("PUT /api/v1/rules/{ruleId} should update rule and return 200")
    void shouldUpdateRule() throws Exception {
        UUID ruleId = UUID.randomUUID();
        UpdateRuleRequestDto request = UpdateRuleRequestDto.builder()
            .name("Updated Rule")
            .boundary("Updated boundary")
            .build();

        RuleResponseDto updated = RuleResponseDto.builder()
            .id(ruleId)
            .name("Updated Rule")
            .boundary("Updated boundary")
            .currentVersion(2)
            .build();

        when(ruleService.updateRule(eq(ruleId), any(UpdateRuleRequestDto.class))).thenReturn(updated);

        mockMvc.perform(put("/api/v1/rules/{ruleId}", ruleId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.currentVersion").value(2));
    }

    @Test
    @DisplayName("PATCH /api/v1/rules/{ruleId}/toggle should toggle enablement")
    void shouldToggleRule() throws Exception {
        UUID ruleId = UUID.randomUUID();
        RuleResponseDto toggled = RuleResponseDto.builder()
            .id(ruleId)
            .name("Rule")
            .isEnabled(false)
            .build();

        when(ruleService.toggleRule(ruleId, false)).thenReturn(toggled);

        mockMvc.perform(patch("/api/v1/rules/{ruleId}/toggle?enabled=false", ruleId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.isEnabled").value(false));
    }

    @Test
    @DisplayName("DELETE /api/v1/rules/{ruleId} should delete rule and return 204")
    void shouldDeleteRule() throws Exception {
        UUID ruleId = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/rules/{ruleId}", ruleId))
            .andExpect(status().isNoContent());

        verify(ruleService).deleteRule(ruleId);
    }

    @Test
    @DisplayName("GET /api/v1/rules/{ruleId}/versions should return version history")
    void shouldReturnVersions() throws Exception {
        UUID ruleId = UUID.randomUUID();
        RuleVersionDto v1 = RuleVersionDto.builder().versionNumber(1).name("Rule").boundary("B1").build();

        when(ruleService.getRuleVersionHistory(ruleId)).thenReturn(List.of(v1));

        mockMvc.perform(get("/api/v1/rules/{ruleId}/versions", ruleId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].versionNumber").value(1))
            .andExpect(jsonPath("$[0].boundary").value("B1"));
    }
}
