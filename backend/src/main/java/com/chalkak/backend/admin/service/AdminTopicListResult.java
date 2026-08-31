package com.chalkak.backend.admin.service;

import java.util.List;

public record AdminTopicListResult(
        int currentPage,
        int pageSize,
        boolean hasNext,
        List<AdminTopicDetail> topics
) {
}
