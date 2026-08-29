package com.chalkak.backend.admin.api.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chalkak.backend.admin.infrastructure.infra.DisabledAdminActorResolver;
import com.chalkak.backend.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest({
        ProdAdminAccessWebMvcConfigTest.AdminController.class,
        ProdAdminAccessWebMvcConfigTest.ControlController.class
})
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("prod")
@Import({
        ProdAdminAccessWebMvcConfigTest.AdminController.class,
        ProdAdminAccessWebMvcConfigTest.ControlController.class,
        AdminArgumentResolverWebMvcConfig.class,
        DisabledAdminActorResolver.class,
        ProdAdminAccessInterceptor.class,
        ProdAdminAccessWebMvcConfig.class,
        GlobalExceptionHandler.class
})
class ProdAdminAccessWebMvcConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("운영 환경의 관리자 API를 실제 인증 연결 전까지 차단한다")
    void request_existingAdminEndpoint_returnsForbidden() throws Exception {
        assertAdminAccessForbidden(get("/api/v1/admin/test"));
    }

    @Test
    @DisplayName("운영 환경의 정확한 관리자 API 루트 경로도 차단한다")
    void request_adminApiRoot_returnsForbidden() throws Exception {
        assertAdminAccessForbidden(get("/api/v1/admin"));
    }

    @Test
    @DisplayName("운영 환경에서는 아직 존재하지 않는 관리자 하위 경로도 차단한다")
    void request_missingAdminEndpoint_returnsForbidden() throws Exception {
        assertAdminAccessForbidden(get("/api/v1/admin/missing"));
    }

    @Test
    @DisplayName("가짜 관리자 ID 헤더로 운영 환경 차단을 우회할 수 없다")
    void request_withFakeAdminIdHeader_returnsForbidden() throws Exception {
        assertAdminAccessForbidden(get("/api/v1/admin/test")
                .header("X-Admin-Id", "0198f6c1-62ba-7d30-8b12-0f733b6570f6"));
    }

    @Test
    @DisplayName("관리자 API와 이름이 비슷한 일반 경로는 과도하게 차단하지 않는다")
    void request_similarNonAdminPath_returnsOk() throws Exception {
        mockMvc.perform(get("/api/v1/administrator/test"))
                .andExpect(status().isOk())
                .andExpect(content().string("ok"));
    }

    private void assertAdminAccessForbidden(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request
    ) throws Exception {
        mockMvc.perform(request)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value("관리자 API에 접근할 수 없습니다."));
    }

    @RestController
    public static class AdminController {

        @GetMapping("/api/v1/admin/test")
        public AuthenticatedAdmin getCurrentAdmin(
                @CurrentAdmin AuthenticatedAdmin authenticatedAdmin
        ) {
            return authenticatedAdmin;
        }
    }

    @RestController
    public static class ControlController {

        @GetMapping("/api/v1/administrator/test")
        public String getControl() {
            return "ok";
        }
    }
}
