package com.chalkak.backend.auth.api.v1.controller;

import com.chalkak.backend.auth.api.v1.docs.AuthApiDocs;
import com.chalkak.backend.auth.api.v1.dto.request.SocialLoginRequest;
import com.chalkak.backend.auth.api.v1.dto.response.SocialLoginResponse;
import com.chalkak.backend.auth.service.SocialLoginResult;
import com.chalkak.backend.auth.service.SocialLoginService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth")
@Profile("!prod")
public class AuthController implements AuthApiDocs {

    private final SocialLoginService socialLoginService;

    @Override
    @PostMapping("/social-login")
    public ResponseEntity<SocialLoginResponse> socialLogin(
            @Valid @RequestBody SocialLoginRequest request
    ) {
        SocialLoginResult result = socialLoginService.login(
                request.provider(),
                request.idToken());

        return ResponseEntity.ok(SocialLoginResponse.from(result));
    }
}
