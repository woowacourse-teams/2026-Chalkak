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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;

@WebMvcTest(PostAiReviewController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class PostAiReviewControllerTest {

    private static final String USER_ID_VALUE = "0198f6c1-62ba-7d30-8b12-0f733b6570a1";
    private static final UUID USER_ID = UUID.fromString(USER_ID_VALUE);
    private static final UUID POST_ID = UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6570d4");
    private static final Instant REQUESTED_AT = Instant.parse("2026-10-20T05:00:00Z");
    private static final Instant COMPLETED_AT = Instant.parse("2026-10-20T05:00:21Z");
    private static final AiReviewResult STORED = new AiReviewResult(
            7.5,
            List.of(new Criterion(
                    "COMPOSITION",
                    "구도",
                    7.5
            )),
            true,
            "구도가 좋습니다."
    );
    private static final String PATH = "/api/v1/posts/{postId}/ai-review";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PostAiReviewService postAiReviewService;

    @ParameterizedTest
    @WithMockLoginUser(USER_ID_VALUE)
    @CsvSource({
            "PROCESSING, 202",
            "COMPLETED, 200",
            "FAILED, 200"
    })
    @DisplayName("요청은 처리 중이면 202, 완료·실패면 200이고 조회는 항상 200이며 응답 필드는 같다")
    void requestAndGet_byStatus_returnsMatchingHttpStatus(
            ApiStatus apiStatus,
            int requestHttpStatus
    ) throws Exception {
        // Given
        ReviewResult reviewResult = result(apiStatus);
        given(postAiReviewService.request(USER_ID, POST_ID)).willReturn(reviewResult);
        given(postAiReviewService.get(USER_ID, POST_ID)).willReturn(reviewResult);
        String completedAt = apiStatus == ApiStatus.PROCESSING ? null : "2026-10-20T05:00:21Z";
        ResultMatcher resultMatcher = apiStatus == ApiStatus.COMPLETED
                ? jsonPath("$.result.overallScore").value(7.5)
                : jsonPath("$.result").value(nullValue());

        // When & Then
        mockMvc.perform(post(PATH, POST_ID))
                .andExpect(status().is(requestHttpStatus))
                .andExpect(jsonPath("$.status").value(apiStatus.name()))
                .andExpect(jsonPath("$.requestedAt").value("2026-10-20T05:00:00Z"))
                .andExpect(jsonPath("$.completedAt").value(completedAt))
                .andExpect(jsonPath("$.id").doesNotExist())
                .andExpect(jsonPath("$.errorCode").doesNotExist())
                .andExpect(resultMatcher);
        mockMvc.perform(get(PATH, POST_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(apiStatus.name()))
                .andExpect(resultMatcher);
    }

    @Test
    @WithMockLoginUser(USER_ID_VALUE)
    @DisplayName("요청한 적 없는 리뷰의 조회는 200과 요청 전 상태이고 시각과 결과는 null이다")
    void get_none_returnsOkWithNullFields() throws Exception {
        // Given
        given(postAiReviewService.get(USER_ID, POST_ID))
                .willReturn(new ReviewResult(
                        ApiStatus.NONE,
                        null,
                        null,
                        null
                ));

        // When & Then
        mockMvc.perform(get(PATH, POST_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NONE"))
                .andExpect(jsonPath("$.requestedAt").value(nullValue()))
                .andExpect(jsonPath("$.completedAt").value(nullValue()))
                .andExpect(jsonPath("$.result").value(nullValue()));
    }

    @Test
    @WithMockLoginUser(USER_ID_VALUE)
    @DisplayName("완료된 리뷰는 결과의 점수·항목·피드백을 반환하고 그 밖의 상태는 결과가 null이다")
    void request_completed_returnsResult() throws Exception {
        // Given
        given(postAiReviewService.request(USER_ID, POST_ID))
                .willReturn(result(ApiStatus.COMPLETED));

        // When & Then
        mockMvc.perform(post(PATH, POST_ID))
                .andExpect(jsonPath("$.result.overallScore").value(7.5))
                .andExpect(jsonPath("$.result.criteria.length()").value(1))
                .andExpect(jsonPath("$.result.criteria[0].key").value("COMPOSITION"))
                .andExpect(jsonPath("$.result.criteria[0].label").value("구도"))
                .andExpect(jsonPath("$.result.criteria[0].score").value(7.5))
                .andExpect(jsonPath("$.result.distinctivePerspective").value(true))
                .andExpect(jsonPath("$.result.feedback").value("구도가 좋습니다."));
        given(postAiReviewService.request(USER_ID, POST_ID)).willReturn(result(ApiStatus.FAILED));
        mockMvc.perform(post(PATH, POST_ID))
                .andExpect(jsonPath("$.result").value(nullValue()));
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
    @DisplayName("게시물을 찾을 수 없으면 서비스 메시지 그대로 404를 반환한다")
    void notFound_returnsNotFound() throws Exception {
        // Given
        given(postAiReviewService.request(USER_ID, POST_ID)).willThrow(new NotFoundException(
                ErrorCode.BUSINESS_ERROR, "게시물을 찾을 수 없습니다."));
        given(postAiReviewService.get(USER_ID, POST_ID)).willThrow(new NotFoundException(
                ErrorCode.BUSINESS_ERROR, "게시물을 찾을 수 없습니다."));

        // When & Then
        mockMvc.perform(post(PATH, POST_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_ERROR"))
                .andExpect(jsonPath("$.message").value("게시물을 찾을 수 없습니다."));
        mockMvc.perform(get(PATH, POST_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("게시물을 찾을 수 없습니다."));
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

    private ReviewResult result(ApiStatus apiStatus) {
        return new ReviewResult(
                apiStatus,
                REQUESTED_AT,
                apiStatus == ApiStatus.PROCESSING ? null : COMPLETED_AT,
                apiStatus == ApiStatus.COMPLETED ? STORED : null
        );
    }
}
