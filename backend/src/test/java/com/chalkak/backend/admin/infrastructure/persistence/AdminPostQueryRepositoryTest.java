package com.chalkak.backend.admin.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.chalkak.backend.admin.repository.AdminPostDetailProjection;
import com.chalkak.backend.admin.repository.AdminPostQueryCriteria;
import com.chalkak.backend.admin.repository.AdminPostQueryPage;
import com.chalkak.backend.admin.repository.AdminPostQueryRepository;
import com.chalkak.backend.admin.repository.AdminPostQuerySort;
import com.chalkak.backend.admin.repository.AdminPostSummaryProjection;
import com.chalkak.backend.post.domain.ModerationStatus;
import com.chalkak.backend.post.domain.PostImageUploadStatus;
import com.chalkak.backend.user.domain.UserStatus;
import jakarta.persistence.EntityManager;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(AdminPostQueryRepositoryImpl.class)
class AdminPostQueryRepositoryTest {

    private static final UUID ADMIN_ID =
            UUID.fromString("0198fa00-0000-7000-8000-000000000001");

    private static final UUID VALIDATING_USER_ID =
            UUID.fromString("0198fa10-0000-7000-8000-000000000001");
    private static final UUID PENDING_USER_ID =
            UUID.fromString("0198fa10-0000-7000-8000-000000000002");
    private static final UUID APPROVED_USER_ID =
            UUID.fromString("0198fa10-0000-7000-8000-000000000003");
    private static final UUID REJECTED_USER_ID =
            UUID.fromString("0198fa10-0000-7000-8000-000000000004");
    private static final UUID DELETED_USER_ID =
            UUID.fromString("0198fa10-0000-7000-8000-000000000005");

    private static final UUID FIRST_TOPIC_ID =
            UUID.fromString("0198fa20-0000-7000-8000-000000000001");
    private static final UUID SECOND_TOPIC_ID =
            UUID.fromString("0198fa20-0000-7000-8000-000000000002");
    private static final UUID DELETED_TOPIC_ID =
            UUID.fromString("0198fa20-0000-7000-8000-000000000003");

    private static final UUID VALIDATING_PHOTO_ID =
            UUID.fromString("0198fa30-0000-7000-8000-000000000001");
    private static final UUID PENDING_PHOTO_ID =
            UUID.fromString("0198fa30-0000-7000-8000-000000000002");
    private static final UUID APPROVED_PHOTO_ID =
            UUID.fromString("0198fa30-0000-7000-8000-000000000003");
    private static final UUID REJECTED_PHOTO_ID =
            UUID.fromString("0198fa30-0000-7000-8000-000000000004");
    private static final UUID DELETED_PHOTO_ID =
            UUID.fromString("0198fa30-0000-7000-8000-000000000005");

    private static final UUID VALIDATING_UPLOAD_ID =
            UUID.fromString("0198fa40-0000-7000-8000-000000000001");
    private static final UUID PENDING_UPLOAD_ID =
            UUID.fromString("0198fa40-0000-7000-8000-000000000002");
    private static final UUID APPROVED_UPLOAD_ID =
            UUID.fromString("0198fa40-0000-7000-8000-000000000003");
    private static final UUID REJECTED_UPLOAD_ID =
            UUID.fromString("0198fa40-0000-7000-8000-000000000004");

    private static final UUID VALIDATING_POST_ID =
            UUID.fromString("0198fa50-0000-7000-8000-000000000001");
    private static final UUID PENDING_POST_ID =
            UUID.fromString("0198fa50-0000-7000-8000-000000000002");
    private static final UUID APPROVED_POST_ID =
            UUID.fromString("0198fa50-0000-7000-8000-000000000003");
    private static final UUID REJECTED_POST_ID =
            UUID.fromString("0198fa50-0000-7000-8000-000000000004");
    private static final UUID DELETED_POST_ID =
            UUID.fromString("0198fa50-0000-7000-8000-000000000005");

    private static final LocalDate FIRST_TOPIC_DATE = LocalDate.of(2026, 8, 20);
    private static final LocalDate SECOND_TOPIC_DATE = LocalDate.of(2026, 8, 21);
    private static final LocalDate DELETED_TOPIC_DATE = LocalDate.of(2026, 8, 22);
    private static final Instant TOPIC_STARTS_AT = Instant.parse("2026-08-20T00:00:00Z");
    private static final Instant TOPIC_ENDS_AT = Instant.parse("2026-08-20T23:59:59Z");
    private static final Instant REJECTED_CREATED_AT = Instant.parse("2026-08-20T08:00:00Z");
    private static final Instant APPROVED_CREATED_AT = Instant.parse("2026-08-20T09:00:00Z");
    private static final Instant TIED_CREATED_AT = Instant.parse("2026-08-20T10:00:00Z");
    private static final Instant DELETED_CREATED_AT = Instant.parse("2026-08-20T11:00:00Z");
    private static final Instant MODERATED_AT = Instant.parse("2026-08-20T10:10:00Z");
    private static final Instant DELETED_AT = Instant.parse("2026-08-21T00:00:00Z");
    private static final Instant UPLOAD_EXPIRES_AT = Instant.parse("2026-08-20T11:00:00Z");
    private static final Instant UPLOAD_CLAIMED_AT = Instant.parse("2026-08-20T09:55:00Z");
    private static final Instant UPLOAD_CREATED_AT = Instant.parse("2026-08-20T09:50:00Z");
    private static final Instant UPLOAD_UPDATED_AT = Instant.parse("2026-08-20T10:00:05Z");
    private static final Instant PHOTO_CREATED_AT = Instant.parse("2026-08-20T09:50:30Z");
    private static final Instant PHOTO_UPDATED_AT = Instant.parse("2026-08-20T10:00:10Z");

    @Autowired
    private AdminPostQueryRepository adminPostQueryRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @BeforeEach
    void setUp() {
        insertAdmin();
        insertUsers();
        insertTopics();
        insertPhotos();
        insertUploads();
        insertPosts();
        insertModerationAuditLog();
        insertLikes();
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    @DisplayName("관리자 목록은 모든 검수 상태와 삭제된 게시물을 함께 조회한다")
    void findPosts_noFilters_returnsEveryModerationStatusAndSoftDeletedPost() {
        // Given
        AdminPostQueryCriteria criteria = criteria(AdminPostQuerySort.CREATED_AT_DESC);

        // When
        AdminPostQueryPage result = adminPostQueryRepository.findPosts(criteria, 1, 10);

        // Then
        assertThat(result.currentPage()).isEqualTo(1);
        assertThat(result.pageSize()).isEqualTo(10);
        assertThat(result.hasNext()).isFalse();
        assertThat(result.posts()).extracting(AdminPostSummaryProjection::postId)
                .containsExactly(
                        DELETED_POST_ID,
                        PENDING_POST_ID,
                        VALIDATING_POST_ID,
                        APPROVED_POST_ID,
                        REJECTED_POST_ID
                );
        assertThat(result.posts()).extracting(AdminPostSummaryProjection::moderationStatus)
                .contains(
                        ModerationStatus.VALIDATING,
                        ModerationStatus.PENDING,
                        ModerationStatus.APPROVED,
                        ModerationStatus.REJECTED
                );
        assertThat(summaryOf(result, DELETED_POST_ID).deletedAt()).isEqualTo(DELETED_AT);
        assertThat(summaryOf(result, PENDING_POST_ID).likeCount()).isEqualTo(2);
        assertThat(summaryOf(result, PENDING_POST_ID).authorEmail())
                .isEqualTo("pending@example.com");
        assertThat(summaryOf(result, PENDING_POST_ID).authorStatus())
                .isEqualTo(UserStatus.BANNED);
        assertThat(summaryOf(result, DELETED_POST_ID).authorDeletedAt())
                .isEqualTo(DELETED_AT);
        assertThat(summaryOf(result, PENDING_POST_ID).photoId())
                .isEqualTo(PENDING_PHOTO_ID);
    }

    @Test
    @DisplayName("상태·주제·날짜·사용자·등록 시각 필터를 함께 적용한다")
    void findPosts_combinedFilters_returnsOnlyMatchingPost() {
        // Given
        AdminPostQueryCriteria criteria = new AdminPostQueryCriteria(
                ModerationStatus.PENDING,
                FIRST_TOPIC_ID,
                FIRST_TOPIC_DATE,
                PENDING_USER_ID,
                TIED_CREATED_AT,
                TIED_CREATED_AT,
                AdminPostQuerySort.CREATED_AT_ASC
        );

        // When
        AdminPostQueryPage result = adminPostQueryRepository.findPosts(criteria, 1, 20);

        // Then
        assertThat(result.posts()).extracting(AdminPostSummaryProjection::postId)
                .containsExactly(PENDING_POST_ID);
        assertThat(result.hasNext()).isFalse();
    }

    @Test
    @DisplayName("각 목록 필터는 삭제 여부와 관계없이 해당 조건만 정확히 적용한다")
    void findPosts_eachFilter_returnsMatchingPosts() {
        // When
        AdminPostQueryPage statusResult = adminPostQueryRepository.findPosts(
                new AdminPostQueryCriteria(
                        ModerationStatus.APPROVED,
                        null,
                        null,
                        null,
                        null,
                        null,
                        AdminPostQuerySort.CREATED_AT_DESC
                ),
                1,
                20
        );
        AdminPostQueryPage topicIdResult = adminPostQueryRepository.findPosts(
                new AdminPostQueryCriteria(
                        null,
                        FIRST_TOPIC_ID,
                        null,
                        null,
                        null,
                        null,
                        AdminPostQuerySort.CREATED_AT_DESC
                ),
                1,
                20
        );
        AdminPostQueryPage topicDateResult = adminPostQueryRepository.findPosts(
                new AdminPostQueryCriteria(
                        null,
                        null,
                        SECOND_TOPIC_DATE,
                        null,
                        null,
                        null,
                        AdminPostQuerySort.CREATED_AT_DESC
                ),
                1,
                20
        );
        AdminPostQueryPage userIdResult = adminPostQueryRepository.findPosts(
                new AdminPostQueryCriteria(
                        null,
                        null,
                        null,
                        REJECTED_USER_ID,
                        null,
                        null,
                        AdminPostQuerySort.CREATED_AT_DESC
                ),
                1,
                20
        );
        AdminPostQueryPage createdAtFromResult = adminPostQueryRepository.findPosts(
                new AdminPostQueryCriteria(
                        null,
                        null,
                        null,
                        null,
                        DELETED_CREATED_AT,
                        null,
                        AdminPostQuerySort.CREATED_AT_DESC
                ),
                1,
                20
        );
        AdminPostQueryPage createdAtToResult = adminPostQueryRepository.findPosts(
                new AdminPostQueryCriteria(
                        null,
                        null,
                        null,
                        null,
                        null,
                        REJECTED_CREATED_AT,
                        AdminPostQuerySort.CREATED_AT_DESC
                ),
                1,
                20
        );

        // Then
        assertThat(statusResult.posts()).extracting(AdminPostSummaryProjection::postId)
                .containsExactly(DELETED_POST_ID, APPROVED_POST_ID);
        assertThat(topicIdResult.posts()).extracting(AdminPostSummaryProjection::postId)
                .containsExactly(PENDING_POST_ID, VALIDATING_POST_ID, REJECTED_POST_ID);
        assertThat(topicDateResult.posts()).extracting(AdminPostSummaryProjection::postId)
                .containsExactly(APPROVED_POST_ID);
        assertThat(userIdResult.posts()).extracting(AdminPostSummaryProjection::postId)
                .containsExactly(REJECTED_POST_ID);
        assertThat(createdAtFromResult.posts()).extracting(AdminPostSummaryProjection::postId)
                .containsExactly(DELETED_POST_ID);
        assertThat(createdAtToResult.posts()).extracting(AdminPostSummaryProjection::postId)
                .containsExactly(REJECTED_POST_ID);
    }

    @Test
    @DisplayName("등록 시각 오름차순과 내림차순은 ID 보조 정렬로 페이지 경계를 유지한다")
    void findPosts_createdAtSortAndPagination_returnsStablePages() {
        // Given
        AdminPostQueryCriteria descendingCriteria = criteria(
                AdminPostQuerySort.CREATED_AT_DESC
        );
        AdminPostQueryCriteria ascendingCriteria = criteria(
                AdminPostQuerySort.CREATED_AT_ASC
        );

        // When
        AdminPostQueryPage descendingFirst = adminPostQueryRepository.findPosts(
                descendingCriteria,
                1,
                3
        );
        AdminPostQueryPage descendingSecond = adminPostQueryRepository.findPosts(
                descendingCriteria,
                2,
                3
        );
        AdminPostQueryPage ascendingFirst = adminPostQueryRepository.findPosts(
                ascendingCriteria,
                1,
                3
        );
        AdminPostQueryPage ascendingSecond = adminPostQueryRepository.findPosts(
                ascendingCriteria,
                2,
                3
        );

        // Then
        assertThat(descendingFirst.posts()).extracting(AdminPostSummaryProjection::postId)
                .containsExactly(DELETED_POST_ID, PENDING_POST_ID, VALIDATING_POST_ID);
        assertThat(descendingSecond.posts()).extracting(AdminPostSummaryProjection::postId)
                .containsExactly(APPROVED_POST_ID, REJECTED_POST_ID);
        assertThat(descendingFirst.hasNext()).isTrue();
        assertThat(descendingSecond.hasNext()).isFalse();
        assertThat(descendingSecond.currentPage()).isEqualTo(2);

        assertThat(ascendingFirst.posts()).extracting(AdminPostSummaryProjection::postId)
                .containsExactly(REJECTED_POST_ID, APPROVED_POST_ID, VALIDATING_POST_ID);
        assertThat(ascendingSecond.posts()).extracting(AdminPostSummaryProjection::postId)
                .containsExactly(PENDING_POST_ID, DELETED_POST_ID);
        assertThat(ascendingFirst.hasNext()).isTrue();
        assertThat(ascendingSecond.hasNext()).isFalse();
        assertThat(ascendingSecond.currentPage()).isEqualTo(2);
    }

    @Test
    @DisplayName("상세 조회는 게시물 연관 정보와 사진·업로드 메타데이터 및 좋아요 수를 반환한다")
    void findPostById_existingPost_returnsSingleRichProjection() {
        // When
        AdminPostDetailProjection detail = adminPostQueryRepository.findPostById(
                PENDING_POST_ID
        ).orElseThrow();

        // Then
        assertThat(detail.postId()).isEqualTo(PENDING_POST_ID);
        assertThat(detail.title()).isEqualTo("검수 대기");
        assertThat(detail.moderationStatus()).isEqualTo(ModerationStatus.PENDING);
        assertThat(detail.moderatedAt()).isNull();
        assertThat(detail.createdAt()).isEqualTo(TIED_CREATED_AT);
        assertThat(detail.updatedAt()).isEqualTo(TIED_CREATED_AT.plusSeconds(30));
        assertThat(detail.deletedAt()).isNull();

        assertThat(detail.topicId()).isEqualTo(FIRST_TOPIC_ID);
        assertThat(detail.topicTitle()).isEqualTo("첫 번째 주제");
        assertThat(detail.topicDate()).isEqualTo(FIRST_TOPIC_DATE);
        assertThat(detail.topicStartsAt()).isEqualTo(TOPIC_STARTS_AT);
        assertThat(detail.topicEndsAt()).isEqualTo(TOPIC_ENDS_AT);
        assertThat(detail.topicDeletedAt()).isNull();

        assertThat(detail.authorId()).isEqualTo(PENDING_USER_ID);
        assertThat(detail.authorEmail()).isEqualTo("pending@example.com");
        assertThat(detail.authorStatus()).isEqualTo(UserStatus.BANNED);
        assertThat(detail.authorDeletedAt()).isNull();

        assertThat(detail.photoId()).isEqualTo(PENDING_PHOTO_ID);
        assertThat(detail.originalStorageKey())
                .isEqualTo("chalkak/posts/pending/original.webp");
        assertThat(detail.thumbnailStorageKey())
                .isEqualTo("chalkak/posts/pending/thumbnail.webp");
        assertThat(detail.photoMetadata())
                .containsEntry("width", 4032)
                .containsEntry("height", 3024);
        assertThat(detail.photoCreatedAt()).isEqualTo(PHOTO_CREATED_AT);
        assertThat(detail.photoUpdatedAt()).isEqualTo(PHOTO_UPDATED_AT);
        assertThat(detail.photoDeletedAt()).isNull();

        assertThat(detail.uploadId()).isEqualTo(PENDING_UPLOAD_ID);
        assertThat(detail.uploadStatus()).isEqualTo(PostImageUploadStatus.READY);
        assertThat(detail.uploadRejectionReason()).isNull();
        assertThat(detail.uploadCreatedAt()).isEqualTo(UPLOAD_CREATED_AT);
        assertThat(detail.uploadUpdatedAt()).isEqualTo(UPLOAD_UPDATED_AT);
        assertThat(detail.likeCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("상세 조회는 삭제된 연관 정보와 업로드 연결이 없는 기존 게시물도 반환한다")
    void findPostById_softDeletedLegacyPost_returnsDeletedStateWithNullUpload() {
        // When
        AdminPostDetailProjection detail = adminPostQueryRepository.findPostById(
                DELETED_POST_ID
        ).orElseThrow();

        // Then
        assertThat(detail.deletedAt()).isEqualTo(DELETED_AT);
        assertThat(detail.topicDeletedAt()).isEqualTo(DELETED_AT);
        assertThat(detail.authorDeletedAt()).isEqualTo(DELETED_AT);
        assertThat(detail.photoDeletedAt()).isEqualTo(DELETED_AT);
        assertThat(detail.uploadId()).isNull();
        assertThat(detail.uploadStatus()).isNull();
        assertThat(detail.uploadRejectionReason()).isNull();
        assertThat(detail.uploadCreatedAt()).isNull();
        assertThat(detail.uploadUpdatedAt()).isNull();
    }

    @Test
    @DisplayName("상세 조회는 관리자 검수 감사 로그에서 처리자와 사유를 함께 조회한다")
    void findPostById_moderatedPost_returnsModeratorAndReasonFromAuditLog() {
        // When
        AdminPostDetailProjection detail = adminPostQueryRepository.findPostById(
                APPROVED_POST_ID
        ).orElseThrow();

        // Then
        assertThat(detail.moderatedBy()).isEqualTo(ADMIN_ID);
        assertThat(detail.rejectionReason()).isNull();
        assertThat(detail.moderatedAt()).isEqualTo(MODERATED_AT);
    }

    @Test
    @DisplayName("존재하지 않는 게시물 상세 조회는 빈 결과를 반환한다")
    void findPostById_unknownPost_returnsEmpty() {
        // When & Then
        assertThat(adminPostQueryRepository.findPostById(UUID.randomUUID())).isEmpty();
    }

    private AdminPostQueryCriteria criteria(AdminPostQuerySort sort) {
        return new AdminPostQueryCriteria(null, null, null, null, null, null, sort);
    }

    private AdminPostSummaryProjection summaryOf(
            AdminPostQueryPage page,
            UUID postId
    ) {
        return page.posts().stream()
                .filter(post -> post.postId().equals(postId))
                .findFirst()
                .orElseThrow();
    }

    private void insertUsers() {
        insertUser(VALIDATING_USER_ID, "validating@example.com", "ACTIVE", null);
        insertUser(PENDING_USER_ID, "pending@example.com", "BANNED", null);
        insertUser(APPROVED_USER_ID, "approved@example.com", "ACTIVE", null);
        insertUser(REJECTED_USER_ID, "rejected@example.com", "ACTIVE", null);
        insertUser(
                DELETED_USER_ID,
                "withdrawn+0198fa10000070008000000000000005@chalkak.invalid",
                "ACTIVE",
                DELETED_AT
        );
    }

    private void insertAdmin() {
        jdbcTemplate.update("""
                INSERT INTO admins (
                    id, username, password, created_at, updated_at
                ) VALUES (
                    ?, 'admin-post-query', 'test-password',
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """, ADMIN_ID);
    }

    private void insertModerationAuditLog() {
        jdbcTemplate.update("""
                INSERT INTO admin_audit_logs (
                    actor_admin_id, action, target_type, target_id, reason,
                    before_state, after_state, occurred_at, request_id
                ) VALUES (
                    ?, CAST('POST_APPROVED' AS admin_action),
                    CAST('POST' AS admin_target_type), ?, NULL,
                    CAST('{"moderationStatus":"PENDING","moderatedAt":null}' AS jsonb),
                    CAST(? AS jsonb), ?, ?
                )
                """,
                ADMIN_ID,
                APPROVED_POST_ID,
                "{\"moderationStatus\":\"APPROVED\","
                        + "\"moderatedAt\":\"" + MODERATED_AT + "\","
                        + "\"moderatedBy\":\"" + ADMIN_ID + "\"}",
                timestamp(MODERATED_AT),
                UUID.fromString("0198fa60-0000-7000-8000-000000000001")
        );
    }

    private void insertUser(
            UUID userId,
            String email,
            String status,
            Instant deletedAt
    ) {
        jdbcTemplate.update("""
                INSERT INTO users (
                    id, email, status, signature_original_storage_key,
                    created_at, updated_at, deleted_at
                ) VALUES (
                    ?, ?, CAST(? AS user_status), ?,
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, ?
                )
                """,
                userId,
                email,
                status,
                "chalkak/signatures/" + userId + "/original.png",
                timestamp(deletedAt)
        );
    }

    private void insertTopics() {
        insertTopic(
                FIRST_TOPIC_ID,
                "첫 번째 주제",
                FIRST_TOPIC_DATE,
                TOPIC_STARTS_AT,
                TOPIC_ENDS_AT,
                null
        );
        insertTopic(
                SECOND_TOPIC_ID,
                "두 번째 주제",
                SECOND_TOPIC_DATE,
                TOPIC_STARTS_AT.plusSeconds(86_400),
                TOPIC_ENDS_AT.plusSeconds(86_400),
                null
        );
        insertTopic(
                DELETED_TOPIC_ID,
                "삭제된 주제",
                DELETED_TOPIC_DATE,
                TOPIC_STARTS_AT.plusSeconds(172_800),
                TOPIC_ENDS_AT.plusSeconds(172_800),
                DELETED_AT
        );
    }

    private void insertTopic(
            UUID topicId,
            String title,
            LocalDate topicDate,
            Instant startsAt,
            Instant endsAt,
            Instant deletedAt
    ) {
        jdbcTemplate.update("""
                INSERT INTO topics (
                    id, title, topic_date, starts_at, ends_at,
                    created_at, updated_at, deleted_at
                ) VALUES (
                    ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, ?
                )
                """,
                topicId,
                title,
                topicDate,
                timestamp(startsAt),
                timestamp(endsAt),
                timestamp(deletedAt)
        );
    }

    private void insertPhotos() {
        insertPhoto(
                VALIDATING_PHOTO_ID,
                "validating",
                "{}",
                PHOTO_CREATED_AT,
                PHOTO_CREATED_AT,
                null
        );
        insertPhoto(
                PENDING_PHOTO_ID,
                "pending",
                "{\"width\":4032,\"height\":3024}",
                PHOTO_CREATED_AT,
                PHOTO_UPDATED_AT,
                null
        );
        insertPhoto(
                APPROVED_PHOTO_ID,
                "approved",
                "{\"width\":1920,\"height\":1080}",
                PHOTO_CREATED_AT,
                PHOTO_UPDATED_AT,
                null
        );
        insertPhoto(
                REJECTED_PHOTO_ID,
                "rejected",
                "{}",
                PHOTO_CREATED_AT,
                PHOTO_CREATED_AT,
                null
        );
        insertPhoto(
                DELETED_PHOTO_ID,
                "deleted",
                "{\"width\":1280,\"height\":720}",
                PHOTO_CREATED_AT,
                PHOTO_UPDATED_AT,
                DELETED_AT
        );
    }

    private void insertPhoto(
            UUID photoId,
            String keySegment,
            String metadata,
            Instant createdAt,
            Instant updatedAt,
            Instant deletedAt
    ) {
        jdbcTemplate.update("""
                INSERT INTO photos (
                    id, original_storage_key, thumbnail_storage_key, metadata,
                    created_at, updated_at, deleted_at
                ) VALUES (
                    ?, ?, ?, CAST(? AS jsonb), ?, ?, ?
                )
                """,
                photoId,
                "chalkak/posts/" + keySegment + "/original.webp",
                keySegment.equals("validating") || keySegment.equals("rejected")
                        ? null
                        : "chalkak/posts/" + keySegment + "/thumbnail.webp",
                metadata,
                timestamp(createdAt),
                timestamp(updatedAt),
                timestamp(deletedAt)
        );
    }

    private void insertUploads() {
        insertUpload(
                VALIDATING_UPLOAD_ID,
                VALIDATING_USER_ID,
                "ISSUED",
                null,
                null
        );
        insertUpload(
                PENDING_UPLOAD_ID,
                PENDING_USER_ID,
                "READY",
                null,
                "{\"width\":4032,\"height\":3024}"
        );
        insertUpload(
                APPROVED_UPLOAD_ID,
                APPROVED_USER_ID,
                "READY",
                null,
                "{\"width\":1920,\"height\":1080}"
        );
        insertUpload(
                REJECTED_UPLOAD_ID,
                REJECTED_USER_ID,
                "REJECTED",
                "CORRUPTED_IMAGE",
                null
        );
    }

    private void insertUpload(
            UUID uploadId,
            UUID userId,
            String status,
            String rejectionReason,
            String imageMetadata
    ) {
        jdbcTemplate.update("""
                INSERT INTO post_image_uploads (
                    id, user_id, status, rejection_reason, image_metadata,
                    expires_at, claimed_at, created_at, updated_at
                ) VALUES (
                    ?, ?, CAST(? AS post_image_upload_status), ?, CAST(? AS jsonb),
                    ?, ?, ?, ?
                )
                """,
                uploadId,
                userId,
                status,
                rejectionReason,
                imageMetadata,
                timestamp(UPLOAD_EXPIRES_AT),
                timestamp(UPLOAD_CLAIMED_AT),
                timestamp(UPLOAD_CREATED_AT),
                timestamp(UPLOAD_UPDATED_AT)
        );
    }

    private void insertPosts() {
        insertPost(
                VALIDATING_POST_ID,
                VALIDATING_USER_ID,
                FIRST_TOPIC_ID,
                VALIDATING_PHOTO_ID,
                VALIDATING_UPLOAD_ID,
                "이미지 처리 중",
                "VALIDATING",
                null,
                TIED_CREATED_AT,
                null
        );
        insertPost(
                PENDING_POST_ID,
                PENDING_USER_ID,
                FIRST_TOPIC_ID,
                PENDING_PHOTO_ID,
                PENDING_UPLOAD_ID,
                "검수 대기",
                "PENDING",
                null,
                TIED_CREATED_AT,
                null
        );
        insertPost(
                APPROVED_POST_ID,
                APPROVED_USER_ID,
                SECOND_TOPIC_ID,
                APPROVED_PHOTO_ID,
                APPROVED_UPLOAD_ID,
                "승인됨",
                "APPROVED",
                MODERATED_AT,
                APPROVED_CREATED_AT,
                null
        );
        insertPost(
                REJECTED_POST_ID,
                REJECTED_USER_ID,
                FIRST_TOPIC_ID,
                REJECTED_PHOTO_ID,
                REJECTED_UPLOAD_ID,
                "거절됨",
                "REJECTED",
                null,
                REJECTED_CREATED_AT,
                null
        );
        insertPost(
                DELETED_POST_ID,
                DELETED_USER_ID,
                DELETED_TOPIC_ID,
                DELETED_PHOTO_ID,
                null,
                "삭제됨",
                "APPROVED",
                MODERATED_AT,
                DELETED_CREATED_AT,
                DELETED_AT
        );
    }

    private void insertPost(
            UUID postId,
            UUID userId,
            UUID topicId,
            UUID photoId,
            UUID uploadId,
            String title,
            String moderationStatus,
            Instant moderatedAt,
            Instant createdAt,
            Instant deletedAt
    ) {
        jdbcTemplate.update("""
                INSERT INTO posts (
                    id, user_id, topic_id, photo_id, post_image_upload_id,
                    title, moderation_status, moderated_at,
                    created_at, updated_at, deleted_at
                ) VALUES (
                    ?, ?, ?, ?, ?, ?, CAST(? AS moderation_status), ?, ?, ?, ?
                )
                """,
                postId,
                userId,
                topicId,
                photoId,
                uploadId,
                title,
                moderationStatus,
                timestamp(moderatedAt),
                timestamp(createdAt),
                timestamp(createdAt.plusSeconds(30)),
                timestamp(deletedAt)
        );
    }

    private Timestamp timestamp(Instant instant) {
        if (instant == null) {
            return null;
        }
        return Timestamp.from(instant);
    }

    private void insertLikes() {
        jdbcTemplate.update(
                "INSERT INTO post_likes (post_id, user_id) VALUES (?, ?)",
                PENDING_POST_ID,
                VALIDATING_USER_ID
        );
        jdbcTemplate.update(
                "INSERT INTO post_likes (post_id, user_id) VALUES (?, ?)",
                PENDING_POST_ID,
                APPROVED_USER_ID
        );
    }
}
