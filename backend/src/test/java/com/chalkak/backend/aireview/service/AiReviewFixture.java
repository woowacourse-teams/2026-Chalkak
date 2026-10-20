package com.chalkak.backend.aireview.service;

import com.chalkak.backend.support.DatabaseCleaner;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * AI 리뷰 통합 테스트가 쓰는 DB 픽스처. 서비스를 거치지 않고 SQL로 직접 넣어 CHECK 제약을 만족하는 행을 만든다.
 */
final class AiReviewFixture {

    static final UUID ADMIN_ID = UUID.fromString("019a0000-0000-7000-8000-000000000689");
    static final UUID AUTHOR_ID = UUID.fromString("019a0000-0000-7000-8000-0000000006a1");
    static final UUID OTHER_USER_ID = UUID.fromString("019a0000-0000-7000-8000-0000000006a2");
    static final UUID POST_ID = UUID.fromString("019a0000-0000-7000-8000-0000000006d1");
    static final UUID SECOND_POST_ID = UUID.fromString("019a0000-0000-7000-8000-0000000006d2");
    static final String ORIGINAL_STORAGE_KEY = "chalkak/test/ai-review/original.webp";
    static final String MODEL = "gpt-6.1-sol";
    static final String TOPIC_TITLE = "노을";

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

    AiReviewFixture(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 작성자 회원, 승인된 공개 게시물, 관리자를 넣는다. */
    void insertPublicPost() {
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
    void insertSecondPublicPost() {
        insertUser(
                OTHER_USER_ID,
                "ai-review-other@example.com",
                "ACTIVE",
                false
        );
        jdbcTemplate.update("""
                INSERT INTO photos (id, original_storage_key, thumbnail_storage_key, metadata,
                    created_at, updated_at)
                VALUES (?, 'chalkak/test/ai-review/original-2.webp',
                    'chalkak/test/ai-review/thumb-2.webp',
                    CAST('{"width":1024,"height":768}' AS jsonb),
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, SECOND_PHOTO_ID);
        jdbcTemplate.update(
                """
                        INSERT INTO posts (id, user_id, topic_id, photo_id, post_image_upload_id, title,
                            moderation_status, moderated_at, created_at, updated_at)
                        VALUES (?, ?, ?, ?, NULL, '두 번째 리뷰 대상', CAST('APPROVED' AS moderation_status),
                            NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                        """,
                SECOND_POST_ID,
                OTHER_USER_ID,
                TOPIC_ID,
                SECOND_PHOTO_ID
        );
    }

    void insertAdmin() {
        jdbcTemplate.update(
                """
                        INSERT INTO admins (id, username, password, created_at, updated_at)
                        VALUES (?, 'ai-review-fixture', 'test-password', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                        """,
                ADMIN_ID);
    }

    void insertUser(
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
                        """.formatted(deleted ? DELETED_AT : "NULL"),
                id,
                email,
                status,
                "chalkak/test/ai-review/sign-" + id + ".png"
        );
    }

    void insertPost(
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
                        .formatted(topicDeleted ? DELETED_AT : "NULL"),
                TOPIC_ID,
                TOPIC_TITLE
        );
        jdbcTemplate.update("""
                INSERT INTO photos (id, original_storage_key, thumbnail_storage_key, metadata,
                    created_at, updated_at, deleted_at)
                VALUES (?, 'chalkak/test/ai-review/original.webp',
                    'chalkak/test/ai-review/thumb.webp',
                    CAST('{"width":1024,"height":768}' AS jsonb),
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, %s)
                """.formatted(photoDeleted ? DELETED_AT : "NULL"), PHOTO_ID);
        jdbcTemplate.update(
                """
                        INSERT INTO posts (id, user_id, topic_id, photo_id, post_image_upload_id, title,
                            moderation_status, moderated_at, created_at, updated_at, deleted_at)
                        VALUES (?, ?, ?, ?, NULL, '리뷰 대상', CAST(? AS moderation_status),
                            NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, %s)
                        """
                        .formatted(postDeleted ? DELETED_AT : "NULL"),
                POST_ID,
                AUTHOR_ID,
                TOPIC_ID,
                PHOTO_ID,
                moderationStatus
        );
    }

    UUID insertPromptVersion(
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

    UUID insertQueuedReview(UUID promptVersionId, Instant requestedAt) {
        return insertQueuedReview(
                POST_ID,
                AUTHOR_ID,
                promptVersionId,
                requestedAt
        );
    }

    UUID insertQueuedReview(
            UUID postId,
            UUID userId,
            UUID promptVersionId,
            Instant requestedAt
    ) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                """
                        INSERT INTO ai_reviews (id, post_id, user_id, prompt_version_id, status, attempt,
                            request_id, requested_at)
                        VALUES (?, ?, ?, ?, 'QUEUED', 0, ?, ?)
                        """,
                id,
                postId,
                userId,
                promptVersionId,
                UUID.randomUUID(),
                Timestamp.from(requestedAt)
        );
        return id;
    }

    UUID insertProcessingReview(
            UUID postId,
            UUID userId,
            UUID promptVersionId,
            int attempt,
            Instant requestedAt,
            Instant leaseExpiresAt
    ) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                """
                        INSERT INTO ai_reviews (id, post_id, user_id, prompt_version_id, status, attempt,
                            lease_expires_at, request_id, requested_at, started_at)
                        VALUES (?, ?, ?, ?, 'PROCESSING', ?, ?, ?, ?, ?)
                        """,
                id,
                postId,
                userId,
                promptVersionId,
                attempt,
                Timestamp.from(leaseExpiresAt),
                UUID.randomUUID(),
                Timestamp.from(requestedAt),
                Timestamp.from(requestedAt)
        );
        return id;
    }

    UUID insertCompletedReview(
            UUID promptVersionId,
            Instant requestedAt,
            Instant completedAt
    ) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                """
                        INSERT INTO ai_reviews (id, post_id, user_id, prompt_version_id, status, attempt,
                            result, output, request_id, requested_at, started_at, completed_at)
                        VALUES (?, ?, ?, ?, 'COMPLETED', 1, CAST(? AS jsonb), CAST(? AS jsonb), ?, ?, ?, ?)
                        """,
                id,
                POST_ID,
                AUTHOR_ID,
                promptVersionId,
                RESULT_JSON,
                OUTPUT_JSON,
                UUID.randomUUID(),
                Timestamp.from(requestedAt),
                Timestamp.from(requestedAt),
                Timestamp.from(completedAt)
        );
        return id;
    }

    UUID insertFailedReview(
            UUID promptVersionId,
            Instant requestedAt,
            Instant completedAt
    ) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                """
                        INSERT INTO ai_reviews (id, post_id, user_id, prompt_version_id, status, attempt,
                            error_code, request_id, requested_at, started_at, completed_at)
                        VALUES (?, ?, ?, ?, 'FAILED', 1, 'TIMEOUT', ?, ?, ?, ?)
                        """,
                id,
                POST_ID,
                AUTHOR_ID,
                promptVersionId,
                UUID.randomUUID(),
                Timestamp.from(requestedAt),
                Timestamp.from(requestedAt),
                Timestamp.from(completedAt)
        );
        return id;
    }

    int countReviews() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ai_reviews", Integer.class);
    }

    /**
     * 커밋되는 테스트가 남긴 행을 지운다. ai_reviews는 DatabaseCleaner의 TRUNCATE ... CASCADE로 지워지고,
     * 프롬프트 버전과 관리자는 DatabaseCleaner 대상이 아니라 직접 지운다.
     */
    void clean() {
        new DatabaseCleaner(jdbcTemplate).clean();
        jdbcTemplate.update("DELETE FROM ai_prompt_versions WHERE created_by_admin_id = ?",
                ADMIN_ID);
        jdbcTemplate.update("DELETE FROM admins WHERE id = ?", ADMIN_ID);
    }
}
