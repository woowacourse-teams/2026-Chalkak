package com.chalkak.backend.admin.api.v1.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chalkak.backend.admin.api.support.AdminActorResolver;
import com.chalkak.backend.admin.api.support.AdminArgumentResolverWebMvcConfig;
import com.chalkak.backend.admin.api.support.AuthenticatedAdmin;
import com.chalkak.backend.admin.api.v1.converter.AdminFeedbackSortConverter;
import com.chalkak.backend.admin.service.AdminFeedbackListResult;
import com.chalkak.backend.admin.service.AdminFeedbackQueryService;
import com.chalkak.backend.admin.service.AdminFeedbackSort;
import com.chalkak.backend.admin.service.AdminUserStatus;
import com.chalkak.backend.exception.GlobalExceptionHandler;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
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

    private static final UUID ADMIN_ID =
            UUID.fromString("0198fd00-0000-7000-8000-000000000001");
    private static final UUID USER_ID =
            UUID.fromString("0198fd00-0000-7000-8000-000000000002");
    private static final UUID FEEDBACK_ID =
            UUID.fromString("0198fd00-0000-7000-8000-000000000003");
    private static final Instant CREATED_AT = Instant.parse("2026-09-16T01:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminFeedbackQueryService adminFeedbackQueryService;

    @MockitoBean
    private AdminActorResolver adminActorResolver;

    @BeforeEach
    void setUp() {
        given(adminActorResolver.resolve()).willReturn(new AuthenticatedAdmin(ADMIN_ID));
    }

    @Test
    @DisplayName("조회 조건을 생략하면 최신순 첫 페이지를 조회한다")
    void getFeedbacks_withoutParameters_usesDefaults() throws Exception {
        // Given
        given(adminFeedbackQueryService.getFeedbacks(
                AdminFeedbackSort.CREATED_AT_DESC, 1, 20))
                .willReturn(listResult());

        // When & Then
        mockMvc.perform(get("/api/v1/admin/feedbacks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentPage").value(1))
                .andExpect(jsonPath("$.pageSize").value(20))
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.feedbacks[0].feedbackId").value(FEEDBACK_ID.toString()))
                .andExpect(jsonPath("$.feedbacks[0].content").value("사진 업로드가 느려요."))
                .andExpect(jsonPath("$.feedbacks[0].createdAt").value("2026-09-16T01:00:00Z"))
                .andExpect(jsonPath("$.feedbacks[0].author.userId").value(USER_ID.toString()))
                .andExpect(jsonPath("$.feedbacks[0].author.email").value("user@example.com"))
                .andExpect(jsonPath("$.feedbacks[0].author.status").value("ACTIVE"))
                .andExpect(jsonPath("$.feedbacks[0].author.appVersion").value("1.2.3"));

        then(adminFeedbackQueryService).should()
                .getFeedbacks(AdminFeedbackSort.CREATED_AT_DESC, 1, 20);
    }

    @Test
    @DisplayName("오래된 순 정렬과 페이지 조건을 전달한다")
    void getFeedbacks_withParameters_passesThemToService() throws Exception {
        // Given
        given(adminFeedbackQueryService.getFeedbacks(
                AdminFeedbackSort.CREATED_AT_ASC, 2, 50))
                .willReturn(listResult());

        // When & Then
        mockMvc.perform(get("/api/v1/admin/feedbacks")
                        .param("sort", "createdAtAsc")
                        .param("page", "2")
                        .param("pageSize", "50"))
                .andExpect(status().isOk());

        then(adminFeedbackQueryService).should()
                .getFeedbacks(AdminFeedbackSort.CREATED_AT_ASC, 2, 50);
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

    private AdminFeedbackListResult listResult() {
        return new AdminFeedbackListResult(
                1,
                20,
                false,
                List.of(new AdminFeedbackListResult.FeedbackSummary(
                        FEEDBACK_ID,
                        "사진 업로드가 느려요.",
                        CREATED_AT,
                        new AdminFeedbackListResult.AuthorSummary(
                                USER_ID,
                                "user@example.com",
                                AdminUserStatus.ACTIVE,
                                "1.2.3"))));
    }
}
