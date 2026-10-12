package com.chalkak.backend.admin.api.v1.controller;

import com.chalkak.backend.admin.api.support.AuthenticatedAdmin;
import com.chalkak.backend.admin.api.support.CurrentAdmin;
import com.chalkak.backend.admin.api.v1.docs.AdminNoticeApiDocs;
import com.chalkak.backend.admin.api.v1.dto.request.AdminNoticeRequest;
import com.chalkak.backend.admin.api.v1.dto.response.AdminNoticeResponse;
import com.chalkak.backend.admin.service.notification.AdminNoticeResult;
import com.chalkak.backend.admin.service.notification.AdminNoticeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/notices")
public class AdminNoticeController implements AdminNoticeApiDocs {

    private final AdminNoticeService noticeService;

    @Override
    @PostMapping
    public ResponseEntity<AdminNoticeResponse> createNotice(
            @CurrentAdmin AuthenticatedAdmin authenticatedAdmin,
            @Valid @RequestBody AdminNoticeRequest request
    ) {
        AdminNoticeResult result = noticeService.createNotice(authenticatedAdmin.adminId(),
                request.title(), request.body(), request.scheduledAt());
        return ResponseEntity.status(HttpStatus.CREATED).body(AdminNoticeResponse.from(result));
    }
}
