package com.chalkak.backend.auth.api.v1.docs;

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
}
