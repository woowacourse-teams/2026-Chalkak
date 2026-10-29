package com.chalkak.backend.admin.api.v1.controller;

import com.chalkak.backend.admin.api.support.AuthenticatedAdmin;
import com.chalkak.backend.admin.api.support.CurrentAdmin;
import com.chalkak.backend.admin.api.v1.docs.AdminAppVersionApiDocs;
import com.chalkak.backend.admin.api.v1.dto.request.AdminAppVersionRequest;
import com.chalkak.backend.admin.api.v1.dto.response.AdminAppVersionListResponse;
import com.chalkak.backend.admin.api.v1.dto.response.AdminAppVersionResponse;
import com.chalkak.backend.admin.service.appversion.AdminAppVersionService;
import com.chalkak.backend.appversion.domain.AppPlatform;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/app-versions")
public class AdminAppVersionController implements AdminAppVersionApiDocs {

    private final AdminAppVersionService service;

    @Override
    @GetMapping
    public ResponseEntity<AdminAppVersionListResponse> getAppVersions(
            @CurrentAdmin AuthenticatedAdmin admin
    ) {
        return ResponseEntity.ok(new AdminAppVersionListResponse(service.getAppVersions().stream()
                .map(AdminAppVersionResponse::from).toList()));
    }

    @Override
    @PutMapping("/{platform}")
    public ResponseEntity<AdminAppVersionResponse> saveAppVersion(
            @CurrentAdmin AuthenticatedAdmin admin,
            @PathVariable AppPlatform platform,
            @Valid @RequestBody AdminAppVersionRequest request
    ) {
        return ResponseEntity.ok(AdminAppVersionResponse.from(service.saveAppVersion(
                admin.adminId(),
                platform,
                request.revision(),
                request.minimumSupportedVersion(),
                request.storeUrl(),
                request.reason()
        )));
    }
}
