package com.chalkak.backend.admin.api.support;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(AdminCorsProdProfileTest.TestController.class)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("prod")
@Import({
        AdminCorsProdProfileTest.TestController.class,
        AdminArgumentResolverWebMvcConfig.class,
        AdminCorsWebMvcConfig.class
})
@TestPropertySource(properties =
        "chalkak.admin.cors.allowed-origins=https://admin.chalkak.example")
class AdminCorsProdProfileTest {

    private static final UUID ADMIN_ID = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminActorResolver adminActorResolver;

    @BeforeEach
    void setUp() {
        given(adminActorResolver.resolve()).willReturn(new AuthenticatedAdmin(ADMIN_ID));
    }

    @Test
    @DisplayName("운영 프로필에서도 설정한 관리자 웹 Origin의 사전 요청만 허용한다")
    void preflight_configuredProductionOrigin_allowsAdminRequest() throws Exception {
        mockMvc.perform(options("/api/v1/admin/cors-test")
                        .header(HttpHeaders.ORIGIN, "https://admin.chalkak.example")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS,
                                HttpHeaders.AUTHORIZATION))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,
                        "https://admin.chalkak.example"));
    }

    @Test
    @DisplayName("운영 프로필에서 등록하지 않은 Origin은 차단한다")
    void request_unconfiguredProductionOrigin_rejectsAdminRequest() throws Exception {
        mockMvc.perform(get("/api/v1/admin/cors-test")
                        .header(HttpHeaders.ORIGIN, "https://attacker.example"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @RestController
    public static class TestController {

        @GetMapping("/api/v1/admin/cors-test")
        public AuthenticatedAdmin getCurrentAdmin(
                @CurrentAdmin AuthenticatedAdmin authenticatedAdmin
        ) {
            return authenticatedAdmin;
        }
    }
}
