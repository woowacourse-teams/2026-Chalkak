package com.chalkak.backend.admin.repository.post;

import com.chalkak.backend.admin.repository.AdminPage;
import java.util.Optional;
import java.util.UUID;

public interface AdminPostQueryRepository {

    AdminPage<AdminPostSummaryProjection> findPosts(
            AdminPostQueryCriteria criteria,
            int page,
            int pageSize
    );

    Optional<AdminPostDetailProjection> findPostById(UUID postId);
}
