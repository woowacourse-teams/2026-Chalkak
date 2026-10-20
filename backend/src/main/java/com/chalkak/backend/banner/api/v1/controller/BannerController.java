package com.chalkak.backend.banner.api.v1.controller;

import com.chalkak.backend.banner.api.v1.docs.BannerApiDocs;
import com.chalkak.backend.banner.api.v1.dto.request.BannerListRequest;
import com.chalkak.backend.banner.api.v1.dto.response.BannerListResponse;
import com.chalkak.backend.banner.service.BannerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/banners")
public class BannerController implements BannerApiDocs {

    private final BannerService bannerService;

    @Override
    @GetMapping
    public ResponseEntity<BannerListResponse> getBanners(
            @ModelAttribute BannerListRequest request
    ) {
        return ResponseEntity.ok(BannerListResponse.from(
                bannerService.getBanners(request.page(), request.pageSize())));
    }
}
