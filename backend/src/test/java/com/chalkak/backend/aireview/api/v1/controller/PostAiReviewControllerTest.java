package com.chalkak.backend.aireview.api.v1.controller;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chalkak.backend.aireview.domain.AiReviewResult;
import com.chalkak.backend.aireview.domain.AiReviewResult.Criterion;
import com.chalkak.backend.aireview.service.PostAiReviewService;
import com.chalkak.backend.aireview.service.PostAiReviewService.ApiStatus;
import com.chalkak.backend.aireview.service.PostAiReviewService.ReviewResult;
import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.exception.GlobalExceptionHandler;
import com.chalkak.backend.exception.NotFoundException;
import com.chalkak.backend.support.WithMockLoginUser;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PostAiReviewController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class PostAiReviewControllerTest {

    private static final String USER_ID_VALUE = "0198f6c1-62ba-7d30-8b12-0f733b6570a1";
    private static final UUID USER_ID = UUID.fromString(USER_ID_VALUE);
    private static final UUID POST_ID = UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6570d4");
    private static final Instant REQUESTED_AT = Instant.parse("2026-10-20T05:00:00Z");
    private static final Instant COMPLETED_AT = Instant.parse("2026-10-20T05:00:21Z");
    private static final String PATH = "/api/v1/posts/{postId}/ai-review";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PostAiReviewService postAiReviewService;

    @Test
    @WithMockLoginUser(USER_ID_VALUE)
    @DisplayName("요청이 처리 중이면 202와 상태·요청 시각만 반환한다")
    void request_processing_returnsAcceptedWithoutResult() throws Exception {
        // Given
        given(postAiReviewService.request(USER_ID, POST_ID)).willReturn(
                new ReviewResult(
                        ApiStatus.PROCESSING,
                        REQUESTED_AT,
                        null,
                        null
                ));

        // When & Then
        mockMvc.perform(post(PATH, POST_ID))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PROCESSING"))
                .andExpect(jsonPath("$.requestedAt").value("2026-10-20T05:00:00Z"))
                .andExpect(jsonPath("$.completedAt").value(nullValue()))
                .andExpect(jsonPath("$.result").value(nullValue()))
                .andExpect(jsonPath("$.id").doesNotExist());
    }

    @Test
    @WithMockLoginUser(USER_ID_VALUE)
    @DisplayName("요청한 리뷰가 완료됐으면 200과 결과를 반환한다")
    void request_completed_returnsOkWithResult() throws Exception {
        // Given
        given(postAiReviewService.request(USER_ID, POST_ID)).willReturn(completed());

        // When & Then
        mockMvc.perform(post(PATH, POST_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.requestedAt").value("2026-10-20T05:00:00Z"))
                .andExpect(jsonPath("$.completedAt").value("2026-10-20T05:00:21Z"))
                .andExpect(jsonPath("$.result.overallScore").value(7.5))
                .andExpect(jsonPath("$.result.criteria.length()").value(1))
                .andExpect(jsonPath("$.result.criteria[0].key").value("COMPOSITION"))
                .andExpect(jsonPath("$.result.criteria[0].label").value("구도"))
                .andExpect(jsonPath("$.result.criteria[0].score").value(7.5))
                .andExpect(jsonPath("$.result.distinctivePerspective").value(true))
                .andExpect(jsonPath("$.result.feedback").value("구도가 좋습니다."));
    }

    @Test
    @WithMockLoginUser(USER_ID_VALUE)
    @DisplayName("요청한 리뷰가 실패했으면 200과 실패 상태만 반환하고 사유는 노출하지 않는다")
    void request_failed_returnsOkWithoutResult() throws Exception {
        // Given
        given(postAiReviewService.request(USER_ID, POST_ID)).willReturn(new ReviewResult(
                ApiStatus.FAILED,
                REQUESTED_AT,
                COMPLETED_AT,
                null
        ));

        // When & Then
        mockMvc.perform(post(PATH, POST_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.completedAt").value("2026-10-20T05:00:21Z"))
                .andExpect(jsonPath("$.result").value(nullValue()))
                .andExpect(jsonPath("$.errorCode").doesNotExist());
    }

    @Test
    @WithMockLoginUser(USER_ID_VALUE)
    @DisplayName("조회는 처리 중이어도 200이다")
    void get_processing_returnsOk() throws Exception {
        // Given
        given(postAiReviewService.get(USER_ID, POST_ID)).willReturn(
                new ReviewResult(
                        ApiStatus.PROCESSING,
                        REQUESTED_AT,
                        null,
                        null
                ));

        // When & Then
        mockMvc.perform(get(PATH, POST_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PROCESSING"))
                .andExpect(jsonPath("$.result").value(nullValue()));
    }

    @Test
    @WithMockLoginUser(USER_ID_VALUE)
    @DisplayName("조회가 완료 상태이면 결과를 반환한다")
    void get_completed_returnsResult() throws Exception {
        // Given
        given(postAiReviewService.get(USER_ID, POST_ID)).willReturn(completed());

        // When & Then
        mockMvc.perform(get(PATH, POST_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.result.overallScore").value(7.5));
    }

    @Test
    @WithMockLoginUser(USER_ID_VALUE)
    @DisplayName("게시물 ID 형식이 올바르지 않으면 400을 반환하고 서비스를 호출하지 않는다")
    void invalidPostId_returnsBadRequest() throws Exception {
        // When & Then
        mockMvc.perform(post(PATH, "invalid-post-id"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_ERROR"))
                .andExpect(jsonPath("$.message").value("ID 형식이 올바르지 않습니다."));
        mockMvc.perform(get(PATH, "invalid-post-id"))
                .andExpect(status().isBadRequest());

        verify(postAiReviewService, never()).request(any(), any());
        verify(postAiReviewService, never()).get(any(), any());
    }

    @Test
    @DisplayName("인증 정보가 없으면 401을 반환한다")
    void unauthenticated_returnsUnauthorized() throws Exception {
        // When & Then
        mockMvc.perform(post(PATH, POST_ID))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
        mockMvc.perform(get(PATH, POST_ID))
                .andExpect(status().isUnauthorized());

        verify(postAiReviewService, never()).request(any(), any());
        verify(postAiReviewService, never()).get(any(), any());
    }

    @Test
    @WithMockLoginUser(USER_ID_VALUE)
    @DisplayName("게시물이나 리뷰를 찾을 수 없으면 서비스 메시지 그대로 404를 반환한다")
    void notFound_returnsNotFound() throws Exception {
        // Given
        given(postAiReviewService.request(USER_ID, POST_ID)).willThrow(new NotFoundException(
                ErrorCode.BUSINESS_ERROR, "게시물을 찾을 수 없습니다."));
        given(postAiReviewService.get(USER_ID, POST_ID)).willThrow(new NotFoundException(
                ErrorCode.BUSINESS_ERROR, "AI 리뷰를 찾을 수 없습니다."));

        // When & Then
        mockMvc.perform(post(PATH, POST_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_ERROR"))
                .andExpect(jsonPath("$.message").value("게시물을 찾을 수 없습니다."));
        mockMvc.perform(get(PATH, POST_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("AI 리뷰를 찾을 수 없습니다."));
    }

    @Test
    @WithMockLoginUser(USER_ID_VALUE)
    @DisplayName("지금 요청할 수 없으면 안내 메시지와 함께 400을 반환한다")
    void request_unavailable_returnsBadRequest() throws Exception {
        // Given
        given(postAiReviewService.request(USER_ID, POST_ID)).willThrow(new BusinessException(
                ErrorCode.BUSINESS_ERROR, "지금은 AI 리뷰를 요청할 수 없습니다."));

        // When & Then
        mockMvc.perform(post(PATH, POST_ID))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_ERROR"))
                .andExpect(jsonPath("$.message").value("지금은 AI 리뷰를 요청할 수 없습니다."));
    }

    private ReviewResult completed() {
        return new ReviewResult(
                ApiStatus.COMPLETED,
                REQUESTED_AT,
                COMPLETED_AT,
                new AiReviewResult(
                        7.5,
                        List.of(new Criterion(
                                "COMPOSITION",
                                "구도",
                                7.5
                        )),
                        true,
                        "구도가 좋습니다."
                )
        );
    }
}
