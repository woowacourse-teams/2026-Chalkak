package com.chalkak.backend.aireview.service;

import static com.chalkak.backend.aireview.service.AiReviewFixture.AUTHOR_ID;
import static com.chalkak.backend.aireview.service.AiReviewFixture.MODEL;
import static com.chalkak.backend.aireview.service.AiReviewFixture.OTHER_USER_ID;
import static com.chalkak.backend.aireview.service.AiReviewFixture.POST_ID;
import static com.chalkak.backend.aireview.service.AiReviewFixture.REQUESTED_AT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willCallRealMethod;

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
import java.time.Clock;
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
        willCallRealMethod().given(aiReviewGenerator).canRun(any());
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
                    "model=\"" + MODEL + "\""
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
            "COMPLETED, COMPLETED",
            "FAILED, FAILED"
    })
    @DisplayName("이미 리뷰가 있으면 다시 만들지 않고 요청도 조회도 저장된 상태를 돌려주며 실패는 결과가 없다")
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
    @DisplayName("요청한 적 없으면 조회는 404다")
    void get_noReview_throwsNotFound() {
        // When & Then
        assertThatThrownBy(() -> service.get(AUTHOR_ID, POST_ID))
                .isInstanceOfSatisfying(NotFoundException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BUSINESS_ERROR);
                    assertThat(exception).hasMessage("AI 리뷰를 찾을 수 없습니다.");
                });
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
