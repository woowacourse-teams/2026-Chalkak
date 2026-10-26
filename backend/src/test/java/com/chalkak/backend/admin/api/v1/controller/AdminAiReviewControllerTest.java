package com.chalkak.backend.admin.api.v1.controller;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chalkak.backend.admin.api.support.AdminActorResolver;
import com.chalkak.backend.admin.api.support.AdminArgumentResolverWebMvcConfig;
import com.chalkak.backend.admin.api.support.AuthenticatedAdmin;
import com.chalkak.backend.admin.service.aireview.AdminAiReviewQueryService;
import com.chalkak.backend.admin.service.aireview.AdminAiReviewQueryService.AiReviewDetail;
import com.chalkak.backend.admin.service.aireview.AdminAiReviewQueryService.AiReviewSummary;
import com.chalkak.backend.admin.service.aireview.AdminAiReviewQueryService.AuthorSummary;
import com.chalkak.backend.admin.service.aireview.AdminAiReviewQueryService.ListResult;
import com.chalkak.backend.admin.service.aireview.AdminAiReviewQueryService.PostSummary;
import com.chalkak.backend.admin.service.aireview.AdminAiReviewQueryService.PromptVersionSummary;
import com.chalkak.backend.admin.service.aireview.AdminAiReviewQueryService.UsageSummary;
import com.chalkak.backend.aireview.domain.AiReview.Status;
import com.chalkak.backend.aireview.domain.AiReviewResult;
import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.exception.GlobalExceptionHandler;
import com.chalkak.backend.exception.NotFoundException;
import com.chalkak.backend.post.domain.ModerationStatus;
import com.chalkak.backend.user.domain.UserStatus;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@WebMvcTest(AdminAiReviewController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({GlobalExceptionHandler.class, AdminArgumentResolverWebMvcConfig.class})
class AdminAiReviewControllerTest {

    private static final UUID ADMIN_ID = UUID.fromString("019a0000-0000-7000-8000-000000000901");
    private static final UUID REVIEW_ID = UUID.fromString("019a0000-0000-7000-8000-000000000691");
    private static final UUID VERSION_ID = UUID.fromString("019a0000-0000-7000-8000-000000000692");
    private static final UUID POST_ID = UUID.fromString("019a0000-0000-7000-8000-000000000693");
    private static final UUID USER_ID = UUID.fromString("019a0000-0000-7000-8000-000000000694");
    private static final String PATH = "/api/v1/admin/ai-reviews";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AdminAiReviewQueryService queryService;

    @MockitoBean
    private AdminActorResolver adminActorResolver;

    @BeforeEach
    void setUp() {
        given(adminActorResolver.resolve()).willReturn(new AuthenticatedAdmin(ADMIN_ID));
    }

    @Test
    @DisplayName("조건 없이 조회하면 기본 페이지로 서비스에 전달하고 항목 전체와 페이지 정보를 응답한다")
    void getAiReviews_noParameters_usesDefaultsAndReturnsItems() throws Exception {
        // Given
        given(queryService.getAiReviews(
                null,
                null,
                1,
                20
        )).willReturn(new ListResult(
                1,
                20,
                true,
                List.of(summary(Status.COMPLETED, 7.5))
        ));

        // When & Then
        mockMvc.perform(get(PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentPage").value(1))
                .andExpect(jsonPath("$.pageSize").value(20))
                .andExpect(jsonPath("$.hasNext").value(true))
                .andExpect(jsonPath("$.aiReviews[0].aiReviewId").value(REVIEW_ID.toString()))
                .andExpect(jsonPath("$.aiReviews[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$.aiReviews[0].errorCode").value(nullValue()))
                .andExpect(jsonPath("$.aiReviews[0].requestCount").value(2))
                .andExpect(jsonPath("$.aiReviews[0].overallScore").value(7.5))
                .andExpect(jsonPath("$.aiReviews[0].promptVersion.promptVersionId")
                        .value(VERSION_ID.toString()))
                .andExpect(jsonPath("$.aiReviews[0].promptVersion.version").value(3))
                .andExpect(jsonPath("$.aiReviews[0].promptVersion.model").value("gpt-6.1-sol"))
                .andExpect(jsonPath("$.aiReviews[0].post.postId").value(POST_ID.toString()))
                .andExpect(jsonPath("$.aiReviews[0].post.thumbnailUrl")
                        .value("https://cdn.example.com/thumb.webp"))
                .andExpect(jsonPath("$.aiReviews[0].post.moderationStatus").value("APPROVED"))
                .andExpect(jsonPath("$.aiReviews[0].post.deletedAt").value(nullValue()))
                .andExpect(jsonPath("$.aiReviews[0].author.userId").value(USER_ID.toString()))
                .andExpect(jsonPath("$.aiReviews[0].author.email").value("author@example.com"))
                .andExpect(jsonPath("$.aiReviews[0].author.status").value("BANNED"))
                .andExpect(jsonPath("$.aiReviews[0].author.deletedAt").value(nullValue()))
                .andExpect(jsonPath("$.aiReviews[0].usage.inputTokens").value(1200))
                .andExpect(jsonPath("$.aiReviews[0].usage.cachedInputTokens").value(200))
                .andExpect(jsonPath("$.aiReviews[0].usage.outputTokens").value(300))
                .andExpect(jsonPath("$.aiReviews[0].usage.reasoningTokens").value(100))
                .andExpect(jsonPath("$.aiReviews[0].latencyMs").value(21000))
                .andExpect(jsonPath("$.aiReviews[0].requestedAt").value("2026-10-20T05:00:00Z"))
                .andExpect(jsonPath("$.aiReviews[0].lastRequestedAt")
                        .value("2026-10-21T05:00:00Z"))
                .andExpect(jsonPath("$.aiReviews[0].completedAt").value("2026-10-21T05:00:21Z"))
                .andExpect(jsonPath("$.aiReviews[0].visibleToAuthor").value(true));
    }

    @Test
    @DisplayName("완료가 아닌 리뷰의 점수는 null로 응답한다")
    void getAiReviews_nonCompletedReview_returnsNullScore() throws Exception {
        // Given
        given(queryService.getAiReviews(
                null,
                null,
                1,
                20
        )).willReturn(new ListResult(
                1,
                20,
                false,
                List.of(summary(Status.QUEUED, null))
        ));

        // When & Then
        mockMvc.perform(get(PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aiReviews[0].status").value("QUEUED"))
                .andExpect(jsonPath("$.aiReviews[0].overallScore").value(nullValue()));
    }

    @Test
    @DisplayName("상태·프롬프트 버전·페이지 조건을 서비스에 그대로 전달한다")
    void getAiReviews_allParameters_passesToService() throws Exception {
        // Given
        given(queryService.getAiReviews(
                Status.FAILED,
                VERSION_ID,
                2,
                50
        )).willReturn(new ListResult(
                2,
                50,
                false,
                List.of()
        ));

        // When & Then
        mockMvc.perform(get(PATH)
                .queryParam("status", "FAILED")
                .queryParam("promptVersionId", VERSION_ID.toString())
                .queryParam("page", "2")
                .queryParam("pageSize", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentPage").value(2))
                .andExpect(jsonPath("$.pageSize").value(50))
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.aiReviews").isEmpty());
        then(queryService).should().getAiReviews(
                Status.FAILED,
                VERSION_ID,
                2,
                50
        );
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 100})
    @DisplayName("페이지 크기의 양쪽 경계값을 허용한다")
    void getAiReviews_pageSizeBoundary_returnsOk(int pageSize) throws Exception {
        // Given
        given(queryService.getAiReviews(
                null,
                null,
                1,
                pageSize
        )).willReturn(new ListResult(
                1,
                pageSize,
                false,
                List.of()
        ));

        // When & Then
        mockMvc.perform(get(PATH).queryParam("pageSize", Integer.toString(pageSize)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pageSize").value(pageSize));
    }

    @ParameterizedTest
    @CsvSource({
            "page, 0",
            "pageSize, 0",
            "pageSize, 101",
            "page, abc",
            "pageSize, abc"
    })
    @DisplayName("페이지 조건이 범위를 벗어나거나 숫자가 아니면 400을 반환하고 서비스를 호출하지 않는다")
    void getAiReviews_invalidPagination_returnsBadRequest(
            String parameterName,
            String parameterValue
    ) throws Exception {
        // When & Then
        mockMvc.perform(get(PATH).queryParam(parameterName, parameterValue))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_ERROR"));
        then(queryService).shouldHaveNoInteractions();
    }

    @ParameterizedTest
    @CsvSource({
            "status, UNKNOWN",
            "status, completed",
            "promptVersionId, invalid-uuid"
    })
    @DisplayName("상태나 프롬프트 버전 ID 형식이 잘못되면 400을 반환하고 서비스를 호출하지 않는다")
    void getAiReviews_invalidFilter_returnsBadRequest(
            String parameterName,
            String parameterValue
    ) throws Exception {
        // When & Then
        mockMvc.perform(get(PATH).queryParam(parameterName, parameterValue))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_ERROR"));
        then(queryService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("서비스가 조회 조건 오류를 던지면 400 BUSINESS_ERROR로 변환한다")
    void getAiReviews_serviceRejectsPagination_returnsBadRequest() throws Exception {
        // Given
        given(queryService.getAiReviews(
                null,
                null,
                2147483647,
                100
        )).willThrow(new BusinessException(ErrorCode.BUSINESS_ERROR, "조회 조건이 올바르지 않습니다."));

        // When & Then
        mockMvc.perform(get(PATH)
                .queryParam("page", "2147483647")
                .queryParam("pageSize", "100"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_ERROR"))
                .andExpect(jsonPath("$.message").value("조회 조건이 올바르지 않습니다."));
    }

    @Test
    @DisplayName("상세 조회는 목록 항목 전체와 결과·원출력 객체·응답 ID·시도 횟수·시작 시각·원본 이미지 URL을 응답한다")
    void getAiReview_existing_returnsFullDetail() throws Exception {
        // Given
        given(queryService.getAiReview(REVIEW_ID)).willReturn(detail(
                summary(Status.COMPLETED, 7.5),
                objectMapper.readTree(
                        """
                                {"compositionScore":7.46,"themeExpressionScore":8,
                                 "feedback":{"summary":"요약","strength":"강점","weakness":"약점","suggestion":"제안"}}
                                """)
        ));

        // When & Then
        mockMvc.perform(get(PATH + "/" + REVIEW_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aiReviewId").value(REVIEW_ID.toString()))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.errorCode").value(nullValue()))
                .andExpect(jsonPath("$.requestCount").value(2))
                .andExpect(jsonPath("$.overallScore").value(7.5))
                .andExpect(jsonPath("$.promptVersion.promptVersionId")
                        .value(VERSION_ID.toString()))
                .andExpect(jsonPath("$.promptVersion.version").value(3))
                .andExpect(jsonPath("$.promptVersion.model").value("gpt-6.1-sol"))
                .andExpect(jsonPath("$.post.postId").value(POST_ID.toString()))
                .andExpect(jsonPath("$.post.thumbnailUrl")
                        .value("https://cdn.example.com/thumb.webp"))
                .andExpect(jsonPath("$.post.imageUrl")
                        .value("https://cdn.example.com/original.webp"))
                .andExpect(jsonPath("$.post.moderationStatus").value("APPROVED"))
                .andExpect(jsonPath("$.post.deletedAt").value(nullValue()))
                .andExpect(jsonPath("$.author.userId").value(USER_ID.toString()))
                .andExpect(jsonPath("$.author.email").value("author@example.com"))
                .andExpect(jsonPath("$.author.status").value("BANNED"))
                .andExpect(jsonPath("$.author.deletedAt").value(nullValue()))
                .andExpect(jsonPath("$.usage.inputTokens").value(1200))
                .andExpect(jsonPath("$.usage.cachedInputTokens").value(200))
                .andExpect(jsonPath("$.usage.outputTokens").value(300))
                .andExpect(jsonPath("$.usage.reasoningTokens").value(100))
                .andExpect(jsonPath("$.latencyMs").value(21000))
                .andExpect(jsonPath("$.requestedAt").value("2026-10-20T05:00:00Z"))
                .andExpect(jsonPath("$.lastRequestedAt").value("2026-10-21T05:00:00Z"))
                .andExpect(jsonPath("$.startedAt").value("2026-10-21T05:00:01Z"))
                .andExpect(jsonPath("$.completedAt").value("2026-10-21T05:00:21Z"))
                .andExpect(jsonPath("$.visibleToAuthor").value(true))
                .andExpect(jsonPath("$.openaiResponseId").value("resp_abc"))
                .andExpect(jsonPath("$.attempt").value(1))
                .andExpect(jsonPath("$.result.overallScore").value(7.5))
                .andExpect(jsonPath("$.result.criteria[0].key").value("COMPOSITION"))
                .andExpect(jsonPath("$.result.feedback.summary").value("요약"))
                .andExpect(jsonPath("$.output.compositionScore").value(7.46))
                .andExpect(jsonPath("$.output.themeExpressionScore").value(8))
                .andExpect(jsonPath("$.output.feedback.summary").value("요약"));
    }

    @Test
    @DisplayName("결과·원출력·응답 ID·시작 시각이 없는 리뷰는 해당 필드를 null로 응답한다")
    void getAiReview_withoutResultAndOutput_returnsNulls() throws Exception {
        // Given
        given(queryService.getAiReview(REVIEW_ID)).willReturn(new AiReviewDetail(
                summary(Status.QUEUED, null),
                null,
                null,
                null,
                0,
                null,
                "https://cdn.example.com/original.webp"
        ));

        // When & Then
        mockMvc.perform(get(PATH + "/" + REVIEW_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value(nullValue()))
                .andExpect(jsonPath("$.output").value(nullValue()))
                .andExpect(jsonPath("$.openaiResponseId").value(nullValue()))
                .andExpect(jsonPath("$.attempt").value(0))
                .andExpect(jsonPath("$.startedAt").value(nullValue()));
    }

    @Test
    @DisplayName("UUID 형식이 아닌 리뷰 ID는 400을 반환하고 서비스를 호출하지 않는다")
    void getAiReview_invalidId_returnsBadRequest() throws Exception {
        // When & Then
        mockMvc.perform(get(PATH + "/invalid-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_ERROR"));
        then(queryService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("없는 리뷰 ID는 404 BUSINESS_ERROR로 변환한다")
    void getAiReview_unknownId_returnsNotFound() throws Exception {
        // Given
        given(queryService.getAiReview(REVIEW_ID)).willThrow(
                new NotFoundException(ErrorCode.BUSINESS_ERROR, "AI 리뷰를 찾을 수 없습니다."));

        // When & Then
        mockMvc.perform(get(PATH + "/" + REVIEW_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_ERROR"))
                .andExpect(jsonPath("$.message").value("AI 리뷰를 찾을 수 없습니다."));
    }

    private AiReviewDetail detail(AiReviewSummary summary, JsonNode output) {
        return new AiReviewDetail(
                summary,
                new AiReviewResult(
                        7.5,
                        List.of(new AiReviewResult.Criterion(
                                "COMPOSITION",
                                "구도",
                                7.5
                        )),
                        false,
                        new AiReviewResult.Feedback(
                                "요약",
                                "강점",
                                "약점",
                                "제안"
                        )
                ),
                output,
                "resp_abc",
                1,
                Instant.parse("2026-10-21T05:00:01Z"),
                "https://cdn.example.com/original.webp"
        );
    }

    private AiReviewSummary summary(Status status, Double overallScore) {
        return new AiReviewSummary(
                REVIEW_ID,
                status,
                null,
                2,
                overallScore,
                new PromptVersionSummary(
                        VERSION_ID,
                        3,
                        "gpt-6.1-sol"
                ),
                new PostSummary(
                        POST_ID,
                        "https://cdn.example.com/thumb.webp",
                        ModerationStatus.APPROVED,
                        null
                ),
                new AuthorSummary(
                        USER_ID,
                        "author@example.com",
                        UserStatus.BANNED,
                        null
                ),
                new UsageSummary(
                        1200,
                        200,
                        300,
                        100
                ),
                21000L,
                Instant.parse("2026-10-20T05:00:00Z"),
                Instant.parse("2026-10-21T05:00:00Z"),
                Instant.parse("2026-10-21T05:00:21Z"),
                true
        );
    }
}
