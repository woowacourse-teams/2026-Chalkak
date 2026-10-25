package com.chalkak.backend.admin.infrastructure.persistence.aireview;

import static org.assertj.core.api.Assertions.assertThat;

import com.chalkak.backend.admin.repository.AdminPage;
import com.chalkak.backend.admin.repository.aireview.AdminAiReviewQueryRepository;
import com.chalkak.backend.admin.repository.aireview.AdminAiReviewQueryRepository.Criteria;
import com.chalkak.backend.admin.repository.aireview.AdminAiReviewQueryRepository.SummaryProjection;
import com.chalkak.backend.aireview.domain.AiReview.Status;
import com.chalkak.backend.aireview.service.AiReviewFixture;
import com.chalkak.backend.post.domain.ModerationStatus;
import com.chalkak.backend.post.repository.PostRepository;
import com.chalkak.backend.support.IntegrationTestSupport;
import com.chalkak.backend.user.domain.UserStatus;
import jakarta.persistence.EntityManager;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class AdminAiReviewQueryRepositoryTest extends IntegrationTestSupport {

    private static final UUID ACTIVE_TOPIC_ID = UUID
            .fromString("019a0000-0000-7000-8000-000000069101");
    private static final UUID DELETED_TOPIC_ID = UUID
            .fromString("019a0000-0000-7000-8000-000000069102");
    private static final Instant BASE_TIME = Instant.parse("2026-10-20T05:00:00Z");
    private static final String RESULT_JSON = """
            {"overallScore":8.25,"criteria":[],"distinctivePerspective":false,
             "feedback":{"summary":"요약","strength":"강점","weakness":"약점","suggestion":"제안"}}
            """;

    @Autowired
    private AdminAiReviewQueryRepository repository;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    private AiReviewFixture fixture;
    private UUID firstVersionId;
    private UUID secondVersionId;

    @BeforeEach
    void setUp() {
        fixture = new AiReviewFixture(jdbcTemplate);
        fixture.insertAdmin();
        firstVersionId = fixture.insertPromptVersion(1);
        secondVersionId = fixture.insertPromptVersion(
                2,
                "gpt-6.1-terra",
                "2"
        );
        insertTopic(
                ACTIVE_TOPIC_ID,
                401,
                false
        );
        insertTopic(
                DELETED_TOPIC_ID,
                402,
                true
        );
    }

    @Test
    @DisplayName("조건 없이 조회하면 마지막 요청 시각 내림차순, 같으면 ID 내림차순으로 정렬한다")
    void findAiReviews_noFilter_sortsByLastRequestedAtThenIdDescending() {
        // Given
        UUID oldest = insertReview(
                1,
                firstVersionId,
                BASE_TIME
        );
        UUID middle = insertReview(
                2,
                firstVersionId,
                BASE_TIME.plusSeconds(60)
        );
        UUID tiedLowId = insertReview(
                3,
                reviewId(1),
                firstVersionId,
                BASE_TIME.plusSeconds(120)
        );
        UUID tiedHighId = insertReview(
                4,
                reviewId(2),
                firstVersionId,
                BASE_TIME.plusSeconds(120)
        );
        UUID reRequested = insertReview(
                5,
                firstVersionId,
                BASE_TIME.minusSeconds(60)
        );
        jdbcTemplate.update(
                "UPDATE ai_reviews SET last_requested_at = ? WHERE id = ?",
                Timestamp.from(BASE_TIME.plusSeconds(600)),
                reRequested
        );
        flushAndClear();

        // When
        AdminPage<SummaryProjection> page = repository.findAiReviews(
                new Criteria(null, null),
                1,
                20
        );

        // Then
        assertThat(page.items()).extracting(SummaryProjection::aiReviewId)
                .containsExactly(
                        reRequested,
                        tiedHighId,
                        tiedLowId,
                        middle,
                        oldest
                );
    }

    @Test
    @DisplayName("상태만 지정하면 그 상태의 리뷰만 조회한다")
    void findAiReviews_statusOnly_filtersByStatus() {
        // Given
        UUID queued = insertReview(
                1,
                firstVersionId,
                BASE_TIME
        );
        UUID failed = insertReview(
                2,
                secondVersionId,
                BASE_TIME.plusSeconds(10)
        );
        UUID completed = insertReview(
                3,
                firstVersionId,
                BASE_TIME.plusSeconds(20)
        );
        markFailed(failed);
        markCompleted(completed);
        flushAndClear();

        // When
        AdminPage<SummaryProjection> failedPage = repository.findAiReviews(
                new Criteria(Status.FAILED, null),
                1,
                20
        );
        AdminPage<SummaryProjection> queuedPage = repository.findAiReviews(
                new Criteria(Status.QUEUED, null),
                1,
                20
        );

        // Then
        assertThat(failedPage.items()).extracting(SummaryProjection::aiReviewId)
                .containsExactly(failed);
        assertThat(queuedPage.items()).extracting(SummaryProjection::aiReviewId)
                .containsExactly(queued);
    }

    @Test
    @DisplayName("프롬프트 버전 ID만 지정하면 그 버전의 리뷰만 조회한다")
    void findAiReviews_promptVersionOnly_filtersByPromptVersion() {
        // Given
        insertReview(
                1,
                firstVersionId,
                BASE_TIME
        );
        UUID onSecond = insertReview(
                2,
                secondVersionId,
                BASE_TIME.plusSeconds(10)
        );
        flushAndClear();

        // When
        AdminPage<SummaryProjection> page = repository.findAiReviews(
                new Criteria(null, secondVersionId),
                1,
                20
        );

        // Then
        assertThat(page.items()).extracting(SummaryProjection::aiReviewId)
                .containsExactly(onSecond);
        assertThat(page.items().get(0).promptVersionId()).isEqualTo(secondVersionId);
        assertThat(page.items().get(0).promptVersion()).isEqualTo(2);
        assertThat(page.items().get(0).promptModel()).isEqualTo("gpt-6.1-terra");
    }

    @Test
    @DisplayName("상태와 프롬프트 버전을 함께 지정하면 두 조건을 모두 만족하는 리뷰만 조회한다")
    void findAiReviews_statusAndPromptVersion_filtersByBoth() {
        // Given
        UUID failedOnFirst = insertReview(
                1,
                firstVersionId,
                BASE_TIME
        );
        UUID failedOnSecond = insertReview(
                2,
                secondVersionId,
                BASE_TIME.plusSeconds(10)
        );
        insertReview(
                3,
                secondVersionId,
                BASE_TIME.plusSeconds(20)
        );
        markFailed(failedOnFirst);
        markFailed(failedOnSecond);
        flushAndClear();

        // When
        AdminPage<SummaryProjection> page = repository.findAiReviews(
                new Criteria(Status.FAILED, secondVersionId),
                1,
                20
        );

        // Then
        assertThat(page.items()).extracting(SummaryProjection::aiReviewId)
                .containsExactly(failedOnSecond);
    }

    @Test
    @DisplayName("없는 프롬프트 버전 ID로 조회하면 빈 목록을 반환한다")
    void findAiReviews_unknownPromptVersion_returnsEmpty() {
        // Given
        insertReview(
                1,
                firstVersionId,
                BASE_TIME
        );
        flushAndClear();

        // When
        AdminPage<SummaryProjection> page = repository.findAiReviews(
                new Criteria(null, UUID.randomUUID()),
                1,
                20
        );

        // Then
        assertThat(page.items()).isEmpty();
        assertThat(page.hasNext()).isFalse();
    }

    @Test
    @DisplayName("숨겨진 게시물이나 탈퇴한 작성자의 리뷰도 목록에 포함하고 원값을 그대로 담는다")
    void findAiReviews_hiddenReviews_areIncludedWithRawValues() {
        // Given
        UUID visible = insertReview(
                1,
                firstVersionId,
                BASE_TIME
        );
        UUID postDeleted = insertReview(
                2,
                firstVersionId,
                BASE_TIME.plusSeconds(10),
                new Target(
                        "APPROVED",
                        true,
                        false,
                        false,
                        "ACTIVE",
                        false
                )
        );
        UUID authorWithdrawn = insertReview(
                3,
                firstVersionId,
                BASE_TIME.plusSeconds(20),
                new Target(
                        "APPROVED",
                        false,
                        false,
                        false,
                        "ACTIVE",
                        true
                )
        );
        flushAndClear();

        // When
        AdminPage<SummaryProjection> page = repository.findAiReviews(
                new Criteria(null, null),
                1,
                20
        );

        // Then
        assertThat(page.items()).extracting(SummaryProjection::aiReviewId)
                .containsExactly(
                        authorWithdrawn,
                        postDeleted,
                        visible
                );
        SummaryProjection withdrawn = page.items().get(0);
        assertThat(withdrawn.authorDeletedAt()).isNotNull();
        assertThat(withdrawn.authorId()).isEqualTo(userId(3));
        assertThat(withdrawn.authorEmail()).isEqualTo("reviewer-3@example.com");
        SummaryProjection deletedPost = page.items().get(1);
        assertThat(deletedPost.postDeletedAt()).isNotNull();
        assertThat(deletedPost.postId()).isEqualTo(postId(2));
        assertThat(deletedPost.thumbnailStorageKey()).isEqualTo("chalkak/test/thumb-2.webp");
        assertThat(deletedPost.moderationStatus()).isEqualTo(ModerationStatus.APPROVED);
        assertThat(page.items().get(2).authorStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    @DisplayName("다음 페이지 유무는 페이지 크기 경계에서 정확히 판정한다")
    void findAiReviews_pageBoundary_setsHasNext() {
        // Given
        UUID first = insertReview(
                1,
                firstVersionId,
                BASE_TIME
        );
        UUID second = insertReview(
                2,
                firstVersionId,
                BASE_TIME.plusSeconds(10)
        );
        UUID third = insertReview(
                3,
                firstVersionId,
                BASE_TIME.plusSeconds(20)
        );
        flushAndClear();

        // When
        AdminPage<SummaryProjection> exactFit = repository.findAiReviews(
                new Criteria(null, null),
                1,
                3
        );
        AdminPage<SummaryProjection> oneShort = repository.findAiReviews(
                new Criteria(null, null),
                1,
                2
        );
        AdminPage<SummaryProjection> secondPage = repository.findAiReviews(
                new Criteria(null, null),
                2,
                2
        );

        // Then
        assertThat(exactFit.hasNext()).isFalse();
        assertThat(exactFit.items()).hasSize(3);
        assertThat(oneShort.hasNext()).isTrue();
        assertThat(oneShort.items()).extracting(SummaryProjection::aiReviewId)
                .containsExactly(third, second);
        assertThat(secondPage.currentPage()).isEqualTo(2);
        assertThat(secondPage.pageSize()).isEqualTo(2);
        assertThat(secondPage.hasNext()).isFalse();
        assertThat(secondPage.items()).extracting(SummaryProjection::aiReviewId)
                .containsExactly(first);
    }

    @Test
    @DisplayName("완료된 리뷰만 점수를 담고 대기 중인 리뷰는 점수·사용량·지연시간·완료 시각이 비어 있다")
    void findAiReviews_completedAndQueued_mapsScoreAndUsage() {
        // Given
        UUID queued = insertReview(
                1,
                firstVersionId,
                BASE_TIME
        );
        UUID completed = insertReview(
                2,
                firstVersionId,
                BASE_TIME.plusSeconds(10)
        );
        markCompleted(completed);
        jdbcTemplate.update(
                """
                        UPDATE ai_reviews SET request_count = 2, input_tokens = 1200,
                            cached_input_tokens = 200, output_tokens = 300, reasoning_tokens = 100,
                            latency_ms = 21000
                        WHERE id = ?
                        """,
                completed
        );
        flushAndClear();

        // When
        AdminPage<SummaryProjection> page = repository.findAiReviews(
                new Criteria(null, null),
                1,
                20
        );

        // Then
        SummaryProjection completedItem = page.items().get(0);
        assertThat(completedItem.aiReviewId()).isEqualTo(completed);
        assertThat(completedItem.status()).isEqualTo(Status.COMPLETED);
        assertThat(completedItem.result()).isNotNull();
        assertThat(completedItem.result().overallScore()).isEqualTo(8.25);
        assertThat(completedItem.errorCode()).isNull();
        assertThat(completedItem.requestCount()).isEqualTo(2);
        assertThat(completedItem.inputTokens()).isEqualTo(1200);
        assertThat(completedItem.cachedInputTokens()).isEqualTo(200);
        assertThat(completedItem.outputTokens()).isEqualTo(300);
        assertThat(completedItem.reasoningTokens()).isEqualTo(100);
        assertThat(completedItem.latencyMs()).isEqualTo(21000L);
        assertThat(completedItem.completedAt()).isNotNull();
        SummaryProjection queuedItem = page.items().get(1);
        assertThat(queuedItem.aiReviewId()).isEqualTo(queued);
        assertThat(queuedItem.result()).isNull();
        assertThat(queuedItem.inputTokens()).isNull();
        assertThat(queuedItem.cachedInputTokens()).isNull();
        assertThat(queuedItem.outputTokens()).isNull();
        assertThat(queuedItem.reasoningTokens()).isNull();
        assertThat(queuedItem.latencyMs()).isNull();
        assertThat(queuedItem.completedAt()).isNull();
        assertThat(queuedItem.requestCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("실패한 리뷰는 점수 없이 오류 코드와 마지막 시도의 사용량을 담는다")
    void findAiReviews_failedReview_mapsErrorCodeWithoutScore() {
        // Given
        UUID failed = insertReview(
                1,
                firstVersionId,
                BASE_TIME
        );
        markFailed(failed);
        jdbcTemplate.update(
                "UPDATE ai_reviews SET input_tokens = 900, latency_ms = 5000 WHERE id = ?",
                failed
        );
        flushAndClear();

        // When
        AdminPage<SummaryProjection> page = repository.findAiReviews(
                new Criteria(null, null),
                1,
                20
        );

        // Then
        SummaryProjection item = page.items().get(0);
        assertThat(item.status()).isEqualTo(Status.FAILED);
        assertThat(item.errorCode()).isEqualTo("TIMEOUT");
        assertThat(item.result()).isNull();
        assertThat(item.inputTokens()).isEqualTo(900);
        assertThat(item.outputTokens()).isNull();
        assertThat(item.latencyMs()).isEqualTo(5000L);
    }

    @Test
    @DisplayName("사진에 썸네일 저장 키가 없어도 리뷰를 조회하고 키는 null이다")
    void findAiReviews_nullThumbnailKey_returnsNullKey() {
        // Given
        insertReview(
                1,
                firstVersionId,
                BASE_TIME
        );
        jdbcTemplate.update("UPDATE photos SET thumbnail_storage_key = NULL");
        flushAndClear();

        // When
        AdminPage<SummaryProjection> page = repository.findAiReviews(
                new Criteria(null, null),
                1,
                20
        );

        // Then
        assertThat(page.items()).hasSize(1);
        assertThat(page.items().get(0).thumbnailStorageKey()).isNull();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("visibilityCases")
    @DisplayName("visibleToAuthor는 사용자 조회가 쓰는 findVisibleById의 결과와 조건마다 같다")
    void findAiReviews_visibility_matchesFindVisibleById(
            String caseName,
            Target target,
            boolean expectedVisible
    ) {
        // Given
        insertReview(
                1,
                firstVersionId,
                BASE_TIME,
                target
        );
        flushAndClear();

        // When
        SummaryProjection item = repository.findAiReviews(
                new Criteria(null, null),
                1,
                20
        ).items().get(0);

        // Then
        boolean visibleByUserQuery = postRepository.findVisibleById(postId(1)).isPresent();
        assertThat(visibleByUserQuery).isEqualTo(expectedVisible);
        assertThat(item.visibleToAuthor()).isEqualTo(visibleByUserQuery);
    }

    private static Stream<Arguments> visibilityCases() {
        return Stream.of(
                Arguments.of(
                        "모두 충족",
                        new Target(
                                "APPROVED",
                                false,
                                false,
                                false,
                                "ACTIVE",
                                false
                        ),
                        true
                ),
                Arguments.of(
                        "검수 검증 중",
                        new Target(
                                "VALIDATING",
                                false,
                                false,
                                false,
                                "ACTIVE",
                                false
                        ),
                        false
                ),
                Arguments.of(
                        "검수 대기",
                        new Target(
                                "PENDING",
                                false,
                                false,
                                false,
                                "ACTIVE",
                                false
                        ),
                        false
                ),
                Arguments.of(
                        "검수 거절",
                        new Target(
                                "REJECTED",
                                false,
                                false,
                                false,
                                "ACTIVE",
                                false
                        ),
                        false
                ),
                Arguments.of(
                        "게시물 삭제",
                        new Target(
                                "APPROVED",
                                true,
                                false,
                                false,
                                "ACTIVE",
                                false
                        ),
                        false
                ),
                Arguments.of(
                        "주제 삭제",
                        new Target(
                                "APPROVED",
                                false,
                                true,
                                false,
                                "ACTIVE",
                                false
                        ),
                        false
                ),
                Arguments.of(
                        "사진 삭제",
                        new Target(
                                "APPROVED",
                                false,
                                false,
                                true,
                                "ACTIVE",
                                false
                        ),
                        false
                ),
                Arguments.of(
                        "작성자 탈퇴",
                        new Target(
                                "APPROVED",
                                false,
                                false,
                                false,
                                "ACTIVE",
                                true
                        ),
                        false
                ),
                Arguments.of(
                        "정지된 작성자",
                        new Target(
                                "APPROVED",
                                false,
                                false,
                                false,
                                "BANNED",
                                false
                        ),
                        true
                )
        );
    }

    private UUID insertReview(
            int number,
            UUID promptVersionId,
            Instant lastRequestedAt
    ) {
        return insertReview(
                number,
                promptVersionId,
                lastRequestedAt,
                new Target(
                        "APPROVED",
                        false,
                        false,
                        false,
                        "ACTIVE",
                        false
                )
        );
    }

    private UUID insertReview(
            int number,
            UUID promptVersionId,
            Instant requestedAt,
            Target target
    ) {
        return insertReview(
                number,
                UUID.randomUUID(),
                promptVersionId,
                requestedAt,
                target
        );
    }

    private UUID insertReview(
            int number,
            UUID reviewId,
            UUID promptVersionId,
            Instant requestedAt
    ) {
        return insertReview(
                number,
                reviewId,
                promptVersionId,
                requestedAt,
                new Target(
                        "APPROVED",
                        false,
                        false,
                        false,
                        "ACTIVE",
                        false
                )
        );
    }

    private UUID insertReview(
            int number,
            UUID reviewId,
            UUID promptVersionId,
            Instant requestedAt,
            Target target
    ) {
        insertPost(number, target);
        return fixture.insertQueuedReview(
                reviewId,
                postId(number),
                userId(number),
                promptVersionId,
                requestedAt
        );
    }

    private void insertPost(int number, Target target) {
        fixture.insertUser(
                userId(number),
                "reviewer-" + number + "@example.com",
                target.userStatus(),
                target.userDeleted()
        );
        UUID photoId = UUID.fromString("019a0000-0000-7000-8000-0000000692%02d".formatted(number));
        jdbcTemplate.update(
                """
                        INSERT INTO photos (id, original_storage_key, thumbnail_storage_key, metadata,
                            created_at, updated_at, deleted_at)
                        VALUES (?, ?, ?, CAST('{"width":1024,"height":768}' AS jsonb),
                            CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, %s)
                        """
                        .formatted(deletedAt(target.photoDeleted())),
                photoId,
                "chalkak/test/original-" + number + ".webp",
                "chalkak/test/thumb-" + number + ".webp"
        );
        jdbcTemplate.update(
                """
                        INSERT INTO posts (id, user_id, topic_id, photo_id, post_image_upload_id, title,
                            moderation_status, moderated_at, created_at, updated_at, deleted_at)
                        VALUES (?, ?, ?, ?, NULL, '리뷰 대상', CAST(? AS moderation_status),
                            NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, %s)
                        """
                        .formatted(deletedAt(target.postDeleted())),
                postId(number),
                userId(number),
                target.topicDeleted() ? DELETED_TOPIC_ID : ACTIVE_TOPIC_ID,
                photoId,
                target.moderationStatus()
        );
    }

    private void insertTopic(
            UUID id,
            int dayOffset,
            boolean deleted
    ) {
        jdbcTemplate.update(
                """
                        INSERT INTO topics (id, title, topic_date, starts_at, ends_at,
                            created_at, updated_at, deleted_at)
                        VALUES (?, '노을', CURRENT_DATE + %d, '2026-10-01T00:00:00Z', '2026-10-02T00:00:00Z',
                            CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, %s)
                        """
                        .formatted(dayOffset, deletedAt(deleted)),
                id
        );
    }

    private void markCompleted(UUID reviewId) {
        jdbcTemplate.update(
                """
                        UPDATE ai_reviews SET status = 'COMPLETED', attempt = 1, round_attempt = 1,
                            started_at = requested_at, completed_at = requested_at + INTERVAL '21 seconds',
                            result = CAST(? AS jsonb), output = CAST('{"feedback":"좋아요"}' AS jsonb)
                        WHERE id = ?
                        """,
                RESULT_JSON,
                reviewId
        );
    }

    private void markFailed(UUID reviewId) {
        jdbcTemplate.update(
                """
                        UPDATE ai_reviews SET status = 'FAILED', attempt = 1, round_attempt = 1,
                            started_at = requested_at, completed_at = requested_at + INTERVAL '21 seconds',
                            error_code = 'TIMEOUT'
                        WHERE id = ?
                        """,
                reviewId
        );
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    private static String deletedAt(boolean deleted) {
        return deleted ? "TIMESTAMPTZ '2026-10-01T00:00:00Z'" : "NULL";
    }

    private static UUID reviewId(int number) {
        return UUID.fromString("019a0000-0000-7000-8000-0000000693%02d".formatted(number));
    }

    private static UUID postId(int number) {
        return UUID.fromString("019a0000-0000-7000-8000-0000000694%02d".formatted(number));
    }

    private static UUID userId(int number) {
        return UUID.fromString("019a0000-0000-7000-8000-0000000695%02d".formatted(number));
    }

    record Target(
            String moderationStatus,
            boolean postDeleted,
            boolean topicDeleted,
            boolean photoDeleted,
            String userStatus,
            boolean userDeleted
    ) {
    }
}
