package com.chalkak.backend.admin.api.v1.controller;

import com.chalkak.backend.admin.api.support.AuthenticatedAdmin;
import com.chalkak.backend.admin.api.support.CurrentAdmin;
import com.chalkak.backend.admin.api.v1.docs.AdminAuthApiDocs;
import com.chalkak.backend.admin.api.v1.dto.request.AdminLoginRequest;
import com.chalkak.backend.admin.api.v1.dto.request.AdminRefreshTokenRequest;
import com.chalkak.backend.admin.api.v1.dto.response.AdminLoginResponse;
import com.chalkak.backend.admin.api.v1.dto.response.AdminTokenRefreshResponse;
import com.chalkak.backend.admin.api.v1.dto.response.CurrentAdminResponse;
import com.chalkak.backend.admin.service.AdminAuthenticationService;
import com.chalkak.backend.admin.service.AdminRefreshTokenService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/auth")
public class AdminAuthController implements AdminAuthApiDocs {

    private final AdminAuthenticationService service;
    private final AdminRefreshTokenService adminRefreshTokenService;

    @Override
    @PostMapping("/login")
    public ResponseEntity<AdminLoginResponse> login(
            @Valid @RequestBody AdminLoginRequest request
    ) {
        return ResponseEntity.ok(AdminLoginResponse.from(
                service.login(request.username(), request.password())
        ));
    }

    @Override
    @GetMapping("/me")
    public ResponseEntity<CurrentAdminResponse> getCurrentAdmin(
            @CurrentAdmin AuthenticatedAdmin authenticatedAdmin
    ) {
        return ResponseEntity.ok(CurrentAdminResponse.from(
                service.getCurrentAdmin(authenticatedAdmin.adminId())
        ));
    }

    @Override
    @PostMapping("/refresh")
    public ResponseEntity<AdminTokenRefreshResponse> refresh(
            @Valid @RequestBody AdminRefreshTokenRequest request
    ) {
        return ResponseEntity.ok(AdminTokenRefreshResponse.from(
                adminRefreshTokenService.refresh(request.refreshToken())
        ));
    }

    @Override
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @Valid @RequestBody AdminRefreshTokenRequest request
    ) {
        adminRefreshTokenService.logout(request.refreshToken());

        return ResponseEntity.noContent().build();
    }
}
