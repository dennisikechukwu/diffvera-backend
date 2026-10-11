package me.dennislab.diffvera.review;

import me.dennislab.diffvera.diff.dto.GitHubFileDiffDto;
import me.dennislab.diffvera.review.dto.CodeLineDto;
import me.dennislab.diffvera.review.dto.ReviewFileDiffDto;
import me.dennislab.diffvera.review.util.PatchParserUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PatchParserUtilTest {

    @Test
    @DisplayName("parsePatch should parse hunk header and classify added, removed and context lines")
    void shouldParsePatchCorrectly() {
        String patch = """
            @@ -24,4 +24,5 @@
             export async function getOrder(
            -  return db.order.findUnique();
            +  return db.order.findFirst();
            +  // extra check
             }
            """;

        List<CodeLineDto> lines = PatchParserUtil.parsePatch(patch);

        assertThat(lines).hasSize(5);

        // Line 0: context line
        assertThat(lines.get(0).getNumber()).isEqualTo(24);
        assertThat(lines.get(0).getText()).isEqualTo("export async function getOrder(");
        assertThat(lines.get(0).getKind()).isNull();

        // Line 1: removed line
        assertThat(lines.get(1).getNumber()).isEqualTo(25);
        assertThat(lines.get(1).getText()).isEqualTo("  return db.order.findUnique();");
        assertThat(lines.get(1).getKind()).isEqualTo("removed");

        // Line 2: added line
        assertThat(lines.get(2).getNumber()).isEqualTo(25);
        assertThat(lines.get(2).getText()).isEqualTo("  return db.order.findFirst();");
        assertThat(lines.get(2).getKind()).isEqualTo("added");

        // Line 3: added line
        assertThat(lines.get(3).getNumber()).isEqualTo(26);
        assertThat(lines.get(3).getText()).isEqualTo("  // extra check");
        assertThat(lines.get(3).getKind()).isEqualTo("added");

        // Line 4: context line
        assertThat(lines.get(4).getNumber()).isEqualTo(27);
        assertThat(lines.get(4).getText()).isEqualTo("}");
        assertThat(lines.get(4).getKind()).isNull();
    }

    @Test
    @DisplayName("parseDiffs should convert file diffs into review file diffs")
    void shouldParseMultipleFileDiffs() {
        GitHubFileDiffDto fileDiff = GitHubFileDiffDto.builder()
            .filename("src/app.ts")
            .patch("@@ -1,2 +1,2 @@\n-const a = 1;\n+const a = 2;\n")
            .build();

        List<ReviewFileDiffDto> result = PatchParserUtil.parseDiffs(List.of(fileDiff));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getPath()).isEqualTo("src/app.ts");
        assertThat(result.get(0).getLines()).hasSize(2);
    }
}
