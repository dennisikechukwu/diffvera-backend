package me.dennislab.diffvera.review.gemini;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import me.dennislab.diffvera.diff.dto.GitHubFileDiffDto;
import me.dennislab.diffvera.rule.RuleVersion;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.*;

/**
 * Service that integrates with Google Gemini (gemini-flash-latest) using
 * Spring AI design patterns: PromptTemplate, SystemMessage, UserMessage,
 * and BeanOutputConverter for type-safe structured output extraction.
 */
@Service
@Slf4j
public class GeminiAiService {

    private final String apiKey;
    private final String model;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final BeanOutputConverter<GeminiReviewResponse> outputConverter;

    public GeminiAiService(
        @Value("${diffvera.gemini.api-key:}") String apiKey,
        @Value("${diffvera.gemini.model:gemini-flash-latest}") String model
    ) {
        this(apiKey, model, RestClient.builder().build(), new ObjectMapper());
    }

    @Autowired
    public GeminiAiService(
        @Value("${diffvera.gemini.api-key:}") String apiKey,
        @Value("${diffvera.gemini.model:gemini-flash-latest}") String model,
        RestClient restClient,
        ObjectMapper objectMapper
    ) {
        this.apiKey = apiKey != null ? apiKey.trim() : "";
        this.model = (model != null && !model.isBlank()) ? model.trim() : "gemini-flash-latest";
        this.restClient = restClient != null ? restClient : RestClient.builder().build();
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
        this.outputConverter = new BeanOutputConverter<>(GeminiReviewResponse.class, this.objectMapper);
    }

    public boolean isConfigured() {
        return !apiKey.isBlank();
    }

    /**
     * Constructs a Spring AI Prompt with System and User messages,
     * sends it to the Gemini model, and converts the structured JSON output.
     */
    public List<GeminiFindingItem> analyzeDiff(
        String repoName,
        String prTitle,
        List<RuleVersion> activeRules,
        List<GitHubFileDiffDto> diffFiles
    ) {
        if (!isConfigured()) {
            log.warn("Gemini API key is not configured; skipping AI review.");
            return Collections.emptyList();
        }

        if (diffFiles == null || diffFiles.isEmpty()) {
            log.info("No reviewable file diffs provided for {}; skipping AI review.", repoName);
            return Collections.emptyList();
        }

        // Build Spring AI Prompt object (SystemMessage + UserMessage)
        Prompt springAiPrompt = createSpringAiPrompt(repoName, prTitle, activeRules, diffFiles);
        String fullPromptText = springAiPrompt.getContents();
        log.info("Sending Spring AI prompt (length: {} chars) to Gemini model '{}' for {}", fullPromptText.length(), model, repoName);

        Map<String, Object> requestBody = Map.of(
            "contents", List.of(
                Map.of("parts", List.of(Map.of("text", fullPromptText)))
            ),
            "generationConfig", Map.of(
                "responseMimeType", "application/json",
                "temperature", 0.1
            )
        );

        int maxAttempts = 3;
        Exception lastException = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            String uri = String.format("https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s", model, apiKey);

            try {
                if (attempt > 1) {
                    log.info("Retrying Gemini model '{}' (attempt {}/{}) for {}", model, attempt, maxAttempts, repoName);
                }
                String rawResponse = restClient.post()
                    .uri(uri)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);

                if (rawResponse == null || rawResponse.isBlank()) {
                    log.warn("Empty response received from Gemini API for {}", repoName);
                    return Collections.emptyList();
                }

                return parseGeminiResponse(rawResponse);

            } catch (Exception e) {
                lastException = e;
                String msg = e.getMessage() != null ? e.getMessage() : "";
                boolean isTransient = msg.contains("503") ||
                    msg.contains("429") ||
                    msg.contains("UNAVAILABLE") ||
                    msg.contains("high demand") ||
                    msg.contains("timed out") ||
                    msg.contains("Resource has been exhausted");

                if (attempt < maxAttempts && isTransient) {
                    long backoffMs = attempt * 2000L;
                    log.warn("Gemini API call returned transient error (attempt {}/{}). Retrying in {}ms: {}",
                        attempt, maxAttempts, backoffMs, msg);
                    try {
                        Thread.sleep(backoffMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                } else {
                    log.error("Failed to execute Gemini code review for {} on attempt {}/{}: {}", repoName, attempt, maxAttempts, msg);
                    break;
                }
            }
        }

        log.error("Failed to execute Gemini code review for {} after {} attempts: {}", repoName, maxAttempts, lastException != null ? lastException.getMessage() : "Unknown error");
        String cleanReason = formatCleanFailureReason(lastException, maxAttempts);
        throw new RuntimeException(cleanReason, lastException);
    }

    /**
     * Formats a clean, readable developer message explaining transient failures while confirming token preservation.
     */
    public String formatCleanFailureReason(Exception e, int attempts) {
        if (e == null) {
            return "Unable to complete review after " + attempts + " attempts. Please click Retry.";
        }
        String msg = e.getMessage() != null ? e.getMessage() : "";
        if (msg.contains("503") || msg.contains("UNAVAILABLE") || msg.contains("high demand")) {
            return "Google Gemini is currently experiencing peak traffic (503 Service Unavailable). Your tokens were preserved. Please wait a moment and click Retry.";
        }
        if (msg.contains("429") || msg.contains("Resource has been exhausted") || msg.contains("RESOURCE_EXHAUSTED")) {
            return "Google Gemini API rate limit reached (429 Quota Exceeded). Tokens were preserved. Please wait a moment before retrying.";
        }
        if (msg.contains("timed out") || msg.contains("SocketTimeoutException") || msg.contains("ConnectTimeoutException")) {
            return "Request to Google Gemini timed out after " + attempts + " attempts. Tokens were preserved. Please click Retry.";
        }
        return "Review could not be completed after " + attempts + " attempts. Please click Retry.";
    }

    /**
     * Builds a Spring AI Prompt composed of a SystemMessage persona and a parameterized UserMessage.
     */
    public Prompt createSpringAiPrompt(
        String repoName,
        String prTitle,
        List<RuleVersion> activeRules,
        List<GitHubFileDiffDto> diffFiles
    ) {
        // 1. Spring AI SystemMessage: establishes architect persona
        SystemMessage systemMessage = new SystemMessage(
            "You are Diffvera's Principal Software Architect and Security Auditor. " +
            "You perform deep architectural governance, security scrutiny, and correctness analysis on pull request diffs."
        );

        // 2. Spring AI UserMessage via PromptTemplate
        String userContent = buildUserPromptContent(repoName, prTitle, activeRules, diffFiles);
        UserMessage userMessage = new UserMessage(userContent);

        return new Prompt(List.of(systemMessage, userMessage));
    }

    /**
     * Parses the LLM JSON response using Spring AI's BeanOutputConverter.
     */
    List<GeminiFindingItem> parseGeminiResponse(String rawResponse) {
        try {
            JsonNode root = objectMapper.readTree(rawResponse);
            JsonNode candidate = root.path("candidates").path(0);
            String text = candidate.path("content").path("parts").path(0).path("text").asText();

            if (text.isBlank()) {
                log.warn("Gemini candidate text was empty.");
                return Collections.emptyList();
            }

            // Strip potential markdown wrapping if returned by model
            text = text.trim();
            if (text.startsWith("```json")) {
                text = text.substring(7);
            } else if (text.startsWith("```")) {
                text = text.substring(3);
            }
            if (text.endsWith("```")) {
                text = text.substring(0, text.length() - 3);
            }
            text = text.trim();

            // Use Spring AI's BeanOutputConverter for type-safe structured deserialization
            GeminiReviewResponse reviewResponse = outputConverter.convert(text);
            if (reviewResponse == null || reviewResponse.getFindings() == null) {
                return Collections.emptyList();
            }

            List<GeminiFindingItem> sanitizedFindings = new ArrayList<>();
            for (GeminiFindingItem item : reviewResponse.getFindings()) {
                sanitizeFinding(item);
                sanitizedFindings.add(item);
            }

            log.info("Spring AI OutputConverter extracted {} valid findings from review.", sanitizedFindings.size());
            return sanitizedFindings;

        } catch (Exception e) {
            log.error("Error converting Spring AI output to structured findings: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    private void sanitizeFinding(GeminiFindingItem item) {
        // Normalize category
        String cat = item.getCategory() != null ? item.getCategory().toLowerCase(Locale.ROOT).trim() : "correctness";
        if (!Set.of("architecture", "security", "correctness").contains(cat)) {
            cat = "correctness";
        }
        item.setCategory(cat);

        // Normalize severity
        String sev = item.getSeverity() != null ? item.getSeverity().toLowerCase(Locale.ROOT).trim() : "medium";
        if (!Set.of("high", "medium", "low").contains(sev)) {
            sev = "medium";
        }
        item.setSeverity(sev);

        // Ensure valid line numbers
        if (item.getLineStart() <= 0) {
            item.setLineStart(1);
        }
        if (item.getLineEnd() < item.getLineStart()) {
            item.setLineEnd(item.getLineStart());
        }

        if (item.getTitle() == null || item.getTitle().isBlank()) {
            item.setTitle("Potential code quality issue");
        }
        if (item.getSummary() == null || item.getSummary().isBlank()) {
            item.setSummary(item.getTitle());
        }
        if (item.getCodeExcerpt() == null) {
            item.setCodeExcerpt("");
        }
        if (item.getExplanation() == null) {
            item.setExplanation(item.getSummary());
        }
    }

    private String buildUserPromptContent(
        String repoName,
        String prTitle,
        List<RuleVersion> activeRules,
        List<GitHubFileDiffDto> diffFiles
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append("### Pull Request Context\n");
        sb.append("Repository: ").append(repoName).append("\n");
        sb.append("PR Title: ").append(prTitle != null ? prTitle : "Untitled PR").append("\n\n");

        sb.append("### Active Architecture Boundary Rules\n");
        if (activeRules == null || activeRules.isEmpty()) {
            sb.append("No custom architectural boundary rules defined for this repository. Review for general architecture best practices, security vulnerabilities, and correctness.\n\n");
        } else {
            sb.append("You MUST strictly enforce the following architectural boundaries:\n");
            for (RuleVersion rule : activeRules) {
                sb.append(String.format("- Rule: '%s' (v%d)\n  Boundary Constraint: %s\n", rule.getName(), rule.getVersionNumber(), rule.getBoundary()));
            }
            sb.append("\n");
        }

        sb.append("### Changed Files & Unified Diffs\n");
        List<GitHubFileDiffDto> docFiles = new ArrayList<>();
        List<GitHubFileDiffDto> codeFiles = new ArrayList<>();

        for (GitHubFileDiffDto file : diffFiles) {
            String nameLower = file.getFilename().toLowerCase(Locale.ROOT);
            if (nameLower.endsWith(".md") || nameLower.endsWith(".mdx") || nameLower.endsWith(".adoc")) {
                docFiles.add(file);
            } else {
                codeFiles.add(file);
            }
        }

        if (!docFiles.isEmpty()) {
            sb.append("#### Architectural & Design Documentation Files (.md)\n");
            sb.append("Carefully read these documentation files (e.g. ARCHITECTURE.md, README.md, ADRs, design specifications) to deeply understand the intended architecture, subsystem roles, and domain invariants:\n\n");
            for (GitHubFileDiffDto doc : docFiles) {
                sb.append(String.format("Documentation File: %s (Status: %s)\n```markdown\n%s\n```\n\n",
                    doc.getFilename(), doc.getStatus(), doc.getPatch()));
            }
        }

        sb.append("#### Source Code Changes\n");
        for (GitHubFileDiffDto file : codeFiles) {
            sb.append(String.format("File: %s (Status: %s, +%d, -%d)\n```diff\n%s\n```\n\n",
                file.getFilename(), file.getStatus(), file.getAdditions(), file.getDeletions(), file.getPatch()));
        }

        sb.append("### Review Guidelines\n");
        sb.append("1. Holistically evaluate the code against both the Active Architecture Boundary Rules and any specifications found in the Markdown documentation files (.md).\n");
        sb.append("2. Deeply analyze code comments, docstrings, Javadoc, and inline annotations (e.g. //, /* */, #, Javadoc tags, TODO/FIXME markers):\n");
        sb.append("   - Detect Code-Comment Drift: Identify cases where code comments state specific guarantees, invariants, or intended behavior (e.g. thread safety, null handling, transaction boundaries, performance expectations) that the implementation contradicts or neglects.\n");
        sb.append("   - Audit Acknowledged Technical Debt: Flag comments explicitly acknowledging architectural compromises or temporary bypasses (e.g. 'hack to bypass service layer', 'TODO: fix race condition') that break boundary invariants.\n");
        sb.append("3. Strictly identify:\n");
        sb.append("   - 'architecture': Violations of stated architecture boundary rules, discrepancies with documented architectural designs (ADRs/ARCHITECTURE.md), improper cross-layer coupling, leaky abstractions, or cyclic dependencies.\n");
        sb.append("   - 'security': OWASP vulnerabilities, injection flaws, unsafe deserialization, missing authorization, timing leaks, or insecure credentials.\n");
        sb.append("   - 'correctness': Logic bugs, race conditions, unhandled nil/null references, resource leaks, or broken contract invariants.\n");
        sb.append("4. DO NOT comment on cosmetic styling, formatting, linting, spelling, or personal preferences.\n");
        sb.append("5. Line numbers ('lineStart' and 'lineEnd') must be exact 1-based line numbers directly present in the diff.\n");
        sb.append("6. Output MUST strictly follow this JSON schema:\n");
        sb.append("{\n");
        sb.append("  \"findings\": [\n");
        sb.append("    {\n");
        sb.append("      \"category\": \"architecture\" | \"security\" | \"correctness\",\n");
        sb.append("      \"severity\": \"high\" | \"medium\" | \"low\",\n");
        sb.append("      \"title\": \"Concise headline (max 10 words)\",\n");
        sb.append("      \"summary\": \"One-sentence high-level summary\",\n");
        sb.append("      \"filePath\": \"exact file path\",\n");
        sb.append("      \"lineStart\": 10,\n");
        sb.append("      \"lineEnd\": 14,\n");
        sb.append("      \"codeExcerpt\": \"Relevant code snippet from the diff\",\n");
        sb.append("      \"explanation\": \"Detailed technical explanation of why this violates boundaries or introduces risk\",\n");
        sb.append("      \"suggestedCorrection\": \"Concrete, copy-pasteable replacement code or architectural refactoring recommendation\",\n");
        sb.append("      \"ruleName\": \"Exact matching rule name from the active rules above, or null\"\n");
        sb.append("    }\n");
        sb.append("  ]\n");
        sb.append("}\n");
        sb.append("If there are no architectural, security, or correctness issues, return: {\"findings\": []}\n");

        return sb.toString();
    }
}
