package com.chalkak.backend.admin.api.v1.controller;

import com.chalkak.backend.admin.api.support.AuthenticatedAdmin;
import com.chalkak.backend.admin.api.support.CurrentAdmin;
import com.chalkak.backend.admin.api.v1.docs.AdminBannerApiDocs;
import com.chalkak.backend.admin.api.v1.dto.request.AdminBannerRequest;
import com.chalkak.backend.admin.service.banner.AdminBannerService;
import com.chalkak.backend.banner.api.v1.dto.response.BannerResponse;
import com.chalkak.backend.banner.service.BannerResult;
import com.chalkak.backend.common.util.CanonicalUuidParser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/banners")
public class AdminBannerController implements AdminBannerApiDocs {

    private final AdminBannerService adminBannerService;

    @Override
    @PostMapping
    public ResponseEntity<BannerResponse> createBanner(
            @CurrentAdmin AuthenticatedAdmin authenticatedAdmin,
            @Valid @RequestBody AdminBannerRequest request
    ) {
        BannerResult result = adminBannerService.createBanner(authenticatedAdmin.adminId(),
                request.title(), request.subtitle(), request.webViewUrl(), request.thumbnailUrl());
        return ResponseEntity.status(HttpStatus.CREATED).body(BannerResponse.from(result));
    }

    @Override
    @DeleteMapping("/{bannerId}")
    public ResponseEntity<Void> deleteBanner(
            @CurrentAdmin AuthenticatedAdmin authenticatedAdmin,
            @PathVariable String bannerId
    ) {
        adminBannerService.deleteBanner(CanonicalUuidParser.parse(bannerId),
                authenticatedAdmin.adminId());
        return ResponseEntity.noContent().build();
    }
}
