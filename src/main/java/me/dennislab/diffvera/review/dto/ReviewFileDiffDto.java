package me.dennislab.diffvera.review.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * File changed in a pull request with parsed diff lines.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Changed file with line-by-line diff content")
public class ReviewFileDiffDto {

    @Schema(description = "Relative path of the changed file", example = "src/services/orders.ts")
    private String path;

    @Builder.Default
    @Schema(description = "Lines in the diff excerpt")
    private List<CodeLineDto> lines = new ArrayList<>();
}
