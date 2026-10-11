package me.dennislab.diffvera.diff;

import lombok.extern.slf4j.Slf4j;
import me.dennislab.diffvera.diff.dto.GitHubFileDiffDto;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Service that filters and sanitizes file diffs before presenting them
 * to the AI review pipeline, removing noise, lockfiles, binaries, and minified bundles.
 */
@Service
@Slf4j
public class DiffSanitizerService {

    public static final int MAX_REVIEWABLE_FILES = 25;
    public static final int MAX_PATCH_LENGTH = 12_000; // ~3000 tokens per file max

    private static final Set<String> IGNORED_EXACT_FILENAMES = Set.of(
        "package-lock.json",
        "yarn.lock",
        "pnpm-lock.yaml",
        "cargo.lock",
        "go.sum",
        "composer.lock",
        "poetry.lock",
        "pipfile.lock",
        "gemfile.lock"
    );

    private static final Set<String> IGNORED_EXTENSIONS = Set.of(
        ".png", ".jpg", ".jpeg", ".gif", ".svg", ".ico", ".webp",
        ".pdf", ".zip", ".tar", ".gz", ".jar", ".war", ".class",
        ".exe", ".bin", ".woff", ".woff2", ".ttf", ".eot",
        ".mp4", ".mp3", ".mov", ".wav"
    );

    private static final List<String> IGNORED_PATH_PREFIXES = List.of(
        ".git/",
        "node_modules/",
        "dist/",
        "build/",
        "target/",
        ".next/",
        ".nuxt/",
        ".output/"
    );

    private static final Pattern MINIFIED_PATTERN = Pattern.compile(".*\\.(min\\.js|min\\.css|map|bundle\\.js)$", Pattern.CASE_INSENSITIVE);

    /**
     * Determines whether a specific filename is eligible for AI architectural and correctness review.
     */
    public boolean isReviewable(String filename) {
        if (filename == null || filename.isBlank()) {
            return false;
        }

        String normalized = filename.trim().replace('\\', '/');
        String lower = normalized.toLowerCase(Locale.ROOT);

        // 1. Check directory prefixes
        for (String prefix : IGNORED_PATH_PREFIXES) {
            if (lower.startsWith(prefix) || lower.contains("/" + prefix)) {
                return false;
            }
        }

        // 2. Check exact lockfiles
        String simpleName = lower.substring(lower.lastIndexOf('/') + 1);
        if (IGNORED_EXACT_FILENAMES.contains(simpleName)) {
            return false;
        }

        // 3. Check minified bundles
        if (MINIFIED_PATTERN.matcher(simpleName).matches()) {
            return false;
        }

        // 4. Check binary extensions
        for (String ext : IGNORED_EXTENSIONS) {
            if (lower.endsWith(ext)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Filters a list of GitHub file diffs, removing non-reviewable files,
     * truncating oversized patches, and capping at the maximum reviewable count.
     */
    public List<GitHubFileDiffDto> sanitizeAndFilter(List<GitHubFileDiffDto> files) {
        if (files == null || files.isEmpty()) {
            return Collections.emptyList();
        }

        return files.stream()
            .filter(f -> isReviewable(f.getFilename()))
            .filter(f -> f.getPatch() != null && !f.getPatch().isBlank())
            .filter(f -> !"deleted".equalsIgnoreCase(f.getStatus()))
            .limit(MAX_REVIEWABLE_FILES)
            .map(this::truncatePatchIfNeeded)
            .collect(Collectors.toList());
    }

    private GitHubFileDiffDto truncatePatchIfNeeded(GitHubFileDiffDto fileDiff) {
        String patch = fileDiff.getPatch();
        if (patch != null && patch.length() > MAX_PATCH_LENGTH) {
            log.info("Truncating patch for file {} from {} chars to {} chars", fileDiff.getFilename(), patch.length(), MAX_PATCH_LENGTH);
            String truncated = patch.substring(0, MAX_PATCH_LENGTH) + "\n\n... [Diff truncated to preserve context window] ...";
            return GitHubFileDiffDto.builder()
                .sha(fileDiff.getSha())
                .filename(fileDiff.getFilename())
                .status(fileDiff.getStatus())
                .additions(fileDiff.getAdditions())
                .deletions(fileDiff.getDeletions())
                .changes(fileDiff.getChanges())
                .patch(truncated)
                .build();
        }
        return fileDiff;
    }
}
