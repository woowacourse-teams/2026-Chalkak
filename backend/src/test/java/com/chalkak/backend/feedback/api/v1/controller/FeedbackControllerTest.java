package com.chalkak.backend.feedback.api.v1.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chalkak.backend.exception.GlobalExceptionHandler;
import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.feedback.domain.Feedback;
import com.chalkak.backend.feedback.service.FeedbackService;
import com.chalkak.backend.feedback.service.FeedbackListResult;
import com.chalkak.backend.feedback.service.FeedbackDetailResult;
import com.chalkak.backend.feedback.service.FeedbackSubmissionResult;
import com.chalkak.backend.support.WithMockLoginUser;
import java.time.Instant;
import java.util.UUID;
import java.util.List;
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

@WebMvcTest(FeedbackController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class FeedbackControllerTest {

    private static final String USER_ID_VALUE = "0198f6c1-62ba-7d30-8b12-0f733b6570a1";
    private static final UUID USER_ID = UUID.fromString(USER_ID_VALUE);
    private static final UUID FEEDBACK_ID = UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6570d4");
    private static final Instant CREATED_AT = Instant.parse("2026-09-16T01:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FeedbackService feedbackService;

    @Test
    @WithMockLoginUser(USER_ID_VALUE)
    @DisplayName("본인 문의 삭제는 응답 본문 없이 204를 반환한다")
    void deleteFeedback_validIdentifier_returnsNoContent() throws Exception {
        // When & Then
        mockMvc.perform(delete("/api/v1/feedbacks/{feedbackId}", FEEDBACK_ID))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
        verify(feedbackService).deleteFeedback(USER_ID, FEEDBACK_ID);
    }

    @ParameterizedTest
    @ValueSource(strings = {"invalid-id", "1-1-1-1-1"})
    @WithMockLoginUser(USER_ID_VALUE)
    @DisplayName("삭제할 문의 ID 형식이 잘못되면 400을 반환하고 삭제하지 않는다")
    void deleteFeedback_invalidIdentifier_returnsBadRequest(String feedbackId) throws Exception {
        // When & Then
        mockMvc.perform(delete("/api/v1/feedbacks/{feedbackId}", feedbackId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_ERROR"));
        verify(feedbackService, never()).deleteFeedback(any(), any());
    }

    @Test
    @WithMockLoginUser(USER_ID_VALUE)
    @DisplayName("본인 문의 상세는 식별자·내용·UTC 접수 시각을 반환한다")
    void getFeedback_ownFeedback_returnsDetailContract() throws Exception {
        // Given
        given(feedbackService.getFeedback(USER_ID, FEEDBACK_ID))
                .willReturn(new FeedbackDetailResult(
                        FEEDBACK_ID,
                        "내 문의",
                        CREATED_AT,
                        null,
                        null
                ));

        // When & Then
        mockMvc.perform(get("/api/v1/feedbacks/{feedbackId}", FEEDBACK_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.feedbackId").value(FEEDBACK_ID.toString()))
                .andExpect(jsonPath("$.content").value("내 문의"))
                .andExpect(jsonPath("$.createdAt").value(CREATED_AT.toString()))
                .andExpect(jsonPath("$.userId").doesNotExist())
                .andExpect(jsonPath("$.deletedAt").doesNotExist());
    }

    @ParameterizedTest
    @ValueSource(strings = {"invalid-id", "1-1-1-1-1"})
    @WithMockLoginUser(USER_ID_VALUE)
    @DisplayName("문의 식별자가 잘못됐거나 축약된 UUID면 400을 반환하고 조회하지 않는다")
    void getFeedback_invalidIdentifier_returnsBadRequest(String feedbackId) throws Exception {
        // When & Then
        mockMvc.perform(get("/api/v1/feedbacks/{feedbackId}", feedbackId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_ERROR"))
                .andExpect(jsonPath("$.message").value("ID 형식이 올바르지 않습니다."));
        verify(feedbackService, never()).getFeedback(any(), any());
    }

    @Test
    @WithMockLoginUser(USER_ID_VALUE)
    @DisplayName("본인 문의 목록은 기본 페이지와 공개 필드를 반환한다")
    void getFeedbacks_defaultPagination_returnsOwnFeedbackContract() throws Exception {
        // Given
        given(feedbackService.getFeedbacks(
                USER_ID,
                1,
                20
        )).willReturn(new FeedbackListResult(
                1,
                20,
                false,
                List.of(new FeedbackListResult.FeedbackSummary(
                        FEEDBACK_ID,
                        "문의합니다",
                        CREATED_AT,
                        false
                ))
        ));

        // When & Then
        mockMvc.perform(get("/api/v1/feedbacks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentPage").value(1))
                .andExpect(jsonPath("$.pageSize").value(20))
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.feedbacks[0].feedbackId").value(FEEDBACK_ID.toString()))
                .andExpect(jsonPath("$.feedbacks[0].content").value("문의합니다"))
                .andExpect(jsonPath("$.feedbacks[0].createdAt").value(CREATED_AT.toString()))
                .andExpect(jsonPath("$.feedbacks[0].hasAnswer").value(false))
                .andExpect(jsonPath("$.feedbacks[0].userId").doesNotExist());
    }

    @ParameterizedTest
    @CsvSource({"0,20", "-1,20", "1,0", "1,-1", "1,101", "wrong,20",
            "21474838,100", "2147483647,2"})
    @WithMockLoginUser(USER_ID_VALUE)
    @DisplayName("잘못된 목록 조회 조건은 400으로 거절한다")
    void getFeedbacks_invalidParameter_returnsBadRequest(String page, String pageSize)
            throws Exception {
        // When & Then
        mockMvc.perform(get("/api/v1/feedbacks")
                .param("page", page)
                .param("pageSize", pageSize))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_ERROR"));
        verify(feedbackService, never()).getFeedbacks(
                any(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt()
        );
    }

    @ParameterizedTest
    @CsvSource({"1,1", "2,100", "2147483647,1", "21474837,100"})
    @WithMockLoginUser(USER_ID_VALUE)
    @DisplayName("목록 요청 DTO는 건수와 조회 위치의 허용 경계를 통과시킨다")
    void getFeedbacks_pageSizeBoundary_returnsOk(int page, int pageSize) throws Exception {
        // Given
        given(feedbackService.getFeedbacks(
                USER_ID,
                page,
                pageSize
        )).willReturn(new FeedbackListResult(
                page,
                pageSize,
                false,
                List.of()
        ));

        // When & Then
        mockMvc.perform(get("/api/v1/feedbacks")
                .param("page", String.valueOf(page))
                .param("pageSize", String.valueOf(pageSize)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentPage").value(page))
                .andExpect(jsonPath("$.pageSize").value(pageSize));
    }

    @Test
    @WithMockLoginUser(USER_ID_VALUE)
    @DisplayName("피드백을 접수하고 201과 식별자를 반환한다")
    void submitFeedback_validRequest_returnsCreated() throws Exception {
        given(feedbackService.submit(USER_ID, "사진 업로드가 느려요."))
                .willReturn(new FeedbackSubmissionResult(FEEDBACK_ID, CREATED_AT));

        mockMvc.perform(post("/api/v1/feedbacks")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"사진 업로드가 느려요.\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.feedbackId").value(FEEDBACK_ID.toString()))
                .andExpect(jsonPath("$.createdAt").value("2026-09-16T01:00:00Z"));

        verify(feedbackService).submit(USER_ID, "사진 업로드가 느려요.");
    }

    @Test
    @WithMockLoginUser(USER_ID_VALUE)
    @DisplayName("내용이 공백뿐이면 400을 반환한다")
    void submitFeedback_blankContent_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/feedbacks")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_ERROR"))
                .andExpect(jsonPath("$.message").value("피드백 내용이 필요합니다."));

        verify(feedbackService, never()).submit(any(), any());
    }

    @Test
    @WithMockLoginUser(USER_ID_VALUE)
    @DisplayName("앞뒤 공백을 제거해 1000자면 공백을 포함한 원문이 더 길어도 접수한다")
    void submitFeedback_maxLengthAfterStrip_returnsCreated() throws Exception {
        String content = "가".repeat(Feedback.MAX_CONTENT_LENGTH);
        given(feedbackService.submit(USER_ID, "  " + content + "\n"))
                .willReturn(new FeedbackSubmissionResult(FEEDBACK_ID, CREATED_AT));

        mockMvc.perform(post("/api/v1/feedbacks")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"  " + content + "\\n\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockLoginUser(USER_ID_VALUE)
    @DisplayName("내용이 1000자를 넘으면 400을 반환한다")
    void submitFeedback_contentOverMaxLength_returnsBadRequest() throws Exception {
        String content = "가".repeat(Feedback.MAX_CONTENT_LENGTH + 1);

        mockMvc.perform(post("/api/v1/feedbacks")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"" + content + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_ERROR"))
                .andExpect(jsonPath("$.message").value("피드백 내용은 1000자 이하여야 합니다."));

        verify(feedbackService, never()).submit(any(), any());
    }

    @Test
    @WithMockLoginUser(USER_ID_VALUE)
    @DisplayName("접수 제한 초과는 기존 400 오류 형식으로 재접수 시각을 안내한다")
    void submitFeedback_limitExceeded_returnsBadRequestWithNextTime() throws Exception {
        // Given
        String message = "다음 접수 가능 시각(UTC): 2026-11-03T06:01:00Z";
        given(feedbackService.submit(USER_ID, "문의합니다"))
                .willThrow(new BusinessException(ErrorCode.BUSINESS_ERROR, message));

        // When & Then
        mockMvc.perform(post("/api/v1/feedbacks")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"문의합니다\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_ERROR"))
                .andExpect(jsonPath("$.message").value(message));
    }

    @Test
    @DisplayName("인증 정보가 없으면 401을 반환한다")
    void submitFeedback_unauthenticated_returnsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/feedbacks")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"사진 업로드가 느려요.\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("유효하지 않은 인증 정보입니다."));

        verify(feedbackService, never()).submit(any(), any());
    }
}
