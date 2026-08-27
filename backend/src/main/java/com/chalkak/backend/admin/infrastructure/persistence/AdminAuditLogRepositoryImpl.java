package com.chalkak.backend.admin.infrastructure.persistence;

import com.chalkak.backend.admin.domain.AdminAuditLog;
import com.chalkak.backend.admin.repository.AdminAuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class AdminAuditLogRepositoryImpl implements AdminAuditLogRepository {

    private final AdminAuditLogJpaRepository adminAuditLogJpaRepository;

    @Override
    public AdminAuditLog save(AdminAuditLog adminAuditLog) {
        return adminAuditLogJpaRepository.save(adminAuditLog);
    }
}
