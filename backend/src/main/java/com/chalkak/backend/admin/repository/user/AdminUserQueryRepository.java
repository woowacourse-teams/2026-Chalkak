package com.chalkak.backend.admin.repository.user;

import com.chalkak.backend.admin.repository.AdminPage;
import java.util.Optional;
import java.util.UUID;

public interface AdminUserQueryRepository {

    AdminPage<AdminUserSummaryProjection> findUsers(
            AdminUserQueryCriteria criteria,
            int page,
            int pageSize
    );

    Optional<AdminUserDetailProjection> findUserById(UUID userId);
}
