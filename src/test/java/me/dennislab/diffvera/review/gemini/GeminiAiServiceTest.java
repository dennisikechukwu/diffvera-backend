package me.dennislab.diffvera.review.gemini;

import com.fasterxml.jackson.databind.ObjectMapper;
import me.dennislab.diffvera.diff.dto.GitHubFileDiffDto;
import me.dennislab.diffvera.rule.RuleVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GeminiAiServiceTest {

    @Mock
    private RestClient restClient;

    @Mock
    private RestClient.RequestBodyUriSpec requestBodyUriSpec;

    @Mock
    private RestClient.RequestBodySpec requestBodySpec;

    @Mock
    private RestClient.ResponseSpec responseSpec;

    private GeminiAiService geminiAiService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        geminiAiService = new GeminiAiService("test-key-12345", "gemini-2.5-flash", restClient, objectMapper);
    }

    @Test
    @DisplayName("isConfigured should return true when API key is set and false when blank")
    void shouldCheckConfigurationStatus() {
        assertThat(geminiAiService.isConfigured()).isTrue();

        GeminiAiService unconfigured = new GeminiAiService("", "gemini-2.5-flash", restClient, objectMapper);
        assertThat(unconfigured.isConfigured()).isFalse();
    }

    @Test
    @DisplayName("buildPrompt should format markdown documentation files separately from code files")
    void shouldFormatMarkdownDocsAndCodeInPrompt() {
        RuleVersion rule = RuleVersion.builder()
            .id(UUID.randomUUID())
            .versionNumber(1)
            .name("Layer Boundary")
            .boundary("Services must not import Controllers")
            .build();

        GitHubFileDiffDto docFile = GitHubFileDiffDto.builder()
            .filename("ARCHITECTURE.md")
            .status("modified")
            .patch("@@ -1 +1 @@\n+# Clean Architecture Rules")
            .build();

        GitHubFileDiffDto codeFile = GitHubFileDiffDto.builder()
            .filename("src/OrderService.java")
            .status("modified")
            .patch("@@ -10,3 +10,4 @@\n+import me.dennislab.UserController;")
            .build();

        org.springframework.ai.chat.prompt.Prompt springAiPrompt = 
            geminiAiService.createSpringAiPrompt("acme/core-repo", "feat: orders", List.of(rule), List.of(docFile, codeFile));

        String prompt = springAiPrompt.getContents();

        assertThat(springAiPrompt.getInstructions()).hasSize(2);
        assertThat(springAiPrompt.getInstructions().get(0)).isInstanceOf(org.springframework.ai.chat.messages.SystemMessage.class);
        assertThat(springAiPrompt.getInstructions().get(1)).isInstanceOf(org.springframework.ai.chat.messages.UserMessage.class);

        assertThat(prompt).contains("Repository: acme/core-repo");
        assertThat(prompt).contains("Layer Boundary");
        assertThat(prompt).contains("Architectural & Design Documentation Files (.md)");
        assertThat(prompt).contains("ARCHITECTURE.md");
        assertThat(prompt).contains("Source Code Changes");
        assertThat(prompt).contains("src/OrderService.java");
    }

    @Test
    @DisplayName("parseGeminiResponse should parse valid structured JSON and normalize findings")
    void shouldParseGeminiJsonFindings() {
        String geminiJson = """
            {
              "candidates": [
                {
                  "content": {
                    "parts": [
                      {
                        "text": "```json\\n{\\n  \\"findings\\": [\\n    {\\n      \\"category\\": \\"ARCHITECTURE\\",\\n      \\"severity\\": \\"HIGH\\",\\n      \\"title\\": \\"Cross-layer controller dependency in service\\",\\n      \\"summary\\": \\"OrderService directly couples with UserController\\",\\n      \\"filePath\\": \\"src/OrderService.java\\",\\n      \\"lineStart\\": 11,\\n      \\"lineEnd\\": 11,\\n      \\"codeExcerpt\\": \\"import me.dennislab.UserController;\\",\\n      \\"explanation\\": \\"Violates layer boundaries\\",\\n      \\"suggestedCorrection\\": \\"Inject UserRepository instead\\",\\n      \\"ruleName\\": \\"Layer Boundary\\"\\n    }\\n  ]\\n}\\n```"
                      }
                    ]
                  }
                }
              ]
            }
            """;

        List<GeminiFindingItem> findings = geminiAiService.parseGeminiResponse(geminiJson);

        assertThat(findings).hasSize(1);
        GeminiFindingItem item = findings.get(0);
        assertThat(item.getCategory()).isEqualTo("architecture");
        assertThat(item.getSeverity()).isEqualTo("high");
        assertThat(item.getTitle()).isEqualTo("Cross-layer controller dependency in service");
        assertThat(item.getFilePath()).isEqualTo("src/OrderService.java");
        assertThat(item.getLineStart()).isEqualTo(11);
        assertThat(item.getLineEnd()).isEqualTo(11);
        assertThat(item.getRuleName()).isEqualTo("Layer Boundary");
    }

    @Test
    @DisplayName("parseGeminiResponse should return empty list when no findings are returned")
    void shouldHandleCleanFindingsResponse() {
        String geminiJson = """
            {
              "candidates": [
                {
                  "content": {
                    "parts": [
                      {
                        "text": "{\\"findings\\": []}"
                      }
                    ]
                  }
                }
              ]
            }
            """;

        List<GeminiFindingItem> findings = geminiAiService.parseGeminiResponse(geminiJson);
        assertThat(findings).isEmpty();
    }

    @Test
    @DisplayName("analyzeDiff should return empty list without HTTP call if diff files list is empty")
    void shouldSkipWhenNoDiffFiles() {
        List<GeminiFindingItem> result = geminiAiService.analyzeDiff("acme/repo", "feat: test", List.of(), List.of());
        assertThat(result).isEmpty();
    }
}
