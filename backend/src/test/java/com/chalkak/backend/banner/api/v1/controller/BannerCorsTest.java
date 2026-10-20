package com.chalkak.backend.banner.api.v1.controller;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chalkak.backend.auth.domain.token.AccessTokenScope;
import com.chalkak.backend.auth.infrastructure.infra.token.access.JwtAccessTokenProvider;
import com.chalkak.backend.common.logging.LogFields;
import com.chalkak.backend.support.IntegrationTestSupport;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "chalkak.admin.cors.allowed-origins=https://admin.chalkak.example",
        "chalkak.admin.authentication.development-bypass-enabled=false"
})
class BannerCorsTest extends IntegrationTestSupport {

    private static final String PATH = "/api/v1/banners";
    private static final String ORIGIN = "https://admin.chalkak.example";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtAccessTokenProvider accessTokenProvider;

    @Test
    @DisplayName("관리자 웹의 Authorization 헤더를 포함한 배너 GET 사전 요청을 허용한다")
    void preflight_adminOrigin_allowsBannerGet() throws Exception {
        // When & Then
        mockMvc.perform(options(PATH)
                .header(HttpHeaders.ORIGIN, ORIGIN)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, HttpHeaders.AUTHORIZATION))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ORIGIN))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, "GET"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS,
                        containsString(HttpHeaders.AUTHORIZATION)));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @DisplayName("관리자 웹의 익명·관리자 배너 GET에 CORS 허용 헤더를 반환한다")
    void getBanners_adminOrigin_allowsAnonymousAndAdmin(boolean authenticated) throws Exception {
        // Given
        MockHttpServletRequestBuilder request = get(PATH).header(HttpHeaders.ORIGIN, ORIGIN);
        if (authenticated) {
            String token = accessTokenProvider.issue(UUID.randomUUID(), AccessTokenScope.ADMIN)
                    .value();
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }

        // When & Then
        mockMvc.perform(request)
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ORIGIN))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS,
                        containsString(LogFields.REQUEST_ID_HEADER)))
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS));
    }

    @Test
    @DisplayName("미허용 Origin의 배너 사전 요청과 실제 GET을 차단한다")
    void request_unconfiguredOrigin_rejectsBannerRequest() throws Exception {
        // When & Then
        mockMvc.perform(options(PATH)
                .header(HttpHeaders.ORIGIN, "https://attacker.example")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
        mockMvc.perform(get(PATH).header(HttpHeaders.ORIGIN, "https://attacker.example"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @ParameterizedTest
    @ValueSource(strings = {"POST", "PUT", "DELETE"})
    @DisplayName("관리자 웹에서도 배너 조회 이외 메서드의 사전 요청은 차단한다")
    void preflight_otherMethods_rejectsRequest(String method) throws Exception {
        // When & Then
        mockMvc.perform(options(PATH)
                .header(HttpHeaders.ORIGIN, ORIGIN)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, method))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }
}
