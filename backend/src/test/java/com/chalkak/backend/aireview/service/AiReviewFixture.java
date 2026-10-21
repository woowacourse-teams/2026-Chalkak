package com.chalkak.backend.aireview.service;

import com.chalkak.backend.support.DatabaseCleaner;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * AI 리뷰 통합 테스트가 쓰는 DB 픽스처. 서비스를 거치지 않고 SQL로 직접 넣어 CHECK 제약을 만족하는 행을 만든다.
 */
public final class AiReviewFixture {

    public static final UUID ADMIN_ID = UUID.fromString("019a0000-0000-7000-8000-000000000689");
    public static final UUID AUTHOR_ID = UUID.fromString("019a0000-0000-7000-8000-0000000006a1");
    public static final UUID OTHER_USER_ID = UUID
            .fromString("019a0000-0000-7000-8000-0000000006a2");
    public static final UUID POST_ID = UUID.fromString("019a0000-0000-7000-8000-0000000006d1");
    public static final UUID SECOND_POST_ID = UUID
            .fromString("019a0000-0000-7000-8000-0000000006d2");
    public static final String ORIGINAL_STORAGE_KEY = "chalkak/test/ai-review/original.webp";
    public static final String MODEL = "gpt-6.1-sol";
    public static final String TOPIC_TITLE = "노을";
    public static final Instant REQUESTED_AT = Instant.parse("2026-10-20T05:00:00Z");
    public static final Instant COMPLETED_AT = Instant.parse("2026-10-20T05:00:21Z");

    private static final UUID TOPIC_ID = UUID.fromString("019a0000-0000-7000-8000-0000000006b1");
    private static final UUID PHOTO_ID = UUID.fromString("019a0000-0000-7000-8000-0000000006c1");
    private static final UUID SECOND_PHOTO_ID = UUID
            .fromString("019a0000-0000-7000-8000-0000000006c2");
    private static final String DELETED_AT = "TIMESTAMPTZ '2026-10-01T00:00:00Z'";
    private static final String RESULT_JSON = """
            {"overallScore":7.5,
             "criteria":[{"key":"COMPOSITION","label":"구도","score":7.5}],
             "distinctivePerspective":true,
             "feedback":"구도가 좋습니다."}
            """;
    private static final String OUTPUT_JSON = "{\"compositionScore\":7.46,\"themeExpressionScore\":8,\"feedback\":\"구도가 좋습니다.\"}";

    private final JdbcTemplate jdbcTemplate;

    public AiReviewFixture(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 작성자 회원, 승인된 공개 게시물, 관리자를 넣는다. */
    public void insertPublicPost() {
        insertAdmin();
        insertUser(
                AUTHOR_ID,
                "ai-review-author@example.com",
                "ACTIVE",
                false
        );
        insertPost(
                "APPROVED",
                false,
                false,
                false
        );
    }

    /** 같은 주제에 다른 회원이 올린 두 번째 승인 게시물을 넣는다. insertPublicPost 뒤에 부른다. */
    public void insertSecondPublicPost() {
        insertOtherUser();
        insertPhoto(
                SECOND_PHOTO_ID,
                "original-2.webp",
                false
        );
        insertPostRow(
                SECOND_POST_ID,
                OTHER_USER_ID,
                SECOND_PHOTO_ID,
                "APPROVED",
                false
        );
    }

    public void insertOtherUser() {
        insertUser(
                OTHER_USER_ID,
                "ai-review-other@example.com",
                "ACTIVE",
                false
        );
    }

    public void insertAdmin() {
        jdbcTemplate.update(
                """
                        INSERT INTO admins (id, username, password, created_at, updated_at)
                        VALUES (?, 'ai-review-fixture', 'test-password', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                        """,
                ADMIN_ID);
    }

    public void insertUser(
            UUID id,
            String email,
            String status,
            boolean deleted
    ) {
        jdbcTemplate.update(
                """
                        INSERT INTO users (id, email, status, signature_original_storage_key,
                            created_at, updated_at, deleted_at)
                        VALUES (?, ?, CAST(? AS user_status), ?,
                            CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, %s)
                        """.formatted(deletedAt(deleted)),
                id,
                email,
                status,
                "chalkak/test/ai-review/sign-" + id + ".png"
        );
    }

    public void insertPost(
            String moderationStatus,
            boolean postDeleted,
            boolean topicDeleted,
            boolean photoDeleted
    ) {
        jdbcTemplate.update(
                """
                        INSERT INTO topics (id, title, topic_date, starts_at, ends_at,
                            created_at, updated_at, deleted_at)
                        VALUES (?, ?, CURRENT_DATE + 302, '2026-10-01T00:00:00Z', '2026-10-02T00:00:00Z',
                            CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, %s)
                        """
                        .formatted(deletedAt(topicDeleted)),
                TOPIC_ID,
                TOPIC_TITLE
        );
        insertPhoto(
                PHOTO_ID,
                "original.webp",
                photoDeleted
        );
        insertPostRow(
                POST_ID,
                AUTHOR_ID,
                PHOTO_ID,
                moderationStatus,
                postDeleted
        );
    }

    public UUID insertPromptVersion(int version) {
        return insertPromptVersion(
                version,
                MODEL,
                "1"
        );
    }

    public UUID insertPromptVersion(
            int version,
            String model,
            String outputSchemaVersion
    ) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                """
                        INSERT INTO ai_prompt_versions (id, type, version, system_prompt,
                            user_prompt_template, model, parameters, output_schema_version,
                            created_by_admin_id, created_at)
                        VALUES (?, 'PHOTO_REVIEW', ?, '시스템', '주제는 {{topicTitle}}', ?,
                            CAST('{"imageDetail":"LOW","maxOutputTokens":700,"distinctivePerspectiveThreshold":7.0}'
                                AS jsonb),
                            ?, ?, CURRENT_TIMESTAMP)
                        """,
                id,
                version,
                model,
                outputSchemaVersion,
                ADMIN_ID
        );
        return id;
    }

    public UUID insertQueuedReview(UUID promptVersionId, Instant requestedAt) {
        return insertQueuedReview(
                POST_ID,
                AUTHOR_ID,
                promptVersionId,
                requestedAt
        );
    }

    /** 두 번째 게시물의 대기 행을 넣는다. */
    public UUID insertSecondQueuedReview(UUID promptVersionId, Instant requestedAt) {
        return insertQueuedReview(
                SECOND_POST_ID,
                OTHER_USER_ID,
                promptVersionId,
                requestedAt
        );
    }

    public UUID insertQueuedReview(
            UUID postId,
            UUID userId,
            UUID promptVersionId,
            Instant requestedAt
    ) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                """
                        INSERT INTO ai_reviews (id, post_id, user_id, prompt_version_id, status, attempt,
                            request_id, requested_at, last_requested_at)
                        VALUES (?, ?, ?, ?, 'QUEUED', 0, ?, ?, ?)
                        """,
                id,
                postId,
                userId,
                promptVersionId,
                UUID.randomUUID(),
                Timestamp.from(requestedAt),
                Timestamp.from(requestedAt)
        );
        return id;
    }

    /** 첫 게시물의 처리 중 행을 넣는다. 시작 시각은 요청 시각과 같다. */
    public UUID insertProcessingReview(
            UUID promptVersionId,
            int attempt,
            Instant leaseExpiresAt
    ) {
        UUID id = insertQueuedReview(promptVersionId, REQUESTED_AT);
        jdbcTemplate.update(
                """
                        UPDATE ai_reviews SET status = 'PROCESSING', attempt = ?, round_attempt = ?,
                            lease_expires_at = ?, started_at = requested_at
                        WHERE id = ?
                        """,
                attempt,
                attempt,
                Timestamp.from(leaseExpiresAt),
                id
        );
        return id;
    }

    /**
     * 첫 게시물의 행을 status 상태로 넣는다. status는 QUEUED, COMPLETED, FAILED(TIMEOUT) 중 하나다.
     */
    public UUID insertReview(UUID promptVersionId, String status) {
        UUID id = insertQueuedReview(promptVersionId, REQUESTED_AT);
        if ("QUEUED".equals(status)) {
            return id;
        }
        boolean completed = "COMPLETED".equals(status);
        jdbcTemplate.update(
                """
                        UPDATE ai_reviews SET status = ?, attempt = 1, round_attempt = 1,
                            started_at = requested_at,
                            completed_at = ?, error_code = ?, result = CAST(? AS jsonb),
                            output = CAST(? AS jsonb)
                        WHERE id = ?
                        """,
                status,
                Timestamp.from(COMPLETED_AT),
                completed ? null : "TIMEOUT",
                completed ? RESULT_JSON : null,
                completed ? OUTPUT_JSON : null,
                id
        );
        return id;
    }

    /**
     * 첫 게시물의 실패 행을 넣는다. 사용자 요청을 requestCount번 했고 마지막 회차가 TIMEOUT으로 실패했으며 마지막 시도의
     * 사용량이 남아 있다.
     */
    public UUID insertFailedReview(UUID promptVersionId, int requestCount) {
        UUID id = insertReview(promptVersionId, "FAILED");
        jdbcTemplate.update(
                """
                        UPDATE ai_reviews SET request_count = ?, input_tokens = 1200, output_tokens = 300,
                            latency_ms = 21000, openai_response_id = 'resp_abc'
                        WHERE id = ?
                        """,
                requestCount,
                id
        );
        return id;
    }

    /**
     * 첫 게시물의 자동 재시도 대기 행을 넣는다. 한 번 시도해 TIMEOUT으로 실패했고 nextAttemptAt까지 기다린다.
     */
    public UUID insertRetryWaitingReview(UUID promptVersionId, Instant nextAttemptAt) {
        UUID id = insertQueuedReview(promptVersionId, REQUESTED_AT);
        jdbcTemplate.update(
                """
                        UPDATE ai_reviews SET attempt = 1, round_attempt = 1, started_at = requested_at,
                            error_code = 'TIMEOUT', next_attempt_at = ?
                        WHERE id = ?
                        """,
                Timestamp.from(nextAttemptAt),
                id
        );
        return id;
    }

    public int countReviews() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ai_reviews", Integer.class);
    }

    /**
     * 커밋되는 테스트가 남긴 행을 지운다. ai_reviews는 DatabaseCleaner의 TRUNCATE ... CASCADE로 지워지고,
     * 프롬프트 버전과 관리자는 DatabaseCleaner 대상이 아니라 직접 지운다.
     */
    public void clean() {
        new DatabaseCleaner(jdbcTemplate).clean();
        jdbcTemplate.update("DELETE FROM ai_prompt_versions WHERE created_by_admin_id = ?",
                ADMIN_ID);
        jdbcTemplate.update("DELETE FROM admins WHERE id = ?", ADMIN_ID);
    }

    private void insertPhoto(
            UUID id,
            String fileName,
            boolean deleted
    ) {
        jdbcTemplate.update(
                """
                        INSERT INTO photos (id, original_storage_key, thumbnail_storage_key, metadata,
                            created_at, updated_at, deleted_at)
                        VALUES (?, ?, ?, CAST('{"width":1024,"height":768}' AS jsonb),
                            CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, %s)
                        """
                        .formatted(deletedAt(deleted)),
                id,
                "chalkak/test/ai-review/" + fileName,
                "chalkak/test/ai-review/thumb-" + fileName
        );
    }

    private void insertPostRow(
            UUID id,
            UUID userId,
            UUID photoId,
            String moderationStatus,
            boolean deleted
    ) {
        jdbcTemplate.update(
                """
                        INSERT INTO posts (id, user_id, topic_id, photo_id, post_image_upload_id, title,
                            moderation_status, moderated_at, created_at, updated_at, deleted_at)
                        VALUES (?, ?, ?, ?, NULL, '리뷰 대상', CAST(? AS moderation_status),
                            NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, %s)
                        """
                        .formatted(deletedAt(deleted)),
                id,
                userId,
                TOPIC_ID,
                photoId,
                moderationStatus
        );
    }

    private String deletedAt(boolean deleted) {
        return deleted ? DELETED_AT : "NULL";
    }
}
