package com.chalkak.backend.admin.repository;

import java.util.List;

public record AdminPostQueryPage(
        List<AdminPostSummaryProjection> posts,
        int currentPage,
        int pageSize,
        boolean hasNext
) {
}
