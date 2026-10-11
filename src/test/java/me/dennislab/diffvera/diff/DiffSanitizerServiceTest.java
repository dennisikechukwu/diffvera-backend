package me.dennislab.diffvera.diff;

import me.dennislab.diffvera.diff.dto.GitHubFileDiffDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DiffSanitizerServiceTest {

    private DiffSanitizerService sanitizerService;

    @BeforeEach
    void setUp() {
        sanitizerService = new DiffSanitizerService();
    }

    @Test
    @DisplayName("isReviewable should accept code and markdown documentation files")
    void shouldAcceptCodeAndMarkdownFiles() {
        assertThat(sanitizerService.isReviewable("src/main/java/OrderService.java")).isTrue();
        assertThat(sanitizerService.isReviewable("frontend/app.vue")).isTrue();
        assertThat(sanitizerService.isReviewable("lib/utils.ts")).isTrue();
        assertThat(sanitizerService.isReviewable("README.md")).isTrue();
        assertThat(sanitizerService.isReviewable("ARCHITECTURE.md")).isTrue();
        assertThat(sanitizerService.isReviewable("docs/adr/001-bounded-context.md")).isTrue();
    }

    @Test
    @DisplayName("isReviewable should reject lockfiles, binaries, minified bundles and build directories")
    void shouldRejectNoiseAndBinaries() {
        // Lockfiles
        assertThat(sanitizerService.isReviewable("package-lock.json")).isFalse();
        assertThat(sanitizerService.isReviewable("yarn.lock")).isFalse();
        assertThat(sanitizerService.isReviewable("pnpm-lock.yaml")).isFalse();
        assertThat(sanitizerService.isReviewable("Cargo.lock")).isFalse();
        assertThat(sanitizerService.isReviewable("go.sum")).isFalse();

        // Binaries
        assertThat(sanitizerService.isReviewable("assets/logo.png")).isFalse();
        assertThat(sanitizerService.isReviewable("favicon.ico")).isFalse();
        assertThat(sanitizerService.isReviewable("manual.pdf")).isFalse();
        assertThat(sanitizerService.isReviewable("app.jar")).isFalse();

        // Minified
        assertThat(sanitizerService.isReviewable("dist/bundle.min.js")).isFalse();
        assertThat(sanitizerService.isReviewable("styles.min.css")).isFalse();

        // Directory prefixes
        assertThat(sanitizerService.isReviewable(".git/config")).isFalse();
        assertThat(sanitizerService.isReviewable("node_modules/axios/index.js")).isFalse();
        assertThat(sanitizerService.isReviewable("target/classes/App.class")).isFalse();
    }

    @Test
    @DisplayName("sanitizeAndFilter should filter out non-reviewables and retain reviewable files")
    void shouldSanitizeAndFilterList() {
        List<GitHubFileDiffDto> input = List.of(
            GitHubFileDiffDto.builder().filename("src/OrderService.java").status("modified").patch("@@ -1,5 +1,6 @@\n+import java.util.*;").build(),
            GitHubFileDiffDto.builder().filename("package-lock.json").status("modified").patch("@@ -10,3 +10,3 @@\n-1.0.0\n+1.0.1").build(),
            GitHubFileDiffDto.builder().filename("ARCHITECTURE.md").status("modified").patch("@@ -1,2 +1,3 @@\n+# Architecture Guideline").build(),
            GitHubFileDiffDto.builder().filename("assets/diagram.png").status("added").patch("binary").build(),
            GitHubFileDiffDto.builder().filename("old-file.txt").status("deleted").patch("@@ -1,5 +0,0 @@").build()
        );

        List<GitHubFileDiffDto> result = sanitizerService.sanitizeAndFilter(input);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(GitHubFileDiffDto::getFilename)
            .containsExactly("src/OrderService.java", "ARCHITECTURE.md");
    }

    @Test
    @DisplayName("sanitizeAndFilter should truncate patches exceeding MAX_PATCH_LENGTH")
    void shouldTruncateOversizedPatches() {
        String hugePatch = "line\n".repeat(3000); // ~15,000 characters

        GitHubFileDiffDto file = GitHubFileDiffDto.builder()
            .filename("src/MassiveFile.java")
            .status("modified")
            .patch(hugePatch)
            .build();

        List<GitHubFileDiffDto> result = sanitizerService.sanitizeAndFilter(List.of(file));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getPatch()).contains("... [Diff truncated to preserve context window] ...");
        assertThat(result.get(0).getPatch().length()).isLessThan(hugePatch.length());
    }

    @Test
    @DisplayName("sanitizeAndFilter should cap file list at MAX_REVIEWABLE_FILES")
    void shouldCapFileListAtMaxLimit() {
        List<GitHubFileDiffDto> files = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            files.add(GitHubFileDiffDto.builder()
                .filename("src/File" + i + ".java")
                .status("modified")
                .patch("@@ -1 +1 @@\n+test" + i)
                .build());
        }

        List<GitHubFileDiffDto> result = sanitizerService.sanitizeAndFilter(files);
        assertThat(result).hasSize(DiffSanitizerService.MAX_REVIEWABLE_FILES);
    }
}
