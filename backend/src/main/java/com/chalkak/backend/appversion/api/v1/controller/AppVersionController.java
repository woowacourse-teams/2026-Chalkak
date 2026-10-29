package com.chalkak.backend.appversion.api.v1.controller;

import com.chalkak.backend.appversion.api.v1.docs.AppVersionApiDocs;
import com.chalkak.backend.appversion.api.v1.dto.response.AppVersionResponse;
import com.chalkak.backend.appversion.domain.AppPlatform;
import com.chalkak.backend.appversion.service.AppVersionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/app-version")
public class AppVersionController implements AppVersionApiDocs {

    private final AppVersionService appVersionService;

    @Override
    @GetMapping
    public ResponseEntity<AppVersionResponse> getAppVersion(@RequestParam AppPlatform platform) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(AppVersionResponse.from(appVersionService.getAppVersion(platform)));
    }
}
