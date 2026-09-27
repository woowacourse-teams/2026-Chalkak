package com.chalkak.backend.admin.infrastructure.persistence.audit;

import com.chalkak.backend.admin.domain.AdminAuditLog;
import com.chalkak.backend.admin.repository.AdminPage;
import com.chalkak.backend.admin.repository.audit.AdminAuditLogQueryCriteria;
import com.chalkak.backend.admin.repository.audit.AdminAuditLogRepository;
import com.chalkak.backend.admin.repository.audit.AdminAuditLogSort;
import com.chalkak.backend.admin.repository.audit.AdminAuditLogSummaryProjection;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class AdminAuditLogRepositoryImpl implements AdminAuditLogRepository {

    private final AdminAuditLogJpaRepository repository;

    @Override
    public AdminAuditLog save(AdminAuditLog adminAuditLog) {
        return repository.save(adminAuditLog);
    }

    @Override
    public AdminPage<AdminAuditLogSummaryProjection> findAuditLogs(
            AdminAuditLogQueryCriteria criteria,
            int page,
            int pageSize
    ) {
        PageRequest pageable = PageRequest.of(page - 1, pageSize, toSort(criteria.sort()));
        Slice<AdminAuditLogSummaryProjection> result = repository.findAuditLogs(criteria, pageable);
        return new AdminPage<>(result.getContent(), page, pageSize, result.hasNext());
    }

    private Sort toSort(AdminAuditLogSort sort) {
        if (sort == AdminAuditLogSort.OCCURRED_AT_ASC) {
            return Sort.by(Sort.Direction.ASC, "occurredAt", "id");
        }
        return Sort.by(Sort.Direction.DESC, "occurredAt", "id");
    }
}
