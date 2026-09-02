package com.chalkak.backend.admin.repository;

import java.util.Optional;
import java.util.UUID;

public interface AdminTopicQueryRepository {

    AdminTopicQueryPage findTopics(
            AdminTopicQueryCriteria criteria,
            int page,
            int pageSize
    );

    Optional<AdminTopicProjection> findActiveTopicById(UUID topicId);
}
