package com.chalkak.backend.admin.api.v1.docs;

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
class AdminAuthOpenApiTest extends IntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("관리자 문서는 리프레시 토큰 경로만 공개하고 나머지 관리자 API에 관리자 Bearer 인증을 요구한다")
    void adminApiDocs_exposesAdminSecurityAndRefreshTokenExceptions() throws Exception {
        mockMvc.perform(get("/v3/api-docs/admin-api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.securitySchemes.adminAccessToken.type")
                        .value("http"))
                .andExpect(jsonPath("$.components.securitySchemes.adminAccessToken.scheme")
                        .value("bearer"))
                .andExpect(jsonPath("$.paths['/api/v1/admin/auth/login'].post.security")
                        .isEmpty())
                .andExpect(jsonPath("$.paths['/api/v1/admin/auth/refresh'].post.security")
                        .isEmpty())
                .andExpect(jsonPath("$.paths['/api/v1/admin/auth/logout'].post.security")
                        .isEmpty())
                .andExpect(jsonPath("$.paths['/api/v1/admin/auth/me'].get.security[0]"
                        + ".adminAccessToken").isArray())
                .andExpect(jsonPath("$.paths['/api/v1/admin/posts'].get.security[0]"
                        + ".adminAccessToken").isArray())
                .andExpect(jsonPath("$.paths['/api/v1/admin/auth/me'].get.parameters")
                        .doesNotExist())
                .andExpect(jsonPath("$.paths['/api/v1/admin/auth/logout'].post.parameters")
                        .doesNotExist())
                .andExpect(jsonPath("$.paths['/api/v1/admin/audit-logs'].get.responses['401']"
                        + ".content['application/json'].schema['$ref']")
                        .value("#/components/schemas/ErrorResponse"))
                .andExpect(jsonPath("$.paths['/api/v1/admin/auth/me'].get.responses['403']"
                        + ".content['application/json'].schema['$ref']")
                        .value("#/components/schemas/ErrorResponse"));
    }

    @Test
    @DisplayName("관리자 재발급 문서는 재로그인 필요 401을, 로그아웃 문서는 인증 실패 응답을 두지 않는다")
    void adminApiDocs_documentsRefreshFailureAndKeepsLogoutIdempotent() throws Exception {
        mockMvc.perform(get("/v3/api-docs/admin-api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/admin/auth/refresh'].post"
                        + ".responses['401'].content['application/json'].schema['$ref']")
                        .value("#/components/schemas/ErrorResponse"))
                .andExpect(jsonPath("$.paths['/api/v1/admin/auth/refresh'].post"
                        + ".responses['401'].description")
                        .value(containsString("REAUTHENTICATION_REQUIRED")))
                .andExpect(jsonPath("$.paths['/api/v1/admin/auth/logout'].post"
                        + ".responses['401']").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/v1/admin/auth/logout'].post"
                        + ".responses['204']").exists());
    }
}
