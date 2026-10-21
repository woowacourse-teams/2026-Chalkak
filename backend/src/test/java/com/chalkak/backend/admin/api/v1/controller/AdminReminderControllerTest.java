package com.chalkak.backend.admin.api.v1.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chalkak.backend.admin.api.support.AdminActorResolver;
import com.chalkak.backend.admin.api.support.AdminArgumentResolverWebMvcConfig;
import com.chalkak.backend.admin.api.support.AuthenticatedAdmin;
import com.chalkak.backend.admin.service.notification.AdminReminderListResult;
import com.chalkak.backend.admin.service.notification.AdminReminderResult;
import com.chalkak.backend.admin.service.notification.AdminReminderService;
import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.exception.GlobalExceptionHandler;
import com.chalkak.backend.exception.NotFoundException;
import com.chalkak.backend.notification.domain.ReminderRepeat;
import com.chalkak.backend.notification.domain.ReminderTarget;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
    @DisplayName("목록 조회는 200과 페이지 정보·리마인더 목록을 응답한다")
    void getReminders_validRequest_returnsReminderList() throws Exception {
        // Given
        Instant createdAt = Instant.parse("2026-10-21T09:00:00Z");
        given(reminderService.getReminders(2, 10)).willReturn(new AdminReminderListResult(
                2,
                10,
                true,
                List.of(new AdminReminderResult(
                        REMINDER_ID,
                        "오늘이 얼마 남지 않았어요",
                        "사진을 자랑해 주세요!",
                        ReminderRepeat.DAILY,
                        ReminderTarget.TOPIC_NON_PARTICIPANTS,
                        SCHEDULED_AT,
                        createdAt,
                        createdAt
                ))
        ));

        // When & Then
        mockMvc.perform(get(PATH).param("page", "2").param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentPage").value(2))
                .andExpect(jsonPath("$.pageSize").value(10))
                .andExpect(jsonPath("$.hasNext").value(true))
                .andExpect(jsonPath("$.reminders.length()").value(1))
                .andExpect(jsonPath("$.reminders[0].reminderId").value(REMINDER_ID.toString()))
                .andExpect(jsonPath("$.reminders[0].title").value("오늘이 얼마 남지 않았어요"))
                .andExpect(jsonPath("$.reminders[0].body").value("사진을 자랑해 주세요!"))
                .andExpect(jsonPath("$.reminders[0].repeat").value("DAILY"))
                .andExpect(jsonPath("$.reminders[0].target").value("TOPIC_NON_PARTICIPANTS"))
                .andExpect(jsonPath("$.reminders[0].scheduledAt").value("2026-10-25T12:00:00Z"));
    }

    @Test
    @DisplayName("페이지 조건을 생략하면 1페이지·20개로 조회한다")
    void getReminders_omittedPagination_usesDefaults() throws Exception {
        // Given
        given(reminderService.getReminders(1, 20)).willReturn(new AdminReminderListResult(
                1,
                20,
                false,
                List.of()
        ));

        // When & Then
        mockMvc.perform(get(PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentPage").value(1))
                .andExpect(jsonPath("$.pageSize").value(20))
                .andExpect(jsonPath("$.reminders").isEmpty());
    }

    @ParameterizedTest
    @CsvSource({"page, 0", "pageSize, 0", "pageSize, 101"})
    @DisplayName("페이지 번호 0이나 페이지 크기 0·101은 400으로 응답한다")
    void getReminders_invalidPagination_returnsBadRequest(String name, String value)
            throws Exception {
        // When & Then
        mockMvc.perform(get(PATH).param(name, value))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_ERROR"));
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
    @DisplayName("수정 성공 시 200과 수정된 리마인더를 응답한다")
    void updateReminder_validRequest_returnsUpdatedReminder() throws Exception {
        // Given
        Instant createdAt = Instant.parse("2026-10-21T09:00:00Z");
        Instant updatedAt = Instant.parse("2026-10-21T10:00:00Z");
        given(reminderService.updateReminder(
                REMINDER_ID,
                ADMIN_ID,
                "새 제목",
                "새 본문",
                ReminderRepeat.ONCE,
                ReminderTarget.ALL,
                SCHEDULED_AT
        )).willReturn(new AdminReminderResult(
                REMINDER_ID,
                "새 제목",
                "새 본문",
                ReminderRepeat.ONCE,
                ReminderTarget.ALL,
                SCHEDULED_AT,
                createdAt,
                updatedAt
        ));

        // When & Then
        mockMvc.perform(put(PATH + "/" + REMINDER_ID).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"title":"새 제목","body":"새 본문","repeat":"ONCE","target":"ALL",
                        "scheduledAt":"2026-10-25T12:00:00Z"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reminderId").value(REMINDER_ID.toString()))
                .andExpect(jsonPath("$.title").value("새 제목"))
                .andExpect(jsonPath("$.repeat").value("ONCE"))
                .andExpect(jsonPath("$.updatedAt").value(updatedAt.toString()));
    }

    @Test
    @DisplayName("수정 경로의 리마인더 ID가 UUID 형식이 아니면 400으로 응답한다")
    void updateReminder_invalidId_returnsBadRequest() throws Exception {
        // When & Then
        mockMvc.perform(put(PATH + "/not-a-uuid").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"title":"새 제목","body":"새 본문","repeat":"ONCE","target":"ALL",
                        "scheduledAt":"2026-10-25T12:00:00Z"}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_ERROR"));
    }

    @Test
    @DisplayName("수정 요청 본문이 등록 기준을 어기면 400으로 응답한다")
    void updateReminder_invalidRequest_returnsBadRequest() throws Exception {
        // When & Then
        mockMvc.perform(put(PATH + "/" + REMINDER_ID).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"title":" ","body":"새 본문","repeat":"ONCE","target":"ALL",
                        "scheduledAt":"2026-10-25T12:00:00Z"}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_ERROR"));
    }

    @Test
    @DisplayName("없는 리마인더를 수정하면 404로 응답한다")
    void updateReminder_unknownReminder_returnsNotFound() throws Exception {
        // Given
        given(reminderService.updateReminder(
                any(),
                any(),
                anyString(),
                anyString(),
                any(),
                any(),
                any()
        )).willThrow(new NotFoundException(ErrorCode.BUSINESS_ERROR, "리마인더를 찾을 수 없습니다."));

        // When & Then
        mockMvc.perform(put(PATH + "/" + REMINDER_ID).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"title":"새 제목","body":"새 본문","repeat":"ONCE","target":"ALL",
                        "scheduledAt":"2026-10-25T12:00:00Z"}
                        """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_ERROR"))
                .andExpect(jsonPath("$.message").value("리마인더를 찾을 수 없습니다."));
    }

    @Test
    @DisplayName("발송 시각이 지난 일회성 리마인더의 수정 거절은 400으로 응답한다")
    void updateReminder_businessException_returnsBadRequest() throws Exception {
        // Given
        given(reminderService.updateReminder(
                any(),
                any(),
                anyString(),
                anyString(),
                any(),
                any(),
                any()
        )).willThrow(new BusinessException(ErrorCode.BUSINESS_ERROR,
                "발송 시각이 지난 일회성 리마인더는 수정할 수 없습니다."));

        // When & Then
        mockMvc.perform(put(PATH + "/" + REMINDER_ID).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"title":"새 제목","body":"새 본문","repeat":"ONCE","target":"ALL",
                        "scheduledAt":"2026-10-25T12:00:00Z"}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("발송 시각이 지난 일회성 리마인더는 수정할 수 없습니다."));
    }

    @Test
    @DisplayName("삭제 성공 시 204로 응답한다")
    void deleteReminder_existingReminder_returnsNoContent() throws Exception {
        // When & Then
        mockMvc.perform(delete(PATH + "/" + REMINDER_ID))
                .andExpect(status().isNoContent());
        verify(reminderService).deleteReminder(REMINDER_ID, ADMIN_ID);
    }

    @Test
    @DisplayName("삭제 경로의 리마인더 ID가 UUID 형식이 아니면 400으로 응답한다")
    void deleteReminder_invalidId_returnsBadRequest() throws Exception {
        // When & Then
        mockMvc.perform(delete(PATH + "/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_ERROR"));
    }

    @Test
    @DisplayName("없는 리마인더를 삭제하면 404로 응답한다")
    void deleteReminder_unknownReminder_returnsNotFound() throws Exception {
        // Given
        willThrow(new NotFoundException(ErrorCode.BUSINESS_ERROR, "리마인더를 찾을 수 없습니다."))
                .given(reminderService).deleteReminder(REMINDER_ID, ADMIN_ID);

        // When & Then
        mockMvc.perform(delete(PATH + "/" + REMINDER_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("리마인더를 찾을 수 없습니다."));
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
