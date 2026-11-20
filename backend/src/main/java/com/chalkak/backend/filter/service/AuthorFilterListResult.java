package com.chalkak.backend.filter.service;

import com.chalkak.backend.filter.domain.Filter;
import com.chalkak.backend.filter.domain.FilterStatus;
import com.chalkak.backend.filter.repository.FilterSlice;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AuthorFilterListResult(
        int currentPage,
        int pageSize,
        boolean hasNext,
        List<Summary> filters
) {

    public static AuthorFilterListResult fromSlice(
            FilterSlice slice,
            int page,
            int pageSize
    ) {
        return new AuthorFilterListResult(
                page,
                pageSize,
                slice.hasNext(),
                slice.filters().stream().map(Summary::fromFilter).toList()
        );
    }

    public record Summary(
            UUID filterId,
            String name,
            FilterStatus status,
            Instant createdAt
    ) {

        private static Summary fromFilter(Filter filter) {
            return new Summary(
                    filter.getId(),
                    filter.getName(),
                    filter.getStatus(),
                    filter.getCreatedAt()
            );
        }
    }
}
