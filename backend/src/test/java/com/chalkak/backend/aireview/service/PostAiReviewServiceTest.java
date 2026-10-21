package com.chalkak.backend.aireview.service;

import static com.chalkak.backend.aireview.service.AiReviewFixture.AUTHOR_ID;
import static com.chalkak.backend.aireview.service.AiReviewFixture.MODEL;
import static com.chalkak.backend.aireview.service.AiReviewFixture.OTHER_USER_ID;
import static com.chalkak.backend.aireview.service.AiReviewFixture.POST_ID;
import static com.chalkak.backend.aireview.service.AiReviewFixture.REQUESTED_AT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.chalkak.backend.aireview.domain.AiReviewResult;
import com.chalkak.backend.aireview.domain.AiReviewResult.Criterion;
import com.chalkak.backend.aireview.service.PostAiReviewService.ApiStatus;
import com.chalkak.backend.aireview.service.PostAiReviewService.ReviewResult;
import com.chalkak.backend.common.logging.LogFields;
import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.exception.NotFoundException;
import com.chalkak.backend.support.DatabaseCleaner;
import com.chalkak.backend.support.IntegrationTestSupport;
import jakarta.persistence.EntityManager;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class PostAiReviewServiceTest extends IntegrationTestSupport {

    private static final String POST_NOT_FOUND = "게시물을 찾을 수 없습니다.";
    private static final String LIMIT_REACHED = "더 이상 AI 리뷰를 다시 요청할 수 없습니다.";
    private static final Instant AGAIN_AT = Instant.parse("2026-10-20T06:00:00Z");
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

    @Autowired
    private PostAiReviewService service;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @MockitoBean
    private Clock clock;

    private final UUID requestId = UUID.randomUUID();
    private final Logger logger = (Logger) LoggerFactory.getLogger("chalkak.aireview");
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    private AiReviewFixture fixture;

    @BeforeEach
    void setUp() {
        fixture = new AiReviewFixture(jdbcTemplate);
        fixture.insertPublicPost();
        given(aiReviewGenerator.supportsModel(MODEL)).willReturn(true);
        given(clock.instant()).willReturn(REQUESTED_AT);
        MDC.put(LogFields.REQUEST_ID, requestId.toString());
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.INFO);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
        MDC.remove(LogFields.REQUEST_ID);
    }

    @Test
    @DisplayName("처음 요청하면 최신 프롬프트 버전을 고정한 대기 행을 만들고 처리 중으로 응답한다")
    void request_firstTime_createsQueuedRowWithLatestVersion() {
        // Given
        fixture.insertPromptVersion(1);
        UUID latestVersionId = fixture.insertPromptVersion(2);

        // When
        ReviewResult result = service.request(AUTHOR_ID, POST_ID);

        // Then
        assertThat(result.status()).isEqualTo(ApiStatus.PROCESSING);
        assertThat(result.requestedAt()).isEqualTo(REQUESTED_AT);
        assertThat(result.completedAt()).isNull();
        assertThat(result.result()).isNull();
        Map<String, Object> row = jdbcTemplate.queryForMap("SELECT * FROM ai_reviews");
        assertThat(row).containsEntry("post_id", POST_ID)
                .containsEntry("user_id", AUTHOR_ID)
                .containsEntry("prompt_version_id", latestVersionId)
                .containsEntry("status", "QUEUED")
                .containsEntry("attempt", 0)
                .containsEntry("request_id", requestId);
    }

    @Test
    @DisplayName("이미 요청했으면 같은 행을 돌려주고, 요청 로그는 새 행을 만들 때만 한 번 남기며 프롬프트·이미지·주제는 남기지 않는다")
    void request_secondTime_returnsSameRowAndLogsRequestedOnce() {
        // Given
        fixture.insertPromptVersion(1);

        // When
        ReviewResult first = service.request(AUTHOR_ID, POST_ID);
        ReviewResult second = service.request(AUTHOR_ID, POST_ID);

        // Then
        assertThat(second).isEqualTo(first);
        assertThat(fixture.countReviews()).isEqualTo(1);
        String reviewId = jdbcTemplate.queryForObject("SELECT id FROM ai_reviews", String.class);
        assertThat(appender.list).singleElement().satisfies(event -> {
            String keyValues = event.getKeyValuePairs().toString();
            assertThat(keyValues).contains(
                    "type=\"ai_review\"",
                    "event=\"review_requested\"",
                    "aiReviewId=\"" + reviewId + "\"",
                    "postId=\"" + POST_ID + "\"",
                    "userId=\"" + AUTHOR_ID + "\"",
                    "promptVersion=\"1\"",
                    "model=\"" + MODEL + "\"",
                    "requestCount=\"1\"",
                    "requestId=\"" + requestId + "\"",
                    "created=\"true\""
            );
            assertThat(keyValues + event.getFormattedMessage())
                    .doesNotContain(
                            "시스템",
                            "노을",
                            "original.webp"
                    );
        });
    }

    @ParameterizedTest
    @CsvSource({
            "QUEUED, PROCESSING",
            "COMPLETED, COMPLETED"
    })
    @DisplayName("대기 중이거나 완료된 리뷰가 있으면 다시 만들지 않고 요청도 조회도 저장된 상태를 돌려준다")
    void requestAndGet_existingReview_returnsStoredState(String status, ApiStatus expected) {
        // Given
        UUID versionId = fixture.insertPromptVersion(1);
        fixture.insertReview(versionId, status);

        // When
        ReviewResult requested = service.request(AUTHOR_ID, POST_ID);
        ReviewResult queried = service.get(AUTHOR_ID, POST_ID);

        // Then
        assertThat(requested.status()).isEqualTo(expected);
        assertThat(requested.requestedAt()).isEqualTo(REQUESTED_AT);
        assertThat(queried).isEqualTo(requested);
        assertThat(requested.result()).isEqualTo(expected == ApiStatus.COMPLETED ? STORED : null);
        assertThat(fixture.countReviews()).isEqualTo(1);
        assertThat(requested.canRequestAgain()).isFalse();
        assertThat(appender.list).isEmpty();
    }

    @Test
    @DisplayName("자동 재시도를 기다리는 행은 요청도 조회도 처리 중이며 마지막 실패의 완료 시각과 결과를 드러내지 않는다")
    void requestAndGet_retryWaitingReview_returnsProcessingWithoutFailureInfo() {
        // Given
        UUID versionId = fixture.insertPromptVersion(1);
        fixture.insertRetryWaitingReview(versionId, REQUESTED_AT.plusSeconds(10));

        // When
        ReviewResult requested = service.request(AUTHOR_ID, POST_ID);
        ReviewResult queried = service.get(AUTHOR_ID, POST_ID);

        // Then
        assertThat(requested.status()).isEqualTo(ApiStatus.PROCESSING);
        assertThat(requested.isInProgress()).isTrue();
        assertThat(requested.completedAt()).isNull();
        assertThat(requested.result()).isNull();
        assertThat(queried).isEqualTo(requested);
        assertThat(fixture.countReviews()).isEqualTo(1);
    }

    @Test
    @DisplayName("실패한 리뷰를 다시 요청하면 처리 중으로 응답하고 행을 대기로 되돌려 횟수를 늘리고 최신 버전으로 다시 고정하며 사용량을 비운다")
    void request_failedReview_requeuesWithLatestVersion() {
        // Given
        UUID oldVersionId = fixture.insertPromptVersion(1);
        UUID latestVersionId = fixture.insertPromptVersion(2);
        fixture.insertFailedReview(oldVersionId, 1);
        Map<String, Object> before = jdbcTemplate.queryForMap("SELECT * FROM ai_reviews");
        given(clock.instant()).willReturn(AGAIN_AT);

        // When
        ReviewResult result = service.request(AUTHOR_ID, POST_ID);

        // Then
        assertThat(result.status()).isEqualTo(ApiStatus.PROCESSING);
        assertThat(result.isInProgress()).isTrue();
        assertThat(result.requestedAt()).isEqualTo(REQUESTED_AT);
        assertThat(result.completedAt()).isNull();
        assertThat(result.result()).isNull();
        assertThat(result.canRequestAgain()).isFalse();
        entityManager.flush();
        Map<String, Object> row = jdbcTemplate.queryForMap("SELECT * FROM ai_reviews");
        assertThat(row).containsEntry("status", "QUEUED")
                .containsEntry("request_count", 2)
                .containsEntry("round_attempt", 0)
                .containsEntry("attempt", 1)
                .containsEntry("prompt_version_id", latestVersionId)
                .containsEntry("request_id", before.get("request_id"))
                .containsEntry("requested_at", before.get("requested_at"))
                .containsEntry("last_requested_at", Timestamp.from(AGAIN_AT))
                .containsEntry("started_at", null)
                .containsEntry("completed_at", null)
                .containsEntry("error_code", null)
                .containsEntry("next_attempt_at", null)
                .containsEntry("input_tokens", null)
                .containsEntry("output_tokens", null)
                .containsEntry("latency_ms", null)
                .containsEntry("openai_response_id", null);
        assertThat(fixture.countReviews()).isEqualTo(1);
    }

    @Test
    @DisplayName("재요청 로그는 요청 횟수·이번 요청 ID·재고정한 버전을 남기고 신규가 아님을 표시하며 같은 요청을 다시 보내면 남기지 않는다")
    void request_failedReview_logsRequestedAgainOnce() {
        // Given
        fixture.insertPromptVersion(1);
        UUID latestVersionId = fixture.insertPromptVersion(2);
        fixture.insertFailedReview(latestVersionId, 1);
        String reviewId = jdbcTemplate.queryForObject("SELECT id FROM ai_reviews", String.class);

        // When
        service.request(AUTHOR_ID, POST_ID);
        service.request(AUTHOR_ID, POST_ID);

        // Then
        assertThat(appender.list).singleElement().satisfies(event -> {
            String keyValues = event.getKeyValuePairs().toString();
            assertThat(keyValues).contains(
                    "type=\"ai_review\"",
                    "event=\"review_requested\"",
                    "aiReviewId=\"" + reviewId + "\"",
                    "postId=\"" + POST_ID + "\"",
                    "userId=\"" + AUTHOR_ID + "\"",
                    "promptVersion=\"2\"",
                    "model=\"" + MODEL + "\"",
                    "requestCount=\"2\"",
                    "requestId=\"" + requestId + "\"",
                    "created=\"false\""
            );
        });
    }

    @Test
    @DisplayName("같은 재요청을 다시 보내도 요청 횟수는 한 번만 늘고 처리 중으로 응답한다")
    void request_failedReviewTwice_isIdempotent() {
        // Given
        fixture.insertFailedReview(fixture.insertPromptVersion(1), 1);

        // When
        ReviewResult first = service.request(AUTHOR_ID, POST_ID);
        ReviewResult second = service.request(AUTHOR_ID, POST_ID);

        // Then
        assertThat(second).isEqualTo(first);
        assertThat(second.status()).isEqualTo(ApiStatus.PROCESSING);
        entityManager.flush();
        assertThat(
                jdbcTemplate.queryForObject("SELECT request_count FROM ai_reviews", Integer.class))
                .isEqualTo(2);
    }

    @Test
    @DisplayName("요청 횟수가 상한에 닿은 뒤의 요청은 400이고 행도 로그도 바뀌지 않는다")
    void request_failedReviewAtLimit_throwsBusinessExceptionAndKeepsRow() {
        // Given
        UUID versionId = fixture.insertPromptVersion(1);
        fixture.insertPromptVersion(2);
        fixture.insertFailedReview(versionId, 3);
        Map<String, Object> before = jdbcTemplate.queryForMap("SELECT * FROM ai_reviews");

        // When & Then
        assertThatThrownBy(() -> service.request(AUTHOR_ID, POST_ID))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BUSINESS_ERROR);
                    assertThat(exception).hasMessage(LIMIT_REACHED);
                });
        entityManager.flush();
        assertThat(jdbcTemplate.queryForMap("SELECT * FROM ai_reviews")).isEqualTo(before);
        assertThat(appender.list).isEmpty();
    }

    @Test
    @DisplayName("상한에 닿았으면 AI 설정이 꺼져 있어도 횟수 400이 먼저다")
    void request_failedReviewAtLimitWhileFeatureOff_reportsLimitFirst() {
        // Given
        fixture.insertFailedReview(fixture.insertPromptVersion(1), 3);
        fixture.insertPromptVersion(
                2,
                MODEL,
                "0"
        );

        // When & Then
        assertThatThrownBy(() -> service.request(AUTHOR_ID, POST_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessage(LIMIT_REACHED);
    }

    @Test
    @DisplayName("쓸 수 있는 버전이 없으면 재요청은 400이고 행은 실패 그대로이며 횟수도 늘지 않는다")
    void request_failedReviewWithoutUsableVersion_keepsFailedRow() {
        // Given
        fixture.insertFailedReview(fixture.insertPromptVersion(1), 1);
        fixture.insertPromptVersion(
                2,
                MODEL,
                "0"
        );
        Map<String, Object> before = jdbcTemplate.queryForMap("SELECT * FROM ai_reviews");

        // When & Then
        assertThatThrownBy(() -> service.request(AUTHOR_ID, POST_ID))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BUSINESS_ERROR);
                    assertThat(exception).hasMessage("지금은 AI 리뷰를 요청할 수 없습니다.");
                });
        entityManager.flush();
        assertThat(jdbcTemplate.queryForMap("SELECT * FROM ai_reviews")).isEqualTo(before);
        assertThat(before).containsEntry("status", "FAILED").containsEntry("request_count", 1);
        assertThat(appender.list).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({
            "FAILED, 1, true",
            "FAILED, 2, true",
            "FAILED, 3, false",
            "QUEUED, 1, false",
            "COMPLETED, 1, false"
    })
    @DisplayName("조회 응답의 재요청 가능 여부는 실패이고 요청 횟수가 남았을 때만 참이다")
    void get_existingReview_returnsCanRequestAgain(
            String status,
            int requestCount,
            boolean expected
    ) {
        // Given
        UUID versionId = fixture.insertPromptVersion(1);
        fixture.insertReview(versionId, status);
        jdbcTemplate.update("UPDATE ai_reviews SET request_count = ?", requestCount);

        // When
        ReviewResult result = service.get(AUTHOR_ID, POST_ID);

        // Then
        assertThat(result.canRequestAgain()).isEqualTo(expected);
    }

    @Test
    @DisplayName("요청 전 상태는 재요청할 수 없다")
    void get_noReview_cannotRequestAgain() {
        // When & Then
        assertThat(service.get(AUTHOR_ID, POST_ID).canRequestAgain()).isFalse();
    }

    @Test
    @DisplayName("이미 리뷰가 있으면 기능이 꺼져 있어도 기존 상태를 돌려준다")
    void request_existingReviewWhileFeatureOff_returnsExistingState() {
        // Given
        fixture.insertQueuedReview(fixture.insertPromptVersion(1), REQUESTED_AT);
        fixture.insertPromptVersion(
                2,
                MODEL,
                "0"
        );

        // When
        ReviewResult result = service.request(AUTHOR_ID, POST_ID);

        // Then
        assertThat(result.status()).isEqualTo(ApiStatus.PROCESSING);
    }

    @Test
    @DisplayName("작성자가 아니거나 없는 게시물이면 리뷰 존재 여부를 숨기려고 요청도 조회도 게시물 404다")
    void requestAndGet_notAuthorOrUnknownPost_throwsNotFound() {
        // Given
        fixture.insertOtherUser();
        fixture.insertQueuedReview(fixture.insertPromptVersion(1), REQUESTED_AT);

        // When & Then
        assertThatThrownBy(() -> service.request(OTHER_USER_ID, POST_ID))
                .isInstanceOfSatisfying(NotFoundException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BUSINESS_ERROR);
                    assertThat(exception).hasMessage(POST_NOT_FOUND);
                });
        assertThatThrownBy(() -> service.get(OTHER_USER_ID, POST_ID))
                .isInstanceOf(NotFoundException.class)
                .hasMessage(POST_NOT_FOUND);
        assertThatThrownBy(() -> service.request(AUTHOR_ID, UUID.randomUUID()))
                .isInstanceOf(NotFoundException.class)
                .hasMessage(POST_NOT_FOUND);
        assertThat(fixture.countReviews()).isEqualTo(1);
    }

    @ParameterizedTest
    @CsvSource({
            "PENDING, false, false, false, false",
            "VALIDATING, false, false, false, false",
            "REJECTED, false, false, false, false",
            "APPROVED, true, false, false, false",
            "APPROVED, false, true, false, false",
            "APPROVED, false, false, true, false",
            "APPROVED, false, false, false, true"
    })
    @DisplayName("공개 조건을 만족하지 않는 게시물은 요청도 조회도 404이고 행을 만들지 않는다")
    void requestAndGet_invisiblePost_throwsNotFound(
            String moderationStatus,
            boolean postDeleted,
            boolean topicDeleted,
            boolean photoDeleted,
            boolean authorDeleted
    ) {
        // Given
        new DatabaseCleaner(jdbcTemplate).clean();
        fixture.insertUser(
                AUTHOR_ID,
                "ai-review-author@example.com",
                "ACTIVE",
                authorDeleted
        );
        fixture.insertPost(
                moderationStatus,
                postDeleted,
                topicDeleted,
                photoDeleted
        );
        fixture.insertPromptVersion(1);

        // When & Then
        assertThatThrownBy(() -> service.request(AUTHOR_ID, POST_ID))
                .isInstanceOf(NotFoundException.class)
                .hasMessage(POST_NOT_FOUND);
        assertThatThrownBy(() -> service.get(AUTHOR_ID, POST_ID))
                .isInstanceOf(NotFoundException.class)
                .hasMessage(POST_NOT_FOUND);
        assertThat(fixture.countReviews()).isZero();
    }

    @Test
    @DisplayName("프롬프트 버전이 하나도 없으면 400이고 행을 만들지 않는다")
    void request_noPromptVersion_throwsBusinessException() {
        // When & Then
        assertUnavailable();
    }

    @Test
    @DisplayName("최신 버전의 출력 스키마가 현재와 다르면 이전 버전이 호환돼도 400이다")
    void request_latestVersionIncompatible_doesNotFallBack() {
        // Given
        fixture.insertPromptVersion(1);
        fixture.insertPromptVersion(
                2,
                MODEL,
                "0"
        );

        // When & Then
        assertUnavailable();
    }

    @Test
    @DisplayName("최신 버전의 모델이 허용되지 않으면 400이다")
    void request_unsupportedModel_throwsBusinessException() {
        // Given
        fixture.insertPromptVersion(
                1,
                "gpt-unsupported",
                "1"
        );

        // When & Then
        assertUnavailable();
    }

    @Test
    @DisplayName("본인의 공개된 게시물에 리뷰를 요청한 적 없으면 조회는 요청 전 상태로 응답하고 행을 만들지 않는다")
    void get_noReview_returnsNone() {
        // When
        ReviewResult result = service.get(AUTHOR_ID, POST_ID);

        // Then
        assertThat(result.status()).isEqualTo(ApiStatus.NONE);
        assertThat(result.requestedAt()).isNull();
        assertThat(result.completedAt()).isNull();
        assertThat(result.result()).isNull();
        assertThat(fixture.countReviews()).isZero();
    }

    private void assertUnavailable() {
        assertThatThrownBy(() -> service.request(AUTHOR_ID, POST_ID))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BUSINESS_ERROR);
                    assertThat(exception).hasMessage("지금은 AI 리뷰를 요청할 수 없습니다.");
                });
        assertThat(fixture.countReviews()).isZero();
        assertThat(appender.list).isEmpty();
    }
}
