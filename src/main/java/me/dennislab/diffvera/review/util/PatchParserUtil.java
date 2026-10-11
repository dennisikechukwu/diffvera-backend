package me.dennislab.diffvera.review.util;

import me.dennislab.diffvera.diff.dto.GitHubFileDiffDto;
import me.dennislab.diffvera.review.dto.CodeLineDto;
import me.dennislab.diffvera.review.dto.ReviewFileDiffDto;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility for parsing git unified diff patches into structured CodeLine lists
 * suitable for the frontend diff review viewer.
 */
public final class PatchParserUtil {

    private static final Pattern HUNK_HEADER_PATTERN = Pattern.compile("^@@\\s+-\\d+(?:,\\d+)?\\s+\\+(\\d+)(?:,\\d+)?\\s+@@");

    private PatchParserUtil() {
    }

    /**
     * Converts a list of raw GitHub file diffs into structured ReviewFileDiffDto objects.
     */
    public static List<ReviewFileDiffDto> parseDiffs(List<GitHubFileDiffDto> diffFiles) {
        if (diffFiles == null) {
            return List.of();
        }

        List<ReviewFileDiffDto> result = new ArrayList<>();
        for (GitHubFileDiffDto diffFile : diffFiles) {
            List<CodeLineDto> lines = parsePatch(diffFile.getPatch());
            result.add(ReviewFileDiffDto.builder()
                .path(diffFile.getFilename())
                .lines(lines)
                .build());
        }
        return result;
    }

    /**
     * Parses an individual unified diff patch string into CodeLineDto objects.
     */
    public static List<CodeLineDto> parsePatch(String patch) {
        if (patch == null || patch.isBlank()) {
            return List.of();
        }

        List<CodeLineDto> codeLines = new ArrayList<>();
        String[] rawLines = patch.split("\r?\n");
        int currentLineNumber = 1;

        for (String rawLine : rawLines) {
            if (rawLine == null || rawLine.isEmpty()) {
                continue;
            }

            Matcher hunkMatcher = HUNK_HEADER_PATTERN.matcher(rawLine);
            if (hunkMatcher.find()) {
                try {
                    currentLineNumber = Integer.parseInt(hunkMatcher.group(1));
                } catch (NumberFormatException ignored) {
                    // Fall back to current
                }
                continue;
            }

            if (rawLine.startsWith("\\")) {
                // Ignore "\ No newline at end of file"
                continue;
            }

            char marker = rawLine.charAt(0);
            String text = rawLine.length() > 1 ? rawLine.substring(1) : "";

            if (marker == '+') {
                codeLines.add(CodeLineDto.builder()
                    .number(currentLineNumber++)
                    .text(text)
                    .kind("added")
                    .build());
            } else if (marker == '-') {
                codeLines.add(CodeLineDto.builder()
                    .number(currentLineNumber)
                    .text(text)
                    .kind("removed")
                    .build());
            } else {
                // Context line (leading space or unmodified)
                codeLines.add(CodeLineDto.builder()
                    .number(currentLineNumber++)
                    .text(marker == ' ' ? text : rawLine)
                    .kind(null)
                    .build());
            }
        }

        return codeLines;
    }
}
