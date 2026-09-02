package com.chalkak.backend.admin.repository;

import java.util.List;

public record AdminTopicQueryPage(
        List<AdminTopicProjection> topics,
        int currentPage,
        int pageSize,
        boolean hasNext
) {
}
