package com.chalkak.backend.aireview.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chalkak.backend.aireview.domain.AiPromptType;
import com.chalkak.backend.aireview.domain.AiPromptVersion;
import com.chalkak.backend.aireview.domain.AiReview;
import com.chalkak.backend.aireview.domain.AiReview.Status;
import com.chalkak.backend.aireview.repository.AiPromptVersionRepository;
import com.chalkak.backend.aireview.repository.AiReviewRepository;
import com.chalkak.backend.support.IntegrationTestSupport;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class AiReviewRepositoryTest extends IntegrationTestSupport {

    private static final UUID ADMIN_ID = UUID.fromString("019a0000-0000-7000-8000-000000000690");
    private static final UUID USER_ID = UUID.fromString("019a0000-0000-7000-8000-000000000691");
    private static final UUID POST_ID = UUID.fromString("019a0000-0000-7000-8000-000000000692");
    private static final Instant REQUESTED_AT = Instant.parse("2026-10-20T05:00:00Z");

    @Autowired
    private AiReviewRepository reviewRepository;

    @Autowired
    private AiPromptVersionRepository promptVersionRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID promptVersionId;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("""
                INSERT INTO admins (id, username, password, created_at, updated_at)
                VALUES (?, 'ai-review-repository', 'test-password', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, ADMIN_ID);
        jdbcTemplate.update("""
                INSERT INTO users (id, email, status, signature_original_storage_key,
                    created_at, updated_at)
                VALUES (?, 'ai-review-repository@example.com', 'ACTIVE', 'chalkak/test/sign.png',
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, USER_ID);
        jdbcTemplate.update("""
                INSERT INTO topics (id, title, topic_date, starts_at, ends_at, created_at, updated_at)
                VALUES ('019a0000-0000-7000-8000-0000000006b2', '노을', CURRENT_DATE + 303,
                    '2026-10-01T00:00:00Z', '2026-10-02T00:00:00Z',
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """);
        jdbcTemplate.update("""
                INSERT INTO photos (id, original_storage_key, created_at, updated_at)
                VALUES ('019a0000-0000-7000-8000-0000000006c2', 'chalkak/test/ai-review/repo.webp',
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """);
        jdbcTemplate.update("""
                INSERT INTO posts (id, user_id, topic_id, photo_id, moderation_status,
                    created_at, updated_at)
                VALUES (?, ?, '019a0000-0000-7000-8000-0000000006b2',
                    '019a0000-0000-7000-8000-0000000006c2', 'APPROVED',
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, POST_ID, USER_ID);
        promptVersionId = insertVersion(1);
    }

    @Test
    @DisplayName("게시물에 리뷰가 없으면 한 행을 넣고 1을 돌려주며 이어서 같은 값으로 읽힌다")
    void insertIfAbsent_newPost_insertsQueuedRow() {
        // Given
        AiReview review = request(UUID.randomUUID());

        // When
        int inserted = reviewRepository.insertIfAbsent(review);
        AiReview found = reviewRepository.findByPostId(POST_ID).orElseThrow();

        // Then
        assertThat(inserted).isEqualTo(1);
        assertThat(found.getId()).isNotNull();
        assertThat(found.getStatus()).isEqualTo(Status.QUEUED);
        assertThat(found.getAttempt()).isZero();
        assertThat(found.getPromptVersionId()).isEqualTo(promptVersionId);
        assertThat(found.getRequestId()).isEqualTo(review.getRequestId());
        assertThat(found.getRequestedAt()).isEqualTo(REQUESTED_AT);
        assertThat(found.getResult()).isNull();
        assertThat(found.getCompletedAt()).isNull();
    }

    @Test
    @DisplayName("이미 리뷰가 있으면 0을 돌려주고 기존 행을 바꾸지 않는다")
    void insertIfAbsent_existingPost_keepsFirstRow() {
        // Given
        UUID firstRequestId = UUID.randomUUID();
        reviewRepository.insertIfAbsent(request(firstRequestId));

        // When
        int inserted = reviewRepository.insertIfAbsent(request(UUID.randomUUID()));

        // Then
        assertThat(inserted).isZero();
        assertThat(reviewRepository.findByPostId(POST_ID).orElseThrow().getRequestId())
                .isEqualTo(firstRequestId);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ai_reviews", Integer.class))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("리뷰가 없으면 게시물로 찾아도 비어 있다")
    void findByPostId_noReview_returnsEmpty() {
        // When & Then
        assertThat(reviewRepository.findByPostId(POST_ID)).isEmpty();
    }

    @Test
    @DisplayName("용도의 최신 버전은 가장 큰 번호이고 없으면 비어 있다")
    void findLatestByType_returnsHighestVersion() {
        // Given
        assertThat(promptVersionRepository.findLatestByType(AiPromptType.PHOTO_REVIEW))
                .map(AiPromptVersion::getVersion).contains(1);
        UUID latestId = insertVersion(3);
        insertVersion(2);

        // When & Then
        assertThat(promptVersionRepository.findLatestByType(AiPromptType.PHOTO_REVIEW))
                .map(AiPromptVersion::getId).contains(latestId);
        jdbcTemplate.update("DELETE FROM ai_prompt_versions");
        assertThat(promptVersionRepository.findLatestByType(AiPromptType.PHOTO_REVIEW)).isEmpty();
    }

    @Test
    @DisplayName("처리 중인데 임대 만료 시각이 없으면 CHECK 제약이 거절한다")
    void processingWithoutLease_isRejectedByCheckConstraint() {
        // When & Then
        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO ai_reviews (post_id, user_id, prompt_version_id, status, attempt,
                    request_id, requested_at)
                VALUES (?, ?, ?, 'PROCESSING', 1, ?, CURRENT_TIMESTAMP)
                """, POST_ID, USER_ID, promptVersionId, UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_ai_reviews");
    }

    private AiReview request(UUID requestId) {
        return AiReview.request(POST_ID, USER_ID, promptVersionId, requestId, REQUESTED_AT);
    }

    private UUID insertVersion(int version) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO ai_prompt_versions (id, type, version, system_prompt,
                    user_prompt_template, model, parameters, output_schema_version,
                    created_by_admin_id, created_at)
                VALUES (?, 'PHOTO_REVIEW', ?, '시스템', '{{topicTitle}}', 'gpt-6.1-sol',
                    CAST('{"maxOutputTokens":700}' AS jsonb), '1', ?, CURRENT_TIMESTAMP)
                """, id, version, ADMIN_ID);
        return id;
    }
}
