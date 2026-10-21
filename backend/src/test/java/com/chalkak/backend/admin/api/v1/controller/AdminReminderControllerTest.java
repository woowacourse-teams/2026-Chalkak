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
import com.chalkak.backend.admin.service.notification.AdminReminderResult;
import com.chalkak.backend.admin.service.notification.AdminReminderService;
import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.exception.GlobalExceptionHandler;
import com.chalkak.backend.notification.domain.ReminderRepeat;
import com.chalkak.backend.notification.domain.ReminderTarget;
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

@WebMvcTest(AdminReminderController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({GlobalExceptionHandler.class, AdminArgumentResolverWebMvcConfig.class})
class AdminReminderControllerTest {

    private static final UUID ADMIN_ID = UUID.fromString("019a0000-0000-7000-8000-000000000901");
    private static final UUID REMINDER_ID = UUID.fromString("019a0000-0000-7000-8000-000000000724");
    private static final String PATH = "/api/v1/admin/reminders";
    private static final Instant SCHEDULED_AT = Instant.parse("2026-10-25T12:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminReminderService reminderService;

    @MockitoBean
    private AdminActorResolver adminActorResolver;

    @BeforeEach
    void setUp() {
        given(adminActorResolver.resolve()).willReturn(new AuthenticatedAdmin(ADMIN_ID));
    }

    @Test
    @DisplayName("등록 성공 시 201과 저장된 리마인더를 응답한다")
    void createReminder_validRequest_returnsCreatedReminder() throws Exception {
        // Given
        Instant createdAt = Instant.parse("2026-10-21T09:00:00Z");
        given(reminderService.createReminder(
                ADMIN_ID,
                "오늘이 얼마 남지 않았어요",
                "사진을 자랑해 주세요!",
                ReminderRepeat.DAILY,
                ReminderTarget.TOPIC_NON_PARTICIPANTS,
                SCHEDULED_AT
        ))
                .willReturn(new AdminReminderResult(
                        REMINDER_ID,
                        "오늘이 얼마 남지 않았어요",
                        "사진을 자랑해 주세요!",
                        ReminderRepeat.DAILY,
                        ReminderTarget.TOPIC_NON_PARTICIPANTS,
                        SCHEDULED_AT,
                        createdAt,
                        createdAt
                ));

        // When & Then
        mockMvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"title":"오늘이 얼마 남지 않았어요","body":"사진을 자랑해 주세요!",
                        "repeat":"DAILY","target":"TOPIC_NON_PARTICIPANTS",
                        "scheduledAt":"2026-10-25T12:00:00Z"}
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reminderId").value(REMINDER_ID.toString()))
                .andExpect(jsonPath("$.title").value("오늘이 얼마 남지 않았어요"))
                .andExpect(jsonPath("$.body").value("사진을 자랑해 주세요!"))
                .andExpect(jsonPath("$.repeat").value("DAILY"))
                .andExpect(jsonPath("$.target").value("TOPIC_NON_PARTICIPANTS"))
                .andExpect(jsonPath("$.scheduledAt").value("2026-10-25T12:00:00Z"))
                .andExpect(jsonPath("$.createdAt").value(createdAt.toString()))
                .andExpect(jsonPath("$.updatedAt").value(createdAt.toString()));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"body\":\"본문\",\"repeat\":\"ONCE\",\"target\":\"ALL\",\"scheduledAt\":\"2026-10-25T12:00:00Z\"}",
            "{\"title\":\" \",\"body\":\"본문\",\"repeat\":\"ONCE\",\"target\":\"ALL\",\"scheduledAt\":\"2026-10-25T12:00:00Z\"}",
            "{\"title\":\"제목\",\"repeat\":\"ONCE\",\"target\":\"ALL\",\"scheduledAt\":\"2026-10-25T12:00:00Z\"}",
            "{\"title\":\"제목\",\"body\":\" \",\"repeat\":\"ONCE\",\"target\":\"ALL\",\"scheduledAt\":\"2026-10-25T12:00:00Z\"}",
            "{\"title\":\"제목\",\"body\":\"본문\",\"target\":\"ALL\",\"scheduledAt\":\"2026-10-25T12:00:00Z\"}",
            "{\"title\":\"제목\",\"body\":\"본문\",\"repeat\":\"WEEKLY\",\"target\":\"ALL\",\"scheduledAt\":\"2026-10-25T12:00:00Z\"}",
            "{\"title\":\"제목\",\"body\":\"본문\",\"repeat\":\"ONCE\",\"scheduledAt\":\"2026-10-25T12:00:00Z\"}",
            "{\"title\":\"제목\",\"body\":\"본문\",\"repeat\":\"ONCE\",\"target\":\"ADMINS\",\"scheduledAt\":\"2026-10-25T12:00:00Z\"}",
            "{\"title\":\"제목\",\"body\":\"본문\",\"repeat\":\"ONCE\",\"target\":\"ALL\"}",
            "{\"title\":\"제목\",\"body\":\"본문\",\"repeat\":\"ONCE\",\"target\":\"ALL\",\"scheduledAt\":\"2026-10-25 12:00\"}"
    })
    @DisplayName("제목·본문 누락이나 공백, 반복 방식·대상 누락이나 없는 값, 발송 시각 누락이나 형식 오류는 400으로 응답한다")
    void createReminder_invalidRequest_returnsBadRequest(String content) throws Exception {
        // When & Then
        mockMvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(content))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_ERROR"));
    }

    @Test
    @DisplayName("서비스의 업무 예외는 400과 BUSINESS_ERROR로 응답한다")
    void createReminder_businessException_returnsBadRequest() throws Exception {
        // Given
        given(reminderService.createReminder(
                any(),
                anyString(),
                anyString(),
                any(),
                any(),
                any()
        ))
                .willThrow(new BusinessException(ErrorCode.BUSINESS_ERROR,
                        "리마인더 발송 시각은 현재 시각보다 5분 넘게 과거일 수 없습니다."));

        // When & Then
        mockMvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"title":"오늘이 얼마 남지 않았어요","body":"사진을 자랑해 주세요!",
                        "repeat":"ONCE","target":"ALL","scheduledAt":"2026-10-01T09:00:00Z"}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_ERROR"))
                .andExpect(jsonPath("$.message")
                        .value("리마인더 발송 시각은 현재 시각보다 5분 넘게 과거일 수 없습니다."));
    }
}
