package com.chalkak.backend.admin.service.aireview;

import static com.chalkak.backend.aireview.service.AiReviewFixture.AUTHOR_ID;
import static com.chalkak.backend.aireview.service.AiReviewFixture.COMPLETED_AT;
import static com.chalkak.backend.aireview.service.AiReviewFixture.MODEL;
import static com.chalkak.backend.aireview.service.AiReviewFixture.POST_ID;
import static com.chalkak.backend.aireview.service.AiReviewFixture.REQUESTED_AT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.chalkak.backend.admin.service.aireview.AdminAiReviewQueryService.AiReviewSummary;
import com.chalkak.backend.admin.service.aireview.AdminAiReviewQueryService.ListResult;
import com.chalkak.backend.aireview.domain.AiReview.Status;
import com.chalkak.backend.aireview.service.AiReviewFixture;
import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.photo.service.ImageUrlProvider;
import com.chalkak.backend.post.domain.ModerationStatus;
import com.chalkak.backend.support.IntegrationTestSupport;
import com.chalkak.backend.user.domain.UserStatus;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class AdminAiReviewQueryServiceTest extends IntegrationTestSupport {

    private static final String THUMBNAIL_STORAGE_KEY = "chalkak/test/ai-review/thumb-original.webp";
    private static final String THUMBNAIL_URL = "https://cdn.example.com/test/ai-review/thumb-original.webp";

    @Autowired
    private AdminAiReviewQueryService queryService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private ImageUrlProvider imageUrlProvider;

    private UUID promptVersionId;
    private UUID reviewId;

    @BeforeEach
    void setUp() {
        AiReviewFixture fixture = new AiReviewFixture(jdbcTemplate);
        fixture.insertPublicPost();
        promptVersionId = fixture.insertPromptVersion(1);
        reviewId = fixture.insertReview(promptVersionId, "COMPLETED");
        jdbcTemplate.update(
                """
                        UPDATE ai_reviews SET request_count = 2, input_tokens = 1200,
                            cached_input_tokens = 200, output_tokens = 300, reasoning_tokens = 100,
                            latency_ms = 21000
                        WHERE id = ?
                        """,
                reviewId
        );
        given(imageUrlProvider.getUrl(THUMBNAIL_STORAGE_KEY)).willReturn(THUMBNAIL_URL);
    }

    @Test
    @DisplayName("완료된 리뷰를 점수·프롬프트 버전·게시물 썸네일 URL·작성자·사용량과 함께 조회한다")
    void getAiReviews_completedReview_returnsSummary() {
        // When
        ListResult result = queryService.getAiReviews(
                null,
                null,
                1,
                20
        );

        // Then
        assertThat(result.currentPage()).isEqualTo(1);
        assertThat(result.pageSize()).isEqualTo(20);
        assertThat(result.hasNext()).isFalse();
        assertThat(result.aiReviews()).hasSize(1);
        AiReviewSummary summary = result.aiReviews().get(0);
        assertThat(summary.aiReviewId()).isEqualTo(reviewId);
        assertThat(summary.status()).isEqualTo(Status.COMPLETED);
        assertThat(summary.errorCode()).isNull();
        assertThat(summary.requestCount()).isEqualTo(2);
        assertThat(summary.overallScore()).isEqualTo(7.5);
        assertThat(summary.promptVersion().promptVersionId()).isEqualTo(promptVersionId);
        assertThat(summary.promptVersion().version()).isEqualTo(1);
        assertThat(summary.promptVersion().model()).isEqualTo(MODEL);
        assertThat(summary.post().postId()).isEqualTo(POST_ID);
        assertThat(summary.post().thumbnailUrl()).isEqualTo(THUMBNAIL_URL);
        assertThat(summary.post().moderationStatus()).isEqualTo(ModerationStatus.APPROVED);
        assertThat(summary.post().deletedAt()).isNull();
        assertThat(summary.author().userId()).isEqualTo(AUTHOR_ID);
        assertThat(summary.author().email()).isEqualTo("ai-review-author@example.com");
        assertThat(summary.author().status()).isEqualTo(UserStatus.ACTIVE);
        assertThat(summary.author().deletedAt()).isNull();
        assertThat(summary.usage().inputTokens()).isEqualTo(1200);
        assertThat(summary.usage().cachedInputTokens()).isEqualTo(200);
        assertThat(summary.usage().outputTokens()).isEqualTo(300);
        assertThat(summary.usage().reasoningTokens()).isEqualTo(100);
        assertThat(summary.latencyMs()).isEqualTo(21000L);
        assertThat(summary.requestedAt()).isEqualTo(REQUESTED_AT);
        assertThat(summary.lastRequestedAt()).isEqualTo(REQUESTED_AT);
        assertThat(summary.completedAt()).isEqualTo(COMPLETED_AT);
        assertThat(summary.visibleToAuthor()).isTrue();
    }

    @Test
    @DisplayName("없는 프롬프트 버전 ID로 조회하면 404가 아니라 빈 목록을 반환한다")
    void getAiReviews_unknownPromptVersionId_returnsEmptyList() {
        // When
        ListResult result = queryService.getAiReviews(
                null,
                UUID.randomUUID(),
                1,
                20
        );

        // Then
        assertThat(result.aiReviews()).isEmpty();
        assertThat(result.hasNext()).isFalse();
    }

    @ParameterizedTest
    @CsvSource({
            "0, 20",
            "1, 0",
            "1, 101",
            "2147483647, 100"
    })
    @DisplayName("페이지 번호나 크기가 범위를 벗어나거나 offset이 int 범위를 넘으면 조회 조건 오류를 던진다")
    void getAiReviews_invalidPagination_throwsBusinessException(int page, int pageSize) {
        // When & Then
        assertThatThrownBy(() -> queryService.getAiReviews(
                null,
                null,
                page,
                pageSize
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessage("조회 조건이 올바르지 않습니다.");
    }

    @Test
    @DisplayName("완료되지 않은 리뷰는 점수가 null이고 실패 오류 코드는 그대로 담는다")
    void getAiReviews_nonCompletedReviews_returnsNullScore() {
        // Given
        AiReviewFixture fixture = new AiReviewFixture(jdbcTemplate);
        fixture.insertSecondPublicPost();
        UUID failedId = fixture.insertSecondQueuedReview(
                promptVersionId,
                REQUESTED_AT.plusSeconds(60)
        );
        jdbcTemplate.update(
                """
                        UPDATE ai_reviews SET status = 'FAILED', attempt = 1, round_attempt = 1,
                            started_at = requested_at, completed_at = requested_at,
                            error_code = 'TIMEOUT'
                        WHERE id = ?
                        """,
                failedId
        );

        // When
        ListResult result = queryService.getAiReviews(
                Status.FAILED,
                null,
                1,
                20
        );

        // Then
        assertThat(result.aiReviews()).hasSize(1);
        AiReviewSummary summary = result.aiReviews().get(0);
        assertThat(summary.aiReviewId()).isEqualTo(failedId);
        assertThat(summary.status()).isEqualTo(Status.FAILED);
        assertThat(summary.errorCode()).isEqualTo("TIMEOUT");
        assertThat(summary.overallScore()).isNull();
    }

    @Test
    @DisplayName("썸네일 저장 키가 없으면 썸네일 URL은 null이다")
    void getAiReviews_nullThumbnailKey_returnsNullThumbnailUrl() {
        // Given
        jdbcTemplate.update("UPDATE photos SET thumbnail_storage_key = NULL");

        // When
        ListResult result = queryService.getAiReviews(
                null,
                null,
                1,
                20
        );

        // Then
        assertThat(result.aiReviews()).hasSize(1);
        assertThat(result.aiReviews().get(0).post().thumbnailUrl()).isNull();
    }

    @Test
    @DisplayName("페이지 크기 경계값 1과 100을 허용하며 hasNext는 남은 리뷰 유무를 따른다")
    void getAiReviews_pageSizeBoundary_setsHasNext() {
        // Given
        AiReviewFixture fixture = new AiReviewFixture(jdbcTemplate);
        fixture.insertSecondPublicPost();
        fixture.insertSecondQueuedReview(
                promptVersionId,
                REQUESTED_AT.plusSeconds(60)
        );

        // When
        ListResult minSize = queryService.getAiReviews(
                null,
                null,
                1,
                1
        );
        ListResult maxSize = queryService.getAiReviews(
                null,
                null,
                1,
                100
        );

        // Then
        assertThat(minSize.aiReviews()).hasSize(1);
        assertThat(minSize.hasNext()).isTrue();
        assertThat(maxSize.aiReviews()).hasSize(2);
        assertThat(maxSize.hasNext()).isFalse();
    }
}
