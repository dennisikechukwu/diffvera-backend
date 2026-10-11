package me.dennislab.diffvera.review.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A single rendered line within a file diff.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Line of source code within a diff")
public class CodeLineDto {

    @Schema(description = "1-based line number", example = "27")
    private int number;

    @Schema(description = "Text content of the line", example = "  return db.order.findFirst({")
    private String text;

    @Schema(description = "Diff change kind: 'added', 'removed', or null for unchanged context", example = "added")
    private String kind;
}
