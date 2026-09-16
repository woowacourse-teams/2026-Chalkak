package com.chalkak.backend.feedback.api.v1.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chalkak.backend.exception.GlobalExceptionHandler;
import com.chalkak.backend.feedback.domain.Feedback;
import com.chalkak.backend.feedback.service.FeedbackService;
import com.chalkak.backend.feedback.service.FeedbackSubmissionResult;
import com.chalkak.backend.support.WithMockLoginUser;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
    private static final UUID FEEDBACK_ID =
            UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6570d4");
    private static final Instant CREATED_AT = Instant.parse("2026-09-16T01:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FeedbackService feedbackService;

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
