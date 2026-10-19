package com.chalkak.backend.admin.banner.api.v1.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chalkak.backend.admin.api.support.AdminActorResolver;
import com.chalkak.backend.admin.api.support.AdminArgumentResolverWebMvcConfig;
import com.chalkak.backend.admin.api.support.AuthenticatedAdmin;
import com.chalkak.backend.admin.banner.service.AdminBannerResult;
import com.chalkak.backend.admin.banner.service.AdminBannerService;
import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.exception.GlobalExceptionHandler;
import java.time.Instant;
import java.util.LinkedHashMap;
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

@WebMvcTest(AdminBannerController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({GlobalExceptionHandler.class, AdminArgumentResolverWebMvcConfig.class})
class AdminBannerControllerTest {

    private static final UUID ADMIN_ID = UUID.randomUUID();
    private static final UUID BANNER_ID = UUID.randomUUID();
    private static final String PATH = "/api/v1/admin/banners";
    private static final String URL = "https://chalkak.vercel.app/event";
    private static final String THUMBNAIL = "https://cdn.example.com/banner.png";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AdminBannerService bannerService;

    @MockitoBean
    private AdminActorResolver adminActorResolver;

    @BeforeEach
    void setUp() {
        given(adminActorResolver.resolve()).willReturn(new AuthenticatedAdmin(ADMIN_ID));
    }

    @Test
    @DisplayName("배너 등록 성공 시 201과 여섯 가지 응답 필드를 반환한다")
    void createBanner_validRequest_returnsCreatedBanner() throws Exception {
        // Given
        Instant createdAt = Instant.parse("2026-10-19T00:00:00Z");
        given(bannerService.createBanner(ADMIN_ID, "제목", "부제목", URL, THUMBNAIL))
                .willReturn(new AdminBannerResult(BANNER_ID, "제목", "부제목", URL, THUMBNAIL,
                        createdAt));

        // When & Then
        mockMvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(BANNER_ID.toString()))
                .andExpect(jsonPath("$.title").value("제목"))
                .andExpect(jsonPath("$.subtitle").value("부제목"))
                .andExpect(jsonPath("$.webViewUrl").value(URL))
                .andExpect(jsonPath("$.thumbnailUrl").value(THUMBNAIL))
                .andExpect(jsonPath("$.createdAt").value(createdAt.toString()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"title", "subtitle", "webViewUrl", "thumbnailUrl"})
    @DisplayName("필수 항목이 누락되거나 공백이면 400을 반환한다")
    void createBanner_missingOrBlankField_returnsBadRequest(String field) throws Exception {
        // Given
        Map<String, String> request = validRequest();
        request.remove(field);

        // When & Then
        mockMvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_ERROR"));
        request.put(field, " ");
        mockMvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("중복 웹뷰 URL은 400과 기존 BUSINESS_ERROR 형식으로 반환한다")
    void createBanner_duplicateUrl_returnsBusinessError() throws Exception {
        // Given
        given(bannerService.createBanner(any(), anyString(), anyString(), anyString(), anyString()))
                .willThrow(new BusinessException(ErrorCode.BUSINESS_ERROR,
                        "동일한 웹뷰 URL의 배너가 이미 존재합니다."));

        // When & Then
        mockMvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_ERROR"))
                .andExpect(jsonPath("$.message").value("동일한 웹뷰 URL의 배너가 이미 존재합니다."));
    }

    private Map<String, String> validRequest() {
        return new LinkedHashMap<>(Map.of("title", "제목", "subtitle", "부제목",
                "webViewUrl", URL, "thumbnailUrl", THUMBNAIL));
    }
}
