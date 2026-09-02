package com.chalkak.backend.admin.repository;

import java.util.Optional;
import java.util.UUID;

public interface AdminPostQueryRepository {

    AdminPostQueryPage findPosts(
            AdminPostQueryCriteria criteria,
            int page,
            int pageSize
    );

    Optional<AdminPostDetailProjection> findPostById(UUID postId);
}
