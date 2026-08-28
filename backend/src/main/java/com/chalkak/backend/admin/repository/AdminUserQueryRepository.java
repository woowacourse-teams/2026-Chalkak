package com.chalkak.backend.admin.repository;

import java.util.Optional;
import java.util.UUID;

public interface AdminUserQueryRepository {

    AdminUserQueryPage findUsers(
            AdminUserQueryCriteria criteria,
            int page,
            int pageSize
    );

    Optional<AdminUserDetailProjection> findUserById(UUID userId);
}
