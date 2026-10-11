package com.chalkak.backend.admin.api.v1.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chalkak.backend.admin.api.support.AdminActorResolver;
import com.chalkak.backend.admin.api.support.AdminArgumentResolverWebMvcConfig;
import com.chalkak.backend.admin.api.support.AuthenticatedAdmin;
import com.chalkak.backend.admin.service.notification.TopicPushScheduleResult;
import com.chalkak.backend.admin.service.notification.AdminTopicPushScheduleService;
import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.exception.NotFoundException;
import com.chalkak.backend.exception.GlobalExceptionHandler;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
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

@WebMvcTest(AdminTopicPushScheduleController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({GlobalExceptionHandler.class, AdminArgumentResolverWebMvcConfig.class})
class AdminTopicPushScheduleControllerTest {

    private static final String PATH = "/api/v1/admin/topic-push-schedules";
    private static final UUID SCHEDULE_ID = UUID.fromString("019a0000-0000-7000-8000-000000000493");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminTopicPushScheduleService scheduleService;

    @MockitoBean
    private AdminActorResolver adminActorResolver;

    @BeforeEach
    void setUp() {
        given(adminActorResolver.resolve()).willReturn(new AuthenticatedAdmin(UUID.randomUUID()));
    }

    @Test
    @DisplayName("설정 목록 조회는 200과 설정별 시각·식별자·UTC 시각을 응답한다")
    void getSchedules_registeredSchedules_returnsScheduleList() throws Exception {
        // Given
        Instant createdAt = Instant.parse("2026-10-08T05:00:00Z");
        given(scheduleService.getSchedules()).willReturn(List.of(
                new TopicPushScheduleResult(SCHEDULE_ID, LocalTime.of(9, 0),
                        createdAt, createdAt)));

        // When & Then
        mockMvc.perform(get(PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.schedules.length()").value(1))
                .andExpect(jsonPath("$.schedules[0].scheduleId").value(SCHEDULE_ID.toString()))
                .andExpect(jsonPath("$.schedules[0].sendTime").value("09:00:00"))
                .andExpect(jsonPath("$.schedules[0].createdAt").value(createdAt.toString()))
                .andExpect(jsonPath("$.schedules[0].updatedAt").value(createdAt.toString()));
    }

    @Test
    @DisplayName("등록된 설정이 없으면 200과 빈 배열을 응답한다")
    void getSchedules_noSchedules_returnsEmptyList() throws Exception {
        // Given
        given(scheduleService.getSchedules()).willReturn(List.of());

        // When & Then
        mockMvc.perform(get(PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.schedules").isEmpty());
    }

    @Test
    @DisplayName("생성 성공 시 201과 저장된 설정을 응답한다")
    void createSchedule_validRequest_returnsCreatedSchedule() throws Exception {
        // Given
        Instant createdAt = Instant.parse("2026-10-08T05:00:00Z");
        given(scheduleService.createSchedule(LocalTime.of(9, 0)))
                .willReturn(new TopicPushScheduleResult(SCHEDULE_ID, LocalTime.of(9, 0),
                        createdAt, createdAt));

        // When & Then
        mockMvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON)
                .content("{\"sendTime\":\"09:00:00\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.scheduleId").value(SCHEDULE_ID.toString()))
                .andExpect(jsonPath("$.sendTime").value("09:00:00"))
                .andExpect(jsonPath("$.enabled").doesNotExist())
                .andExpect(jsonPath("$.createdAt").value(createdAt.toString()))
                .andExpect(jsonPath("$.updatedAt").value(createdAt.toString()));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}",
            "{\"sendTime\":null}",
            "{\"sendTime\":\"24:00:00\"}",
            "{\"sendTime\":\"23:59:60\"}",
            "{\"sendTime\":\"invalid\"}"
    })
    @DisplayName("발송 시각 누락·null·잘못된 시각은 400으로 거절한다")
    void createSchedule_invalidRequest_returnsBadRequest(String body) throws Exception {
        // When & Then
        mockMvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_ERROR"));
    }

    @Test
    @DisplayName("중복 등록 오류는 기존 400 BUSINESS_ERROR로 응답한다")
    void createSchedule_duplicateTime_returnsBusinessError() throws Exception {
        // Given
        given(scheduleService.createSchedule(LocalTime.of(9, 0)))
                .willThrow(new BusinessException(ErrorCode.BUSINESS_ERROR,
                        "해당 시각의 주제 푸시 설정이 이미 존재합니다."));

        // When & Then
        mockMvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON)
                .content("{\"sendTime\":\"09:00:00\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_ERROR"))
                .andExpect(jsonPath("$.message").value("해당 시각의 주제 푸시 설정이 이미 존재합니다."));
    }

    @Test
    @DisplayName("수정 성공 시 200과 변경된 설정을 응답한다")
    void updateSchedule_validRequest_returnsUpdatedSchedule() throws Exception {
        // Given
        Instant createdAt = Instant.parse("2026-10-08T05:00:00Z");
        Instant updatedAt = Instant.parse("2026-10-11T05:00:00Z");
        given(scheduleService.updateSchedule(SCHEDULE_ID, LocalTime.of(18, 30)))
                .willReturn(new TopicPushScheduleResult(SCHEDULE_ID, LocalTime.of(18, 30),
                        createdAt, updatedAt));

        // When & Then
        mockMvc.perform(put(PATH + "/" + SCHEDULE_ID).contentType(MediaType.APPLICATION_JSON)
                .content("{\"sendTime\":\"18:30:00\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scheduleId").value(SCHEDULE_ID.toString()))
                .andExpect(jsonPath("$.sendTime").value("18:30:00"))
                .andExpect(jsonPath("$.createdAt").value(createdAt.toString()))
                .andExpect(jsonPath("$.updatedAt").value(updatedAt.toString()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"sendTime\":null}", "{\"sendTime\":\"24:00:00\"}",
            "{\"sendTime\":\"invalid\"}"})
    @DisplayName("시각 누락·null·잘못된 시각의 수정 요청은 400으로 거절한다")
    void updateSchedule_invalidRequest_returnsBadRequest(String body) throws Exception {
        // When & Then
        mockMvc.perform(
                put(PATH + "/" + SCHEDULE_ID).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_ERROR"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"invalid", "1-1-1-1-1"})
    @DisplayName("표준 UUID가 아닌 설정 ID는 400으로 거절한다")
    void updateSchedule_invalidScheduleId_returnsBadRequest(String id) throws Exception {
        // When & Then
        mockMvc.perform(put(PATH + "/" + id).contentType(MediaType.APPLICATION_JSON)
                .content("{\"sendTime\":\"09:00:00\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_ERROR"));
    }

    @Test
    @DisplayName("없는 설정의 수정은 404로 응답한다")
    void updateSchedule_missingSchedule_returnsNotFound() throws Exception {
        // Given
        given(scheduleService.updateSchedule(SCHEDULE_ID, LocalTime.of(9, 0)))
                .willThrow(new NotFoundException(ErrorCode.BUSINESS_ERROR, "주제 푸시 설정을 찾을 수 없습니다."));

        // When & Then
        mockMvc.perform(put(PATH + "/" + SCHEDULE_ID).contentType(MediaType.APPLICATION_JSON)
                .content("{\"sendTime\":\"09:00:00\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_ERROR"));
    }

    @Test
    @DisplayName("다른 설정과 중복되는 수정은 400으로 응답한다")
    void updateSchedule_duplicateTime_returnsBusinessError() throws Exception {
        // Given
        given(scheduleService.updateSchedule(SCHEDULE_ID, LocalTime.of(9, 0)))
                .willThrow(new BusinessException(ErrorCode.BUSINESS_ERROR,
                        "해당 시각의 주제 푸시 설정이 이미 존재합니다."));

        // When & Then
        mockMvc.perform(put(PATH + "/" + SCHEDULE_ID).contentType(MediaType.APPLICATION_JSON)
                .content("{\"sendTime\":\"09:00:00\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_ERROR"));
    }

    @Test
    @DisplayName("삭제 성공 시 본문 없는 204를 응답한다")
    void deleteSchedule_existingSchedule_returnsNoContent() throws Exception {
        // When & Then
        mockMvc.perform(delete(PATH + "/" + SCHEDULE_ID))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
    }

    @ParameterizedTest
    @ValueSource(strings = {"invalid", "1-1-1-1-1"})
    @DisplayName("표준 UUID가 아닌 설정 ID의 삭제는 400으로 거절한다")
    void deleteSchedule_invalidScheduleId_returnsBadRequest(String id) throws Exception {
        // When & Then
        mockMvc.perform(delete(PATH + "/" + id))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_ERROR"));
    }

    @Test
    @DisplayName("없는 설정의 삭제는 404로 응답한다")
    void deleteSchedule_missingSchedule_returnsNotFound() throws Exception {
        // Given
        willThrow(new NotFoundException(ErrorCode.BUSINESS_ERROR, "주제 푸시 설정을 찾을 수 없습니다."))
                .given(scheduleService).deleteSchedule(SCHEDULE_ID);

        // When & Then
        mockMvc.perform(delete(PATH + "/" + SCHEDULE_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_ERROR"))
                .andExpect(jsonPath("$.message").value("주제 푸시 설정을 찾을 수 없습니다."));
    }
}
