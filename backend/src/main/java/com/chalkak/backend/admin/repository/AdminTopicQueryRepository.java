package com.chalkak.backend.admin.repository;

public interface AdminTopicQueryRepository {

    AdminTopicQueryPage findTopics(
            AdminTopicQueryCriteria criteria,
            int page,
            int pageSize
    );
}
