package com.chalkak.backend.admin.api.v1.controller;

import com.chalkak.backend.admin.api.support.AuthenticatedAdmin;
import com.chalkak.backend.admin.api.support.CurrentAdmin;
import com.chalkak.backend.admin.api.v1.docs.AdminBannerApiDocs;
import com.chalkak.backend.admin.api.v1.dto.request.AdminBannerListRequest;
import com.chalkak.backend.admin.api.v1.dto.request.AdminBannerRequest;
import com.chalkak.backend.admin.api.v1.dto.response.AdminBannerListResponse;
import com.chalkak.backend.admin.api.v1.dto.response.AdminBannerResponse;
import com.chalkak.backend.admin.service.banner.AdminBannerResult;
import com.chalkak.backend.admin.service.banner.AdminBannerService;
import com.chalkak.backend.common.util.CanonicalUuidParser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/banners")
public class AdminBannerController implements AdminBannerApiDocs {

    private final AdminBannerService bannerService;

    @Override
    @PostMapping
    public ResponseEntity<AdminBannerResponse> createBanner(
            @CurrentAdmin AuthenticatedAdmin authenticatedAdmin,
            @Valid @RequestBody AdminBannerRequest request
    ) {
        AdminBannerResult result = bannerService.createBanner(authenticatedAdmin.adminId(),
                request.title(), request.subtitle(), request.webViewUrl(), request.thumbnailUrl());
        return ResponseEntity.status(HttpStatus.CREATED).body(AdminBannerResponse.from(result));
    }

    @Override
    @GetMapping
    public ResponseEntity<AdminBannerListResponse> getBanners(
            @CurrentAdmin AuthenticatedAdmin authenticatedAdmin,
            @ModelAttribute AdminBannerListRequest request
    ) {
        return ResponseEntity.ok(AdminBannerListResponse.from(
                bannerService.getBanners(request.page(), request.pageSize())));
    }

    @Override
    @DeleteMapping("/{bannerId}")
    public ResponseEntity<Void> deleteBanner(
            @CurrentAdmin AuthenticatedAdmin authenticatedAdmin,
            @PathVariable String bannerId
    ) {
        bannerService.deleteBanner(CanonicalUuidParser.parse(bannerId),
                authenticatedAdmin.adminId());
        return ResponseEntity.noContent().build();
    }
}
