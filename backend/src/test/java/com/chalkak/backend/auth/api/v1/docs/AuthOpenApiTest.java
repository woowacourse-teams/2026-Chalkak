package com.chalkak.backend.auth.api.v1.docs;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chalkak.backend.support.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class AuthOpenApiTest extends IntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("사용자 문서는 차단 회원의 로그인 성공과 탈퇴한 차단 계정의 거부를 구분한다")
    void userApiDocs_socialLogin_exposesBannedUserContract() throws Exception {
        // When & Then
        mockMvc.perform(get("/v3/api-docs/user-api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/auth/social-login'].post"
                        + ".responses['200'].description")
                        .value("로그인 성공(차단 회원 포함) 또는 회원가입 필요"))
                .andExpect(jsonPath("$.paths['/api/v1/auth/social-login'].post"
                        + ".responses['403'].description")
                        .value("탈퇴한 차단 소셜 계정"));
    }

    @Test
    @DisplayName("사용자 문서는 Apple 로그인에서도 차단 회원의 로그인 성공과 탈퇴한 차단 계정의 거부를 구분한다")
    void userApiDocs_appleLogin_exposesBannedUserContract() throws Exception {
        // When & Then
        mockMvc.perform(get("/v3/api-docs/user-api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/auth/apple/social-login'].post"
                        + ".responses['200'].description")
                        .value("로그인 성공(차단 회원 포함) 또는 회원가입 필요"))
                .andExpect(jsonPath("$.paths['/api/v1/auth/apple/social-login'].post"
                        + ".responses['403'].description")
                        .value("탈퇴한 차단 Apple 계정"));
    }

    /**
     * 가입 완료의 400 중 사인 이미지 처리 중만 errorCode가 다르다. 클라이언트는 이 값으로 같은
     * 회원가입 토큰의 재시도 여부를 판단하므로 문서에서 빠지면 안 된다.
     */
    @Test
    @DisplayName("사용자 문서는 가입 완료의 사인 이미지 처리 중 errorCode를 제공한다")
    void userApiDocs_socialSignup_exposesSignatureProcessingPendingErrorCode() throws Exception {
        // When & Then
        mockMvc.perform(get("/v3/api-docs/user-api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/auth/social-signup'].post"
                        + ".responses['400'].description")
                        .value(containsString("SIGNATURE_PROCESSING_PENDING")));
    }

    /**
     * 재발급은 리프레시 토큰 자체가 자격증명이라 액세스 토큰을 요구하지 않는다. 문서에 보안
     * 요구사항이 붙으면 클라이언트가 만료된 액세스 토큰을 실어 보내려다 막힌다.
     */
    @Test
    @DisplayName("user-api 문서는 토큰 재발급을 인증 없이 호출하는 계약으로 제공한다")
    void userApiDocs_refresh_exposesUnauthenticatedContract() throws Exception {
        // When & Then
        mockMvc.perform(get("/v3/api-docs/user-api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/auth/refresh'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/auth/refresh'].post.security")
                        .doesNotExist())
                .andExpect(jsonPath("$.paths['/api/v1/auth/refresh'].post"
                        + ".responses['200']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/auth/refresh'].post"
                        + ".responses['400']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/auth/refresh'].post"
                        + ".responses['401']").exists());
    }

    /** 로그아웃도 같은 이유로 인증 없이 열려 있어야 한다. */
    @Test
    @DisplayName("user-api 문서는 로그아웃을 인증 없이 호출하는 계약으로 제공한다")
    void userApiDocs_logout_exposesUnauthenticatedContract() throws Exception {
        // When & Then
        mockMvc.perform(get("/v3/api-docs/user-api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/auth/logout'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/auth/logout'].post.security")
                        .doesNotExist())
                .andExpect(jsonPath("$.paths['/api/v1/auth/logout'].post"
                        + ".responses['204']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/auth/logout'].post"
                        + ".responses['400']").exists());
    }
}
