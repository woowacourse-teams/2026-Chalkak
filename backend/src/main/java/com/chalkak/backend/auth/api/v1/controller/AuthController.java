package com.chalkak.backend.auth.api.v1.controller;

import com.chalkak.backend.auth.api.v1.docs.AuthApiDocs;
import com.chalkak.backend.auth.api.v1.dto.request.RefreshTokenRequest;
import com.chalkak.backend.auth.api.v1.dto.request.SocialIdTokenRequest;
import com.chalkak.backend.auth.api.v1.dto.request.SocialSignupRequest;
import com.chalkak.backend.auth.api.v1.dto.response.SocialLoginResponse;
import com.chalkak.backend.auth.api.v1.dto.response.SocialSignupResponse;
import com.chalkak.backend.auth.api.v1.dto.response.SocialSignupSignatureUploadResponse;
import com.chalkak.backend.auth.api.v1.dto.response.TokenRefreshResponse;
import com.chalkak.backend.auth.service.SocialLoginResult;
import com.chalkak.backend.auth.service.SocialLoginService;
import com.chalkak.backend.auth.service.SocialSignupResult;
import com.chalkak.backend.auth.service.SocialSignupService;
import com.chalkak.backend.auth.service.SocialSignupSignatureUploadResult;
import com.chalkak.backend.auth.service.TokenRefreshResult;
import com.chalkak.backend.auth.service.UserRefreshTokenService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth")
public class AuthController implements AuthApiDocs {

    private final SocialLoginService socialLoginService;
    private final SocialSignupService socialSignupService;
    private final UserRefreshTokenService userRefreshTokenService;

    @Override
    @PostMapping("/social-login")
    public ResponseEntity<SocialLoginResponse> socialLogin(
            @Valid @RequestBody SocialIdTokenRequest request
    ) {
        SocialLoginResult result = socialLoginService.login(
                request.provider(),
                request.idToken());

        return ResponseEntity.ok(SocialLoginResponse.from(result));
    }

    @Override
    @PostMapping("/social-signup/signature/uploads")
    public ResponseEntity<SocialSignupSignatureUploadResponse>
            createSocialSignupSignatureUpload(
                    @Valid @RequestBody SocialIdTokenRequest request
            ) {
        SocialSignupSignatureUploadResult result =
                socialSignupService.createSignatureUpload(
                        request.provider(),
                        request.idToken());

        return ResponseEntity.ok(SocialSignupSignatureUploadResponse.from(result));
    }

    @Override
    @PostMapping("/social-signup")
    public ResponseEntity<SocialSignupResponse> socialSignup(
            @Valid @RequestBody SocialSignupRequest request
    ) {
        SocialSignupResult result = socialSignupService.signup(
                request.signupToken());

        return ResponseEntity.ok(SocialSignupResponse.from(result));
    }

    @Override
    @PostMapping("/refresh")
    public ResponseEntity<TokenRefreshResponse> refresh(
            @Valid @RequestBody RefreshTokenRequest request
    ) {
        TokenRefreshResult result = userRefreshTokenService.refresh(
                request.refreshToken());

        return ResponseEntity.ok(TokenRefreshResponse.from(result));
    }

    @Override
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @Valid @RequestBody RefreshTokenRequest request
    ) {
        userRefreshTokenService.logout(request.refreshToken());

        return ResponseEntity.noContent().build();
    }
}
