package com.chalkak.backend.admin.repository.topic;

import com.chalkak.backend.admin.repository.AdminPage;
import java.util.Optional;
import java.util.UUID;

public interface AdminTopicQueryRepository {

    AdminPage<AdminTopicProjection> findTopics(
            AdminTopicQueryCriteria criteria,
            int page,
            int pageSize
    );

    Optional<AdminTopicProjection> findActiveTopicById(UUID topicId);
}
