package com.chalkak.backend.admin.api.v1.controller;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chalkak.backend.admin.api.support.AdminActorResolver;
import com.chalkak.backend.admin.api.support.AdminArgumentResolverWebMvcConfig;
import com.chalkak.backend.admin.api.support.AuthenticatedAdmin;
import com.chalkak.backend.admin.api.v1.converter.AdminFeedbackSortConverter;
import com.chalkak.backend.admin.repository.feedback.AdminFeedbackSort;
import com.chalkak.backend.admin.repository.user.AdminUserStatus;
import com.chalkak.backend.admin.service.feedback.AdminFeedbackListResult;
import com.chalkak.backend.admin.service.feedback.AdminFeedbackDetailResult;
import com.chalkak.backend.admin.service.feedback.AdminFeedbackQueryService;
import com.chalkak.backend.admin.service.feedback.AdminFeedbackCommandService;
import com.chalkak.backend.exception.GlobalExceptionHandler;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AdminFeedbackController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({
        GlobalExceptionHandler.class,
        AdminArgumentResolverWebMvcConfig.class,
        AdminFeedbackSortConverter.class
})
class AdminFeedbackControllerTest {

    private static final UUID ADMIN_ID = UUID.fromString("0198fd00-0000-7000-8000-000000000001");
    private static final UUID USER_ID = UUID.fromString("0198fd00-0000-7000-8000-000000000002");
    private static final UUID FEEDBACK_ID = UUID.fromString("0198fd00-0000-7000-8000-000000000003");
    private static final Instant CREATED_AT = Instant.parse("2026-09-16T01:00:00Z");
    private static final Instant DELETED_AT = Instant.parse("2026-11-03T09:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminFeedbackQueryService adminFeedbackQueryService;

    @MockitoBean
    private AdminFeedbackCommandService adminFeedbackCommandService;

    @MockitoBean
    private AdminActorResolver adminActorResolver;

    @BeforeEach
    void setUp() {
        given(adminActorResolver.resolve()).willReturn(new AuthenticatedAdmin(ADMIN_ID));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "invalid-time"})
    @DisplayName("답변 삭제 요청에 유효한 답변 등록 시각이 없으면 서비스 호출 전에 거절한다")
    void deleteFeedbackAnswer_invalidAnsweredAt_rejects(String answeredAt) throws Exception {
        var request = delete("/api/v1/admin/feedbacks/{feedbackId}/answer", FEEDBACK_ID);
        if (answeredAt != null) {
            request.param("answeredAt", answeredAt);
        }

        mockMvc.perform(request).andExpect(status().isBadRequest());
        then(adminFeedbackCommandService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("3000 코드 포인트를 넘긴 답변은 서비스에 전달하지 않는다")
    void answerFeedback_overLimit_rejectsBeforeServiceCall() throws Exception {
        // When & Then
        mockMvc.perform(post("/api/v1/admin/feedbacks/{feedbackId}/answer", FEEDBACK_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"answerContent\":\"" + "😀".repeat(3001) + "\"}"))
                .andExpect(status().isBadRequest());
        then(adminFeedbackCommandService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("앞뒤 공백을 빼고 3000 코드 포인트인 답변은 서비스에 전달한다")
    void answerFeedback_atLimitAfterStrip_callsService() throws Exception {
        // Given
        String answer = "  " + "😀".repeat(3000) + "  ";

        // When & Then
        mockMvc.perform(post("/api/v1/admin/feedbacks/{feedbackId}/answer", FEEDBACK_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"answerContent\":\"" + answer + "\"}"))
                .andExpect(status().isNoContent());
        then(adminFeedbackCommandService).should().answerFeedback(
                FEEDBACK_ID,
                ADMIN_ID,
                answer
        );
    }

    @Test
    @DisplayName("조회 조건을 생략하면 최신순 첫 페이지를 조회한다")
    void getFeedbacks_withoutParameters_usesDefaults() throws Exception {
        // Given
        given(adminFeedbackQueryService.getFeedbacks(
                null,
                AdminFeedbackSort.CREATED_AT_DESC,
                1,
                20
        ))
                .willReturn(listResult(null));

        // When & Then
        mockMvc.perform(get("/api/v1/admin/feedbacks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentPage").value(1))
                .andExpect(jsonPath("$.pageSize").value(20))
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.feedbacks[0].feedbackId").value(FEEDBACK_ID.toString()))
                .andExpect(jsonPath("$.feedbacks[0].content").value("사진 업로드가 느려요."))
                .andExpect(jsonPath("$.feedbacks[0].createdAt").value("2026-09-16T01:00:00Z"))
                .andExpect(jsonPath("$.feedbacks[0].deletedAt").value(nullValue()))
                .andExpect(jsonPath("$.feedbacks[0].author.userId").value(USER_ID.toString()))
                .andExpect(jsonPath("$.feedbacks[0].author.email").value("user@example.com"))
                .andExpect(jsonPath("$.feedbacks[0].author.status").value("ACTIVE"))
                .andExpect(jsonPath("$.feedbacks[0].author.appVersion").value("1.2.3"));

        then(adminFeedbackQueryService).should()
                .getFeedbacks(
                        null,
                        AdminFeedbackSort.CREATED_AT_DESC,
                        1,
                        20
                );
    }

    @Test
    @DisplayName("오래된 순 정렬과 페이지 조건을 전달한다")
    void getFeedbacks_withParameters_passesThemToService() throws Exception {
        // Given
        given(adminFeedbackQueryService.getFeedbacks(
                null,
                AdminFeedbackSort.CREATED_AT_ASC,
                2,
                50
        ))
                .willReturn(listResult(null));

        // When & Then
        mockMvc.perform(get("/api/v1/admin/feedbacks")
                .param("sort", "createdAtAsc")
                .param("page", "2")
                .param("pageSize", "50"))
                .andExpect(status().isOk());

        then(adminFeedbackQueryService).should()
                .getFeedbacks(
                        null,
                        AdminFeedbackSort.CREATED_AT_ASC,
                        2,
                        50
                );
    }

    @Test
    @DisplayName("사용자 필터를 전달하고 문의 삭제 시각을 반환한다")
    void getFeedbacks_userFilter_returnsDeletionTime() throws Exception {
        // Given
        given(adminFeedbackQueryService.getFeedbacks(
                USER_ID,
                AdminFeedbackSort.CREATED_AT_DESC,
                1,
                20
        )).willReturn(listResult(DELETED_AT));

        // When & Then
        mockMvc.perform(get("/api/v1/admin/feedbacks")
                .param("userId", USER_ID.toString().toUpperCase(Locale.ROOT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.feedbacks[0].author.userId").value(USER_ID.toString()))
                .andExpect(jsonPath("$.feedbacks[0].deletedAt").value(DELETED_AT.toString()));
        then(adminFeedbackQueryService).should().getFeedbacks(
                USER_ID,
                AdminFeedbackSort.CREATED_AT_DESC,
                1,
                20
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"invalid-id", "1-1-1-1-1", "", " "})
    @DisplayName("잘못되거나 축약되거나 비어 있는 사용자 ID는 조회 전에 400으로 거절한다")
    void getFeedbacks_invalidUserId_returnsBadRequest(String userId) throws Exception {
        // When & Then
        mockMvc.perform(get("/api/v1/admin/feedbacks").param("userId", userId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_ERROR"))
                .andExpect(jsonPath("$.message").value("ID 형식이 올바르지 않습니다."));
        then(adminFeedbackQueryService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("정렬 조건이 올바르지 않으면 400을 반환한다")
    void getFeedbacks_invalidSort_returnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/admin/feedbacks").param("sort", "unknown"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_ERROR"))
                .andExpect(jsonPath("$.message").value("sort: 요청 값의 형식이 올바르지 않습니다."));

        then(adminFeedbackQueryService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("페이지당 피드백 수가 100을 넘으면 400을 반환한다")
    void getFeedbacks_pageSizeOverMax_returnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/admin/feedbacks").param("pageSize", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_ERROR"))
                .andExpect(jsonPath("$.message").value("조회 조건이 올바르지 않습니다."));

        then(adminFeedbackQueryService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("관리자 상세 응답은 문의 원문·시각과 기존 작성자 필드를 제공한다")
    void getFeedback_validId_returnsDetailContract() throws Exception {
        // Given
        given(adminFeedbackQueryService.getFeedback(FEEDBACK_ID))
                .willReturn(new AdminFeedbackDetailResult(
                        FEEDBACK_ID,
                        "문의 원문",
                        CREATED_AT,
                        null,
                        null,
                        DELETED_AT,
                        new AdminFeedbackListResult.AuthorSummary(
                                USER_ID,
                                "user@example.com",
                                AdminUserStatus.ACTIVE,
                                "1.2.3"
                        )
                ));

        // When & Then
        mockMvc.perform(get("/api/v1/admin/feedbacks/{feedbackId}", FEEDBACK_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.feedbackId").value(FEEDBACK_ID.toString()))
                .andExpect(jsonPath("$.content").value("문의 원문"))
                .andExpect(jsonPath("$.createdAt").value(CREATED_AT.toString()))
                .andExpect(jsonPath("$.deletedAt").value(DELETED_AT.toString()))
                .andExpect(jsonPath("$.author.userId").value(USER_ID.toString()))
                .andExpect(jsonPath("$.author.email").value("user@example.com"))
                .andExpect(jsonPath("$.author.status").value("ACTIVE"))
                .andExpect(jsonPath("$.author.appVersion").value("1.2.3"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"invalid-id", "1-1-1-1-1"})
    @DisplayName("관리자 상세의 잘못되거나 축약된 문의 ID는 조회 전에 400으로 거절한다")
    void getFeedback_invalidId_returnsBadRequest(String feedbackId) throws Exception {
        // When & Then
        mockMvc.perform(get("/api/v1/admin/feedbacks/{feedbackId}", feedbackId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_ERROR"))
                .andExpect(jsonPath("$.message").value("ID 형식이 올바르지 않습니다."));
        then(adminFeedbackQueryService).shouldHaveNoInteractions();
    }

    @ParameterizedTest
    @ValueSource(strings = {"invalid-id", "1-1-1-1-1"})
    @DisplayName("관리자 삭제의 잘못되거나 축약된 ID는 변경 전에 400으로 거절한다")
    void deleteFeedback_invalidId_returnsBadRequest(String feedbackId) throws Exception {
        // When & Then
        mockMvc.perform(delete("/api/v1/admin/feedbacks/{feedbackId}", feedbackId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_ERROR"));
        then(adminFeedbackCommandService).shouldHaveNoInteractions();
    }

    private AdminFeedbackListResult listResult(Instant deletedAt) {
        return new AdminFeedbackListResult(
                1,
                20,
                false,
                List.of(new AdminFeedbackListResult.FeedbackSummary(
                        FEEDBACK_ID,
                        "사진 업로드가 느려요.",
                        CREATED_AT,
                        deletedAt,
                        new AdminFeedbackListResult.AuthorSummary(
                                USER_ID,
                                "user@example.com",
                                AdminUserStatus.ACTIVE,
                                "1.2.3"
                        )
                ))
        );
    }
}
