package com.chalkak.backend.admin.service;

import com.chalkak.backend.admin.domain.AdminAction;
import com.chalkak.backend.admin.domain.AdminTargetType;
import com.chalkak.backend.admin.repository.AdminAuditLogQueryCriteria;
import com.chalkak.backend.admin.repository.AdminAuditLogQueryRepository;
import com.chalkak.backend.admin.repository.AdminAuditLogQuerySort;
import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminAuditLogQueryService {

    private final AdminAuditLogQueryRepository repository;

    public AdminAuditLogListResult getAuditLogs(
            UUID adminId,
            AdminAction action,
            AdminTargetType targetType,
            UUID targetId,
            Instant occurredFrom,
            Instant occurredTo,
            AdminAuditLogSort sort,
            int page,
            int pageSize
    ) {
        validatePagination(page, pageSize);
        validateOccurredAtRange(occurredFrom, occurredTo);
        AdminAuditLogQueryCriteria criteria = new AdminAuditLogQueryCriteria(
                adminId, action, targetType, targetId, occurredFrom, occurredTo,
                sort == AdminAuditLogSort.OCCURRED_AT_ASC
                        ? AdminAuditLogQuerySort.OCCURRED_AT_ASC
                        : AdminAuditLogQuerySort.OCCURRED_AT_DESC
        );
        return AdminAuditLogListResult.from(repository.findAuditLogs(criteria, page, pageSize));
    }

    private void validateOccurredAtRange(Instant from, Instant to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "감사 로그 발생 시각 범위가 올바르지 않습니다.");
        }
    }

    private void validatePagination(int page, int pageSize) {
        long offset = ((long) page - 1) * pageSize;
        if (page < 1 || pageSize < 1 || pageSize > 100 || offset > Integer.MAX_VALUE) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "조회 조건이 올바르지 않습니다.");
        }
    }
}
