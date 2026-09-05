package com.chalkak.backend.admin.api.v1.controller;

import com.chalkak.backend.admin.api.support.AuthenticatedAdmin;
import com.chalkak.backend.admin.api.support.CurrentAdmin;
import com.chalkak.backend.admin.api.v1.docs.AdminAuditLogApiDocs;
import com.chalkak.backend.admin.api.v1.dto.request.AdminAuditLogListRequest;
import com.chalkak.backend.admin.api.v1.dto.response.AdminAuditLogListResponse;
import com.chalkak.backend.admin.service.AdminAuditLogListResult;
import com.chalkak.backend.admin.service.AdminAuditLogQueryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/audit-logs")
public class AdminAuditLogController implements AdminAuditLogApiDocs {

    private final AdminAuditLogQueryService service;

    @Override
    @GetMapping
    public ResponseEntity<AdminAuditLogListResponse> getAuditLogs(
            @CurrentAdmin AuthenticatedAdmin authenticatedAdmin,
            @Valid @ModelAttribute AdminAuditLogListRequest request
    ) {
        AdminAuditLogListResult result = service.getAuditLogs(
                request.adminId(), request.action(), request.targetType(), request.targetId(),
                request.occurredFrom(), request.occurredTo(), request.sort(),
                request.page(), request.pageSize()
        );
        return ResponseEntity.ok(AdminAuditLogListResponse.from(result));
    }
}
