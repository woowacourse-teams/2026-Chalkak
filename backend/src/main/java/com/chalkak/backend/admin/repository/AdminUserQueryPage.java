package com.chalkak.backend.admin.repository;

import java.util.List;

public record AdminUserQueryPage(
        List<AdminUserSummaryProjection> users,
        int currentPage,
        int pageSize,
        boolean hasNext
) {
}
