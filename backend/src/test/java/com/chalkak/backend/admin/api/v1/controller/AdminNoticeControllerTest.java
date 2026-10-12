package com.chalkak.backend.admin.api.v1.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chalkak.backend.admin.api.support.AdminActorResolver;
import com.chalkak.backend.admin.api.support.AdminArgumentResolverWebMvcConfig;
import com.chalkak.backend.admin.api.support.AuthenticatedAdmin;
import com.chalkak.backend.admin.service.notification.AdminNoticeResult;
import com.chalkak.backend.admin.service.notification.AdminNoticeService;
import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.exception.GlobalExceptionHandler;
import java.time.Instant;
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

@WebMvcTest(AdminNoticeController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({GlobalExceptionHandler.class, AdminArgumentResolverWebMvcConfig.class})
class AdminNoticeControllerTest {

    private static final UUID ADMIN_ID = UUID.fromString("019a0000-0000-7000-8000-000000000901");
    private static final UUID NOTICE_ID = UUID.fromString("019a0000-0000-7000-8000-000000000592");
    private static final String PATH = "/api/v1/admin/notices";
    private static final Instant SCHEDULED_AT = Instant.parse("2026-10-19T09:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminNoticeService noticeService;

    @MockitoBean
    private AdminActorResolver adminActorResolver;

    @BeforeEach
    void setUp() {
        given(adminActorResolver.resolve()).willReturn(new AuthenticatedAdmin(ADMIN_ID));
    }

    @Test
    @DisplayName("등록 성공 시 201과 저장된 공지를 응답한다")
    void createNotice_validRequest_returnsCreatedNotice() throws Exception {
        // Given
        Instant createdAt = Instant.parse("2026-10-12T09:00:00Z");
        given(noticeService.createNotice(ADMIN_ID, "서비스 점검 안내", "점검합니다.", SCHEDULED_AT))
                .willReturn(new AdminNoticeResult(NOTICE_ID, "서비스 점검 안내", "점검합니다.",
                        SCHEDULED_AT, createdAt, createdAt));

        // When & Then
        mockMvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"title":"서비스 점검 안내","body":"점검합니다.",
                        "scheduledAt":"2026-10-19T09:00:00Z"}
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.noticeId").value(NOTICE_ID.toString()))
                .andExpect(jsonPath("$.title").value("서비스 점검 안내"))
                .andExpect(jsonPath("$.body").value("점검합니다."))
                .andExpect(jsonPath("$.scheduledAt").value("2026-10-19T09:00:00Z"))
                .andExpect(jsonPath("$.createdAt").value(createdAt.toString()))
                .andExpect(jsonPath("$.updatedAt").value(createdAt.toString()));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"body\":\"점검합니다.\",\"scheduledAt\":\"2026-10-19T09:00:00Z\"}",
            "{\"title\":\" \",\"body\":\"점검합니다.\",\"scheduledAt\":\"2026-10-19T09:00:00Z\"}",
            "{\"title\":\"안내\",\"scheduledAt\":\"2026-10-19T09:00:00Z\"}",
            "{\"title\":\"안내\",\"body\":\" \",\"scheduledAt\":\"2026-10-19T09:00:00Z\"}",
            "{\"title\":\"안내\",\"body\":\"점검합니다.\"}",
            "{\"title\":\"안내\",\"body\":\"점검합니다.\",\"scheduledAt\":\"2026-10-19 09:00\"}"
    })
    @DisplayName("제목·본문 누락이나 공백, 예약 시각 누락이나 형식 오류는 400으로 응답한다")
    void createNotice_invalidRequest_returnsBadRequest(String content) throws Exception {
        // When & Then
        mockMvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(content))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_ERROR"));
    }

    @Test
    @DisplayName("서비스의 업무 예외는 400과 BUSINESS_ERROR로 응답한다")
    void createNotice_businessException_returnsBadRequest() throws Exception {
        // Given
        given(noticeService.createNotice(any(), anyString(), anyString(), any()))
                .willThrow(new BusinessException(ErrorCode.BUSINESS_ERROR,
                        "공지 예약 시각은 현재 시각보다 5분 넘게 과거일 수 없습니다."));

        // When & Then
        mockMvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"title":"서비스 점검 안내","body":"점검합니다.",
                        "scheduledAt":"2026-10-01T09:00:00Z"}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_ERROR"))
                .andExpect(jsonPath("$.message")
                        .value("공지 예약 시각은 현재 시각보다 5분 넘게 과거일 수 없습니다."));
    }
}
