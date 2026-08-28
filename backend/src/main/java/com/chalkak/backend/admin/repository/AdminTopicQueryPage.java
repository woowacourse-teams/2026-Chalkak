package com.chalkak.backend.admin.repository;

import com.chalkak.backend.topic.domain.Topic;
import java.util.List;

public record AdminTopicQueryPage(
        List<Topic> topics,
        int currentPage,
        int pageSize,
        boolean hasNext
) {
}
