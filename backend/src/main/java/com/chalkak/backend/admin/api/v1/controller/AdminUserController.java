package com.chalkak.backend.admin.api.v1.controller;

import com.chalkak.backend.admin.api.support.AuthenticatedAdmin;
import com.chalkak.backend.admin.api.support.CurrentAdmin;
import com.chalkak.backend.admin.api.v1.docs.AdminUserApiDocs;
import com.chalkak.backend.admin.api.v1.dto.request.AdminUserListRequest;
import com.chalkak.backend.admin.api.v1.dto.request.AdminUserStatusUpdateRequest;
import com.chalkak.backend.admin.api.v1.dto.response.AdminUserDetailResponse;
import com.chalkak.backend.admin.api.v1.dto.response.AdminUserListResponse;
import com.chalkak.backend.admin.api.v1.dto.response.AdminUserStatusResponse;
import com.chalkak.backend.admin.service.AdminUserDetail;
import com.chalkak.backend.admin.service.AdminUserListResult;
import com.chalkak.backend.admin.service.AdminUserQueryService;
import com.chalkak.backend.admin.service.AdminUserStatusResult;
import com.chalkak.backend.admin.service.AdminUserStatusService;
import com.chalkak.backend.common.util.CanonicalUuidParser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/users")
public class AdminUserController implements AdminUserApiDocs {

    private final AdminUserQueryService adminUserQueryService;
    private final AdminUserStatusService adminUserStatusService;

    @Override
    @GetMapping
    public ResponseEntity<AdminUserListResponse> getUsers(
            @CurrentAdmin AuthenticatedAdmin authenticatedAdmin,
            @Valid @ModelAttribute AdminUserListRequest request
    ) {
        AdminUserListResult result = adminUserQueryService.getUsers(
                request.status(),
                request.email(),
                request.sort(),
                request.page(),
                request.pageSize());
        return ResponseEntity.ok(AdminUserListResponse.from(result));
    }

    @Override
    @GetMapping("/{userId}")
    public ResponseEntity<AdminUserDetailResponse> getUser(
            @CurrentAdmin AuthenticatedAdmin authenticatedAdmin,
            @PathVariable String userId
    ) {
        AdminUserDetail result = adminUserQueryService.getUser(
                CanonicalUuidParser.parse(userId));
        return ResponseEntity.ok(AdminUserDetailResponse.from(result));
    }

    @Override
    @PatchMapping("/{userId}/status")
    public ResponseEntity<AdminUserStatusResponse> updateStatus(
            @CurrentAdmin AuthenticatedAdmin authenticatedAdmin,
            @PathVariable String userId,
            @Valid @RequestBody AdminUserStatusUpdateRequest request
    ) {
        AdminUserStatusResult result = adminUserStatusService.updateStatus(
                CanonicalUuidParser.parse(userId),
                authenticatedAdmin.adminId(),
                request.status(),
                request.reason()
        );
        return ResponseEntity.ok(AdminUserStatusResponse.from(result));
    }
}
