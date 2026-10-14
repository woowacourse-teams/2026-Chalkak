package com.chalkak.backend.admin.api.v1.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chalkak.backend.admin.api.support.AdminActorResolver;
import com.chalkak.backend.admin.api.support.AdminArgumentResolverWebMvcConfig;
import com.chalkak.backend.admin.api.support.AuthenticatedAdmin;
import com.chalkak.backend.admin.service.notification.AdminNoticeListResult;
import com.chalkak.backend.admin.service.notification.AdminNoticeResult;
import com.chalkak.backend.admin.service.notification.AdminNoticeService;
import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.exception.GlobalExceptionHandler;
import com.chalkak.backend.exception.NotFoundException;
import com.chalkak.backend.notification.domain.NoticeStatus;
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
    @DisplayName("목록 조회는 200과 페이지 정보·공지 목록을 응답한다")
    void getNotices_validRequest_returnsNoticeList() throws Exception {
        // Given
        Instant createdAt = Instant.parse("2026-10-12T09:00:00Z");
        given(noticeService.getNotices(2, 10)).willReturn(new AdminNoticeListResult(2, 10, true,
                List.of(new AdminNoticeResult(NOTICE_ID, "서비스 점검 안내", "점검합니다.",
                        SCHEDULED_AT, NoticeStatus.SCHEDULED, null, createdAt, createdAt))));

        // When & Then
        mockMvc.perform(get(PATH).param("page", "2").param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentPage").value(2))
                .andExpect(jsonPath("$.pageSize").value(10))
                .andExpect(jsonPath("$.hasNext").value(true))
                .andExpect(jsonPath("$.notices.length()").value(1))
                .andExpect(jsonPath("$.notices[0].noticeId").value(NOTICE_ID.toString()))
                .andExpect(jsonPath("$.notices[0].title").value("서비스 점검 안내"))
                .andExpect(jsonPath("$.notices[0].scheduledAt").value("2026-10-19T09:00:00Z"))
                .andExpect(jsonPath("$.notices[0].status").value("SCHEDULED"))
                .andExpect(jsonPath("$.notices[0].distributedAt").isEmpty());
    }

    @Test
    @DisplayName("페이지 조건을 생략하면 1페이지·20개로 조회한다")
    void getNotices_omittedPagination_usesDefaults() throws Exception {
        // Given
        given(noticeService.getNotices(1, 20))
                .willReturn(new AdminNoticeListResult(1, 20, false, List.of()));

        // When & Then
        mockMvc.perform(get(PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentPage").value(1))
                .andExpect(jsonPath("$.pageSize").value(20))
                .andExpect(jsonPath("$.notices").isEmpty());
    }

    @ParameterizedTest
    @CsvSource({"page, 0", "pageSize, 0", "pageSize, 101"})
    @DisplayName("페이지 번호 0이나 페이지 크기 0·101은 400으로 응답한다")
    void getNotices_invalidPagination_returnsBadRequest(String name, String value)
            throws Exception {
        // When & Then
        mockMvc.perform(get(PATH).param(name, value))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_ERROR"));
    }

    @Test
    @DisplayName("상세 조회는 200과 공지를 응답한다")
    void getNotice_existingNotice_returnsNotice() throws Exception {
        // Given
        Instant createdAt = Instant.parse("2026-10-12T09:00:00Z");
        given(noticeService.getNotice(NOTICE_ID)).willReturn(new AdminNoticeResult(NOTICE_ID,
                "서비스 점검 안내", "점검합니다.", SCHEDULED_AT, NoticeStatus.DISTRIBUTED,
                SCHEDULED_AT, createdAt, createdAt));

        // When & Then
        mockMvc.perform(get(PATH + "/" + NOTICE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.noticeId").value(NOTICE_ID.toString()))
                .andExpect(jsonPath("$.body").value("점검합니다."))
                .andExpect(jsonPath("$.status").value("DISTRIBUTED"))
                .andExpect(jsonPath("$.distributedAt").value("2026-10-19T09:00:00Z"));
    }

    @Test
    @DisplayName("상세 조회의 잘못된 UUID는 400으로 응답한다")
    void getNotice_invalidId_returnsBadRequest() throws Exception {
        // When & Then
        mockMvc.perform(get(PATH + "/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_ERROR"));
    }

    @Test
    @DisplayName("없는 공지의 상세 조회는 404로 응답한다")
    void getNotice_unknownNotice_returnsNotFound() throws Exception {
        // Given
        given(noticeService.getNotice(NOTICE_ID)).willThrow(
                new NotFoundException(ErrorCode.BUSINESS_ERROR, "공지를 찾을 수 없습니다."));

        // When & Then
        mockMvc.perform(get(PATH + "/" + NOTICE_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_ERROR"))
                .andExpect(jsonPath("$.message").value("공지를 찾을 수 없습니다."));
    }

    @Test
    @DisplayName("등록 성공 시 201과 저장된 공지를 응답한다")
    void createNotice_validRequest_returnsCreatedNotice() throws Exception {
        // Given
        Instant createdAt = Instant.parse("2026-10-12T09:00:00Z");
        given(noticeService.createNotice(ADMIN_ID, "서비스 점검 안내", "점검합니다.", SCHEDULED_AT))
                .willReturn(new AdminNoticeResult(NOTICE_ID, "서비스 점검 안내", "점검합니다.",
                        SCHEDULED_AT, NoticeStatus.SCHEDULED, null, createdAt, createdAt));

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

    @Test
    @DisplayName("수정 성공 시 200과 수정된 공지를 응답한다")
    void updateNotice_validRequest_returnsUpdatedNotice() throws Exception {
        // Given
        Instant createdAt = Instant.parse("2026-10-12T09:00:00Z");
        Instant updatedAt = Instant.parse("2026-10-13T09:00:00Z");
        given(noticeService.updateNotice(NOTICE_ID, ADMIN_ID, "점검 일정 변경", "점검합니다.",
                SCHEDULED_AT)).willReturn(new AdminNoticeResult(NOTICE_ID, "점검 일정 변경",
                        "점검합니다.", SCHEDULED_AT, NoticeStatus.SCHEDULED, null, createdAt,
                        updatedAt));

        // When & Then
        mockMvc.perform(put(PATH + "/" + NOTICE_ID).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"title":"점검 일정 변경","body":"점검합니다.",
                        "scheduledAt":"2026-10-19T09:00:00Z"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.noticeId").value(NOTICE_ID.toString()))
                .andExpect(jsonPath("$.title").value("점검 일정 변경"))
                .andExpect(jsonPath("$.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.updatedAt").value(updatedAt.toString()));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"title\":\" \",\"body\":\"점검합니다.\",\"scheduledAt\":\"2026-10-19T09:00:00Z\"}",
            "{\"title\":\"안내\",\"scheduledAt\":\"2026-10-19T09:00:00Z\"}",
            "{\"title\":\"안내\",\"body\":\"점검합니다.\",\"scheduledAt\":\"2026-10-19 09:00\"}"
    })
    @DisplayName("수정 요청의 제목 공백, 본문 누락, 예약 시각 형식 오류는 400으로 응답한다")
    void updateNotice_invalidRequest_returnsBadRequest(String content) throws Exception {
        // When & Then
        mockMvc.perform(put(PATH + "/" + NOTICE_ID).contentType(MediaType.APPLICATION_JSON)
                .content(content))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_ERROR"));
    }

    @Test
    @DisplayName("수정 요청의 잘못된 UUID는 400으로 응답한다")
    void updateNotice_invalidId_returnsBadRequest() throws Exception {
        // When & Then
        mockMvc.perform(put(PATH + "/not-a-uuid").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"title":"점검 일정 변경","body":"점검합니다.",
                        "scheduledAt":"2026-10-19T09:00:00Z"}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_ERROR"));
    }

    @Test
    @DisplayName("배포된 공지의 수정은 400과 BUSINESS_ERROR로 응답한다")
    void updateNotice_distributedNotice_returnsBadRequest() throws Exception {
        // Given
        given(noticeService.updateNotice(any(), any(), anyString(), anyString(), any()))
                .willThrow(new BusinessException(ErrorCode.BUSINESS_ERROR,
                        "배포된 공지는 수정할 수 없습니다."));

        // When & Then
        mockMvc.perform(put(PATH + "/" + NOTICE_ID).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"title":"점검 일정 변경","body":"점검합니다.",
                        "scheduledAt":"2026-10-19T09:00:00Z"}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("배포된 공지는 수정할 수 없습니다."));
    }

    @Test
    @DisplayName("없는 공지의 수정은 404로 응답한다")
    void updateNotice_unknownNotice_returnsNotFound() throws Exception {
        // Given
        given(noticeService.updateNotice(any(), any(), anyString(), anyString(), any()))
                .willThrow(new NotFoundException(ErrorCode.BUSINESS_ERROR, "공지를 찾을 수 없습니다."));

        // When & Then
        mockMvc.perform(put(PATH + "/" + NOTICE_ID).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"title":"점검 일정 변경","body":"점검합니다.",
                        "scheduledAt":"2026-10-19T09:00:00Z"}
                        """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("공지를 찾을 수 없습니다."));
    }
}
