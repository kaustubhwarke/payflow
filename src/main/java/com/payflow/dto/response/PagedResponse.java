package com.payflow.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Stable, transport-friendly pagination envelope (Rule 16 / mentor feedback 22.b).
 *
 * <p>Wrapping {@link Page} in an explicit DTO avoids leaking Spring's internal
 * {@code PageImpl} JSON structure (which is not part of a guaranteed contract) into the API.</p>
 *
 * @param <T> the element type
 */
@Schema(name = "PagedResponse", description = "A page of results with pagination metadata")
public record PagedResponse<T>(

        @Schema(description = "Items on the current page")
        List<T> content,

        @Schema(example = "0", description = "Zero-based page index")
        int page,

        @Schema(example = "20", description = "Requested page size")
        int size,

        @Schema(example = "137", description = "Total elements across all pages")
        long totalElements,

        @Schema(example = "7", description = "Total number of pages")
        int totalPages,

        @Schema(example = "false", description = "Whether this is the last page")
        boolean last
) {

    /**
     * Adapts a Spring Data {@link Page} into a {@code PagedResponse}.
     *
     * @param page the source page
     * @param <T>  the element type
     * @return the populated envelope
     */
    public static <T> PagedResponse<T> from(Page<T> page) {
        return new PagedResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isLast()
        );
    }
}
