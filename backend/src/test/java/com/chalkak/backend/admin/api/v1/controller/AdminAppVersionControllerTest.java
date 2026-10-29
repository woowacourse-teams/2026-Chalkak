package com.chalkak.backend.admin.api.v1.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chalkak.backend.admin.api.support.AdminActorResolver;
import com.chalkak.backend.admin.api.support.AdminArgumentResolverWebMvcConfig;
import com.chalkak.backend.admin.api.support.AuthenticatedAdmin;
import com.chalkak.backend.admin.service.appversion.AdminAppVersionService;
import com.chalkak.backend.appversion.domain.AppPlatform;
import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.exception.GlobalExceptionHandler;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

@WebMvcTest(AdminAppVersionController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({GlobalExceptionHandler.class, AdminArgumentResolverWebMvcConfig.class})
class AdminAppVersionControllerTest {
    private static final UUID ADMIN = UUID.randomUUID();
    private static final String PATH = "/api/v1/admin/app-versions";
    private static final String URL = "https://apps.apple.com/app/id123";
    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper mapper;
    @MockitoBean
    private AdminAppVersionService service;
    @MockitoBean
    private AdminActorResolver actor;

    @BeforeEach
    void setUp() {
        given(actor.resolve()).willReturn(new AuthenticatedAdmin(ADMIN));
    }

    @Test
    @DisplayName("목록 조회는 두 플랫폼과 미설정 null을 직렬화한다")
    void getAppVersions_unconfigured_returnsList() throws Exception {
        // Given
        given(service.getAppVersions()).willReturn(List.of(
                AdminAppVersionService.SettingResult.createUnconfigured(AppPlatform.ANDROID),
                AdminAppVersionService.SettingResult.createUnconfigured(AppPlatform.IOS)));
        // When & Then
        mvc.perform(get(PATH)).andExpect(status().isOk())
                .andExpect(jsonPath("$.appVersions.length()").value(2))
                .andExpect(jsonPath("$.appVersions[0].platform").value("ANDROID"))
                .andExpect(jsonPath("$.appVersions[1].platform").value("IOS"))
                .andExpect(jsonPath("$.appVersions[0].minimumSupportedVersion")
                        .value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    @DisplayName("최초 저장은 null revision을 전달하고 설정·변경자·시각을 200으로 반환한다")
    void saveAppVersion_initialRequest_returnsSetting() throws Exception {
        // Given
        Instant now = Instant.parse("2026-10-29T00:00:00Z");
        given(service.saveAppVersion(
                ADMIN,
                AppPlatform.IOS,
                null,
                "1.0.0",
                URL,
                "출시 확인"
        )).willReturn(new AdminAppVersionService.SettingResult(
                AppPlatform.IOS,
                "1.0.0",
                URL,
                0L,
                ADMIN,
                "출시 확인",
                now,
                now
        ));
        // When & Then
        mvc.perform(put(PATH + "/IOS").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(request())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.platform").value("IOS"))
                .andExpect(jsonPath("$.minimumSupportedVersion").value("1.0.0"))
                .andExpect(jsonPath("$.storeUrl").value(URL))
                .andExpect(jsonPath("$.revision").value(0))
                .andExpect(jsonPath("$.lastChangedBy").value(ADMIN.toString()))
                .andExpect(jsonPath("$.changeReason").value("출시 확인"))
                .andExpect(jsonPath("$.createdAt").value(now.toString()))
                .andExpect(jsonPath("$.updatedAt").value(now.toString()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"minimumSupportedVersion", "storeUrl", "reason"})
    @DisplayName("필수 입력의 누락과 공백은 400 업무 오류로 반환한다")
    void saveAppVersion_missingOrBlankField_returnsBadRequest(String field) throws Exception {
        // Given
        var body = request();
        body.remove(field);
        // When & Then
        mvc.perform(put(PATH + "/IOS").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_ERROR"));
        body.put(field, " ");
        mvc.perform(put(PATH + "/IOS").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("음수 revision과 잘못된 플랫폼은 400을 반환한다")
    void saveAppVersion_invalidRevisionOrPlatform_returnsBadRequest() throws Exception {
        // Given
        var body = request();
        body.put("revision", -1);
        // When & Then
        mvc.perform(put(PATH + "/IOS").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
        mvc.perform(put(PATH + "/WEB").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(request())))
                .andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @ValueSource(strings = {"BUSINESS_ERROR", "RESOURCE_STATE_CHANGED"})
    @DisplayName("서비스 입력 오류와 수정 충돌은 기존 400 오류 계약으로 반환한다")
    void saveAppVersion_serviceException_returnsErrorCode(String code) throws Exception {
        // Given
        given(service.saveAppVersion(
                any(),
                any(),
                any(),
                any(),
                any(),
                any()
        )).willThrow(new BusinessException(ErrorCode.valueOf(code), "설정 오류"));
        // When & Then
        mvc.perform(put(PATH + "/IOS").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(request())))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value(code));
    }

    private Map<String, Object> request() {
        var body = new LinkedHashMap<String, Object>();
        body.put("revision", null);
        body.put("minimumSupportedVersion", "1.0.0");
        body.put("storeUrl", URL);
        body.put("reason", "출시 확인");
        return body;
    }
}
