package com.chalkak.backend.admin.api.support;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(AdminWebMvcConfigTest.TestController.class)
@Import({
        AdminWebMvcConfigTest.TestController.class,
        AdminArgumentResolverWebMvcConfig.class,
        DevelopmentAdminCorsWebMvcConfig.class
})
@TestPropertySource(properties =
        "chalkak.admin.cors.allowed-origins=https://admin-dev.example.com")
class AdminWebMvcConfigTest {

    private static final UUID ADMIN_ID =
            UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6570f6");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminActorResolver adminActorResolver;

    @Test
    @DisplayName("등록한 CurrentAdmin Resolver로 개발 관리자를 Controller에 주입한다")
    void getAdminEndpoint_withoutAuthentication_injectsDevelopmentAdmin() throws Exception {
        // Given
        given(adminActorResolver.resolve()).willReturn(new AuthenticatedAdmin(ADMIN_ID));

        // When & Then
        mockMvc.perform(get("/api/v1/admin/test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.adminId").value(ADMIN_ID.toString()));
    }

    @Test
    @DisplayName("설정한 관리자 웹 Origin의 사전 요청을 허용한다")
    void preflight_configuredOrigin_allowsAdminRequest() throws Exception {
        // When & Then
        mockMvc.perform(options("/api/v1/admin/test")
                        .header(HttpHeaders.ORIGIN, "https://admin-dev.example.com")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, HttpHeaders.AUTHORIZATION))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,
                        "https://admin-dev.example.com"))
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS,
                        containsString(HttpHeaders.AUTHORIZATION)));
    }

    @Test
    @DisplayName("설정한 관리자 웹 Origin의 실제 요청에 허용 응답 헤더를 제공한다")
    void request_configuredOrigin_allowsAdminRequest() throws Exception {
        // Given
        given(adminActorResolver.resolve()).willReturn(new AuthenticatedAdmin(ADMIN_ID));

        // When & Then
        mockMvc.perform(get("/api/v1/admin/test")
                        .header(HttpHeaders.ORIGIN, "https://admin-dev.example.com"))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,
                        "https://admin-dev.example.com"));
    }

    @Test
    @DisplayName("설정하지 않은 Origin의 관리자 API 사전 요청을 차단한다")
    void preflight_unconfiguredOrigin_rejectsAdminRequest() throws Exception {
        // When & Then
        mockMvc.perform(options("/api/v1/admin/test")
                        .header(HttpHeaders.ORIGIN, "https://attacker.example.com")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    @DisplayName("설정하지 않은 Origin의 실제 관리자 API 요청을 작업자 조회 전에 차단한다")
    void request_unconfiguredOrigin_rejectsBeforeResolvingAdmin() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/v1/admin/test")
                        .header(HttpHeaders.ORIGIN, "https://attacker.example.com"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));

        then(adminActorResolver).shouldHaveNoInteractions();
    }

    @RestController
    public static class TestController {

        @GetMapping("/api/v1/admin/test")
        public AuthenticatedAdmin getCurrentAdmin(
                @CurrentAdmin AuthenticatedAdmin authenticatedAdmin
        ) {
            return authenticatedAdmin;
        }
    }
}
