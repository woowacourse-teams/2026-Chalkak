package com.chalkak.backend.admin.infrastructure.persistence.user;

import com.chalkak.backend.admin.repository.AdminPage;
import com.chalkak.backend.admin.repository.user.AdminUserDetailProjection;
import com.chalkak.backend.admin.repository.user.AdminUserQueryCriteria;
import com.chalkak.backend.admin.repository.user.AdminUserQueryRepository;
import com.chalkak.backend.admin.repository.user.AdminUserSort;
import com.chalkak.backend.admin.repository.user.AdminUserSummaryProjection;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class AdminUserQueryRepositoryImpl implements AdminUserQueryRepository {

    private final AdminUserJpaRepository repository;

    @Override
    public AdminPage<AdminUserSummaryProjection> findUsers(
            AdminUserQueryCriteria criteria,
            int page,
            int pageSize
    ) {
        PageRequest pageable = PageRequest.of(page - 1, pageSize, toSort(criteria.sort()));
        Slice<AdminUserSummaryProjection> result = repository.findUsers(criteria, pageable);
        return new AdminPage<>(result.getContent(), page, pageSize, result.hasNext());
    }

    @Override
    public Optional<AdminUserDetailProjection> findUserById(UUID id) {
        return repository.findUserDetail(id, PageRequest.of(0, 1)).stream().findFirst();
    }

    private Sort toSort(AdminUserSort sort) {
        if (sort == AdminUserSort.CREATED_AT_ASC) {
            return Sort.by(Sort.Direction.ASC, "createdAt", "id");
        }
        return Sort.by(Sort.Direction.DESC, "createdAt", "id");
    }
}
