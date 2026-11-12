package com.chalkak.backend.filter.api.v1.dto.response;

import com.chalkak.backend.filter.domain.FilterStatus;
import com.chalkak.backend.filter.service.AuthorFilterListResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AuthorFilterListResponse(
        int currentPage,
        int pageSize,
        boolean hasNext,
        List<FilterSummaryResponse> filters
) {

    public static AuthorFilterListResponse fromResult(AuthorFilterListResult result) {
        return new AuthorFilterListResponse(
                result.currentPage(),
                result.pageSize(),
                result.hasNext(),
                result.filters().stream().map(FilterSummaryResponse::fromSummary).toList()
        );
    }

    public record FilterSummaryResponse(
            UUID filterId,
            @Schema(nullable = true)
            String name,
            FilterStatus status,
            Instant createdAt
    ) {

        private static FilterSummaryResponse fromSummary(AuthorFilterListResult.Summary summary) {
            return new FilterSummaryResponse(
                    summary.filterId(),
                    summary.name(),
                    summary.status(),
                    summary.createdAt()
            );
        }
    }
}
