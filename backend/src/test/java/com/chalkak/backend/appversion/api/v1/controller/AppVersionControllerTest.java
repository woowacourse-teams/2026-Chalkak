package com.chalkak.backend.appversion.api.v1.controller;

import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chalkak.backend.appversion.domain.AppPlatform;
import com.chalkak.backend.appversion.service.AppVersionResult;
import com.chalkak.backend.appversion.service.AppVersionService;
import com.chalkak.backend.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AppVersionController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class AppVersionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AppVersionService service;

    @Test
    @DisplayName("플랫폼 설정의 세 필드와 캐시 방지 헤더를 반환한다")
    void getAppVersion_configured_returnsSettingsAndNoStore() throws Exception {
        // Given
        given(service.getAppVersion(AppPlatform.ANDROID)).willReturn(new AppVersionResult(
                AppPlatform.ANDROID,
                "1.2.0",
                "https://play.google.com/store/apps/details?id=com.example.chalkak"
        ));

        // When & Then
        mockMvc.perform(get("/api/v1/app-version").param("platform", "ANDROID"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$", aMapWithSize(3)))
                .andExpect(jsonPath("$.platform").value("ANDROID"))
                .andExpect(jsonPath("$.minimumSupportedVersion").value("1.2.0"))
                .andExpect(jsonPath("$.storeUrl").value(
                        "https://play.google.com/store/apps/details?id=com.example.chalkak"));
    }

    @Test
    @DisplayName("미설정 플랫폼도 세 필드를 유지하고 버전과 주소를 null로 반환한다")
    void getAppVersion_unconfigured_returnsExplicitNulls() throws Exception {
        // Given
        given(service.getAppVersion(AppPlatform.IOS))
                .willReturn(AppVersionResult.createUnconfigured(AppPlatform.IOS));

        // When & Then
        mockMvc.perform(get("/api/v1/app-version").param("platform", "IOS"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$", aMapWithSize(3)))
                .andExpect(jsonPath("$.platform").value("IOS"))
                .andExpect(jsonPath("$.minimumSupportedVersion").value(nullValue()))
                .andExpect(jsonPath("$.storeUrl").value(nullValue()));
    }

    @Test
    @DisplayName("플랫폼을 생략하면 400과 공통 오류를 반환한다")
    void getAppVersion_missingPlatform_returnsBadRequest() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/v1/app-version"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_ERROR"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "android", "ios", "WINDOWS"})
    @DisplayName("빈 값이나 허용되지 않은 플랫폼이면 400을 반환한다")
    void getAppVersion_invalidPlatform_returnsBadRequest(String platform) throws Exception {
        // When & Then
        mockMvc.perform(get("/api/v1/app-version").param("platform", platform))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_ERROR"));
    }
}
