package com.chalkak.backend.admin.infrastructure.persistence.post;

import static com.chalkak.backend.like.support.PostLikeCountFixtures.synchronizeFixtureCounts;
import static org.assertj.core.api.Assertions.assertThat;

import com.chalkak.backend.admin.repository.AdminPage;
import com.chalkak.backend.admin.repository.post.AdminPostDetailProjection;
import com.chalkak.backend.admin.repository.post.AdminPostInstagramConsentFilter;
import com.chalkak.backend.admin.repository.post.AdminPostQueryCriteria;
import com.chalkak.backend.admin.repository.post.AdminPostQueryRepository;
import com.chalkak.backend.admin.repository.post.AdminPostSort;
import com.chalkak.backend.admin.repository.post.AdminPostSummaryProjection;
import com.chalkak.backend.post.domain.ModerationStatus;
import com.chalkak.backend.post.domain.PostImageUploadStatus;
import com.chalkak.backend.support.PostgresRepositoryTest;
import com.chalkak.backend.user.domain.UserStatus;
import jakarta.persistence.EntityManager;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@PostgresRepositoryTest
class AdminPostQueryRepositoryTest {

    private static final UUID ADMIN_ID = UUID.fromString("0198fa00-0000-7000-8000-000000000001");

    private static final UUID VALIDATING_USER_ID = UUID
            .fromString("0198fa10-0000-7000-8000-000000000001");
    private static final UUID PENDING_USER_ID = UUID
            .fromString("0198fa10-0000-7000-8000-000000000002");
    private static final UUID APPROVED_USER_ID = UUID
            .fromString("0198fa10-0000-7000-8000-000000000003");
    private static final UUID REJECTED_USER_ID = UUID
            .fromString("0198fa10-0000-7000-8000-000000000004");
    private static final UUID DELETED_USER_ID = UUID
            .fromString("0198fa10-0000-7000-8000-000000000005");

    private static final UUID FIRST_TOPIC_ID = UUID
            .fromString("0198fa20-0000-7000-8000-000000000001");
    private static final UUID SECOND_TOPIC_ID = UUID
            .fromString("0198fa20-0000-7000-8000-000000000002");
    private static final UUID DELETED_TOPIC_ID = UUID
            .fromString("0198fa20-0000-7000-8000-000000000003");

    private static final UUID VALIDATING_PHOTO_ID = UUID
            .fromString("0198fa30-0000-7000-8000-000000000001");
    private static final UUID PENDING_PHOTO_ID = UUID
            .fromString("0198fa30-0000-7000-8000-000000000002");
    private static final UUID APPROVED_PHOTO_ID = UUID
            .fromString("0198fa30-0000-7000-8000-000000000003");
    private static final UUID REJECTED_PHOTO_ID = UUID
            .fromString("0198fa30-0000-7000-8000-000000000004");
    private static final UUID DELETED_PHOTO_ID = UUID
            .fromString("0198fa30-0000-7000-8000-000000000005");

    private static final UUID VALIDATING_UPLOAD_ID = UUID
            .fromString("0198fa40-0000-7000-8000-000000000001");
    private static final UUID PENDING_UPLOAD_ID = UUID
            .fromString("0198fa40-0000-7000-8000-000000000002");
    private static final UUID APPROVED_UPLOAD_ID = UUID
            .fromString("0198fa40-0000-7000-8000-000000000003");
    private static final UUID REJECTED_UPLOAD_ID = UUID
            .fromString("0198fa40-0000-7000-8000-000000000004");

    private static final UUID VALIDATING_POST_ID = UUID
            .fromString("0198fa50-0000-7000-8000-000000000001");
    private static final UUID PENDING_POST_ID = UUID
            .fromString("0198fa50-0000-7000-8000-000000000002");
    private static final UUID APPROVED_POST_ID = UUID
            .fromString("0198fa50-0000-7000-8000-000000000003");
    private static final UUID REJECTED_POST_ID = UUID
            .fromString("0198fa50-0000-7000-8000-000000000004");
    private static final UUID DELETED_POST_ID = UUID
            .fromString("0198fa50-0000-7000-8000-000000000005");
    private static final UUID TIE_POST_ID = UUID
            .fromString("0198fa50-0000-7000-8000-000000000000");
    private static final UUID TIE_USER_ID = UUID
            .fromString("0198fa10-0000-7000-8000-000000000006");
    private static final UUID TIE_PHOTO_ID = UUID
            .fromString("0198fa30-0000-7000-8000-000000000006");

    private static final LocalDate FIRST_TOPIC_DATE = LocalDate.of(
            2026,
            8,
            20
    );
    private static final LocalDate SECOND_TOPIC_DATE = LocalDate.of(
            2026,
            8,
            21
    );
    private static final LocalDate DELETED_TOPIC_DATE = LocalDate.of(
            2026,
            8,
            22
    );
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

    private static final Instant INSTAGRAM_AGREED_AT = Instant.parse("2026-08-20T10:20:00Z");
    private static final Instant INSTAGRAM_REVOKED_AT = Instant.parse("2026-08-20T10:30:00Z");

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
        insertInstagramConsents();
        insertModerationAuditLog();
        insertLikes();
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    @DisplayName("관리자 목록은 내부 검증 중 게시물을 제외하고 삭제된 게시물을 함께 조회한다")
    void findPosts_noFilters_excludesValidatingAndIncludesSoftDeletedPost() {
        // Given
        AdminPostQueryCriteria criteria = criteria(AdminPostSort.CREATED_AT_DESC);

        // When
        AdminPage<AdminPostSummaryProjection> result = adminPostQueryRepository.findPosts(
                criteria,
                1,
                10
        );

        // Then
        assertThat(result.currentPage()).isEqualTo(1);
        assertThat(result.pageSize()).isEqualTo(10);
        assertThat(result.hasNext()).isFalse();
        assertThat(result.items()).extracting(AdminPostSummaryProjection::postId)
                .containsExactly(
                        DELETED_POST_ID,
                        PENDING_POST_ID,
                        APPROVED_POST_ID,
                        REJECTED_POST_ID
                );
        assertThat(result.items()).extracting(AdminPostSummaryProjection::moderationStatus)
                .containsExactly(
                        ModerationStatus.APPROVED,
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
    @DisplayName("상태·주제·사용자 필터를 함께 적용한다")
    void findPosts_combinedFilters_returnsOnlyMatchingPost() {
        // Given
        AdminPostQueryCriteria criteria = new AdminPostQueryCriteria(
                ModerationStatus.PENDING,
                FIRST_TOPIC_ID,
                PENDING_USER_ID,
                AdminPostSort.CREATED_AT_ASC,
                null,
                null
        );

        // When
        AdminPage<AdminPostSummaryProjection> result = adminPostQueryRepository.findPosts(
                criteria,
                1,
                20
        );

        // Then
        assertThat(result.items()).extracting(AdminPostSummaryProjection::postId)
                .containsExactly(PENDING_POST_ID);
        assertThat(result.hasNext()).isFalse();
    }

    @Test
    @DisplayName("각 목록 필터는 삭제 여부와 관계없이 해당 조건만 정확히 적용한다")
    void findPosts_eachFilter_returnsMatchingPosts() {
        // When
        AdminPage<AdminPostSummaryProjection> statusResult = adminPostQueryRepository.findPosts(
                new AdminPostQueryCriteria(
                        ModerationStatus.APPROVED,
                        null,
                        null,
                        AdminPostSort.CREATED_AT_DESC,
                        null,
                        null
                ),
                1,
                20
        );
        AdminPage<AdminPostSummaryProjection> topicIdResult = adminPostQueryRepository.findPosts(
                new AdminPostQueryCriteria(
                        null,
                        FIRST_TOPIC_ID,
                        null,
                        AdminPostSort.CREATED_AT_DESC,
                        null,
                        null
                ),
                1,
                20
        );
        AdminPage<AdminPostSummaryProjection> userIdResult = adminPostQueryRepository.findPosts(
                new AdminPostQueryCriteria(
                        null,
                        null,
                        REJECTED_USER_ID,
                        AdminPostSort.CREATED_AT_DESC,
                        null,
                        null
                ),
                1,
                20
        );

        // Then
        assertThat(statusResult.items()).extracting(AdminPostSummaryProjection::postId)
                .containsExactly(DELETED_POST_ID, APPROVED_POST_ID);
        assertThat(topicIdResult.items()).extracting(AdminPostSummaryProjection::postId)
                .containsExactly(PENDING_POST_ID, REJECTED_POST_ID);
        assertThat(userIdResult.items()).extracting(AdminPostSummaryProjection::postId)
                .containsExactly(REJECTED_POST_ID);
    }

    @Test
    @DisplayName("등록 시각 오름차순과 내림차순은 ID 보조 정렬로 페이지 경계를 유지한다")
    void findPosts_createdAtSortAndPagination_returnsStablePages() {
        // Given
        AdminPostQueryCriteria descendingCriteria = criteria(
                AdminPostSort.CREATED_AT_DESC);
        AdminPostQueryCriteria ascendingCriteria = criteria(
                AdminPostSort.CREATED_AT_ASC);

        // When
        AdminPage<AdminPostSummaryProjection> descendingFirst = adminPostQueryRepository.findPosts(
                descendingCriteria,
                1,
                3
        );
        AdminPage<AdminPostSummaryProjection> descendingSecond = adminPostQueryRepository.findPosts(
                descendingCriteria,
                2,
                3
        );
        AdminPage<AdminPostSummaryProjection> ascendingFirst = adminPostQueryRepository.findPosts(
                ascendingCriteria,
                1,
                3
        );
        AdminPage<AdminPostSummaryProjection> ascendingSecond = adminPostQueryRepository.findPosts(
                ascendingCriteria,
                2,
                3
        );

        // Then
        assertThat(descendingFirst.items()).extracting(AdminPostSummaryProjection::postId)
                .containsExactly(
                        DELETED_POST_ID,
                        PENDING_POST_ID,
                        APPROVED_POST_ID
                );
        assertThat(descendingSecond.items()).extracting(AdminPostSummaryProjection::postId)
                .containsExactly(REJECTED_POST_ID);
        assertThat(descendingFirst.hasNext()).isTrue();
        assertThat(descendingSecond.hasNext()).isFalse();
        assertThat(descendingSecond.currentPage()).isEqualTo(2);

        assertThat(ascendingFirst.items()).extracting(AdminPostSummaryProjection::postId)
                .containsExactly(
                        REJECTED_POST_ID,
                        APPROVED_POST_ID,
                        PENDING_POST_ID
                );
        assertThat(ascendingSecond.items()).extracting(AdminPostSummaryProjection::postId)
                .containsExactly(DELETED_POST_ID);
        assertThat(ascendingFirst.hasNext()).isTrue();
        assertThat(ascendingSecond.hasNext()).isFalse();
        assertThat(ascendingSecond.currentPage()).isEqualTo(2);
    }

    @Test
    @DisplayName("주제별 인기순은 저장된 좋아요 수가 많은 순으로 정렬하고 좋아요가 없는 게시물도 포함한다")
    void findPosts_likeCountDescWithTopic_ordersByStoredLikeCountAndPaginates() {
        // Given
        insertLike(REJECTED_POST_ID, VALIDATING_USER_ID);
        insertLike(REJECTED_POST_ID, APPROVED_USER_ID);
        insertLike(REJECTED_POST_ID, PENDING_USER_ID);
        entityManager.flush();
        entityManager.clear();
        AdminPostQueryCriteria firstTopic = popularCriteria(FIRST_TOPIC_ID);
        AdminPostQueryCriteria secondTopic = popularCriteria(SECOND_TOPIC_ID);

        // When
        AdminPage<AdminPostSummaryProjection> firstPage = adminPostQueryRepository.findPosts(
                firstTopic,
                1,
                1
        );
        AdminPage<AdminPostSummaryProjection> secondPage = adminPostQueryRepository.findPosts(
                firstTopic,
                2,
                1
        );
        AdminPage<AdminPostSummaryProjection> zeroLikeTopic = adminPostQueryRepository.findPosts(
                secondTopic,
                1,
                20
        );

        // Then
        assertThat(firstPage.items()).extracting(AdminPostSummaryProjection::postId)
                .containsExactly(REJECTED_POST_ID);
        assertThat(firstPage.items().getFirst().likeCount()).isEqualTo(3);
        assertThat(firstPage.hasNext()).isTrue();
        assertThat(secondPage.items()).extracting(AdminPostSummaryProjection::postId)
                .containsExactly(PENDING_POST_ID);
        assertThat(secondPage.hasNext()).isFalse();
        assertThat(zeroLikeTopic.items()).extracting(AdminPostSummaryProjection::postId)
                .containsExactly(APPROVED_POST_ID);
        assertThat(zeroLikeTopic.items().getFirst().likeCount()).isZero();
    }

    @Test
    @DisplayName("주제별 인기순에서 좋아요 수가 같으면 등록 시각 내림차순, ID 오름차순으로 정렬한다")
    void findPosts_likeCountDescTie_ordersByCreatedAtDescThenIdAsc() {
        // Given
        insertUser(
                TIE_USER_ID,
                "tie@example.com",
                "ACTIVE",
                null
        );
        insertPhoto(
                TIE_PHOTO_ID,
                "tie",
                "{}",
                PHOTO_CREATED_AT,
                PHOTO_CREATED_AT,
                null
        );
        insertPost(
                TIE_POST_ID,
                TIE_USER_ID,
                FIRST_TOPIC_ID,
                TIE_PHOTO_ID,
                null,
                "동률",
                "APPROVED",
                MODERATED_AT,
                TIED_CREATED_AT,
                null
        );
        insertLike(TIE_POST_ID, VALIDATING_USER_ID);
        insertLike(TIE_POST_ID, APPROVED_USER_ID);
        insertLike(REJECTED_POST_ID, VALIDATING_USER_ID);
        insertLike(REJECTED_POST_ID, APPROVED_USER_ID);
        entityManager.flush();
        entityManager.clear();

        // When
        AdminPage<AdminPostSummaryProjection> result = adminPostQueryRepository.findPosts(
                popularCriteria(FIRST_TOPIC_ID),
                1,
                10
        );

        // Then
        assertThat(result.items()).extracting(AdminPostSummaryProjection::postId)
                .containsExactly(
                        TIE_POST_ID,
                        PENDING_POST_ID,
                        REJECTED_POST_ID
                );
    }

    @Test
    @DisplayName("상세 조회는 게시물 연관 정보와 사진·업로드 메타데이터 및 좋아요 수를 반환한다")
    void findPostById_existingPost_returnsSingleRichProjection() {
        // When
        AdminPostDetailProjection detail = adminPostQueryRepository.findPostById(
                PENDING_POST_ID).orElseThrow();

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
    @DisplayName("상세 조회는 저장된 게시물 설명을 줄바꿈 그대로 반환한다")
    void findPostById_postWithDescription_returnsDescription() {
        // Given
        jdbcTemplate.update(
                "UPDATE posts SET description = ? WHERE id = ?",
                "첫 줄\n둘째 줄",
                APPROVED_POST_ID
        );

        // When
        AdminPostDetailProjection detail = adminPostQueryRepository.findPostById(
                APPROVED_POST_ID).orElseThrow();

        // Then
        assertThat(detail.description()).isEqualTo("첫 줄\n둘째 줄");
    }

    @Test
    @DisplayName("상세 조회는 설명이 없는 게시물의 설명을 null로 반환한다")
    void findPostById_postWithoutDescription_returnsNullDescription() {
        // When
        AdminPostDetailProjection detail = adminPostQueryRepository.findPostById(
                PENDING_POST_ID).orElseThrow();

        // Then
        assertThat(detail.description()).isNull();
    }

    @Test
    @DisplayName("내부 검증 중 게시물은 관리자 상세에서 조회하지 않는다")
    void findPostById_validatingPost_returnsEmpty() {
        // When
        Optional<AdminPostDetailProjection> result = adminPostQueryRepository
                .findPostById(VALIDATING_POST_ID);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("상세 조회는 삭제된 연관 정보와 업로드 연결이 없는 기존 게시물도 반환한다")
    void findPostById_softDeletedLegacyPost_returnsDeletedStateWithNullUpload() {
        // When
        AdminPostDetailProjection detail = adminPostQueryRepository.findPostById(
                DELETED_POST_ID).orElseThrow();

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
                APPROVED_POST_ID).orElseThrow();

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

    @Test
    @DisplayName("동의함 필터는 철회 후 다시 동의한 게시물을 포함하고 내부 검증 중 게시물은 제외한다")
    void findPosts_instagramConsentAgreed_returnsAgreedPostsIncludingReAgreed() {
        // When
        AdminPage<AdminPostSummaryProjection> result = adminPostQueryRepository.findPosts(
                consentCriteria(AdminPostInstagramConsentFilter.AGREED, null),
                1,
                20
        );

        // Then
        assertThat(result.items()).extracting(AdminPostSummaryProjection::postId)
                .containsExactly(PENDING_POST_ID, APPROVED_POST_ID);
    }

    @Test
    @DisplayName("동의 안 함 필터는 철회한 적 없는 미동의 게시물만 조회한다")
    void findPosts_instagramConsentNotAgreed_returnsNeverRevokedPosts() {
        // When
        AdminPage<AdminPostSummaryProjection> result = adminPostQueryRepository.findPosts(
                consentCriteria(AdminPostInstagramConsentFilter.NOT_AGREED, null),
                1,
                20
        );

        // Then
        assertThat(result.items()).extracting(AdminPostSummaryProjection::postId)
                .containsExactly(REJECTED_POST_ID);
    }

    @Test
    @DisplayName("철회함 필터는 철회 시각이 있는 미동의 게시물을 삭제된 게시물까지 조회한다")
    void findPosts_instagramConsentRevoked_returnsRevokedPostsIncludingDeleted() {
        // When
        AdminPage<AdminPostSummaryProjection> result = adminPostQueryRepository.findPosts(
                consentCriteria(AdminPostInstagramConsentFilter.REVOKED, null),
                1,
                20
        );

        // Then
        assertThat(result.items()).extracting(AdminPostSummaryProjection::postId)
                .containsExactly(DELETED_POST_ID);
    }

    @Test
    @DisplayName("인스타그램 ID 검색은 포함 검색이며 내부 검증 중 게시물은 제외한다")
    void findPosts_instagramId_matchesContainingKeyword() {
        // When
        AdminPage<AdminPostSummaryProjection> result = adminPostQueryRepository.findPosts(
                consentCriteria(null, "user"),
                1,
                20
        );

        // Then
        assertThat(result.items()).extracting(AdminPostSummaryProjection::postId)
                .containsExactly(PENDING_POST_ID, APPROVED_POST_ID);
    }

    @Test
    @DisplayName("인스타그램 ID 검색어의 밑줄은 와일드카드가 아니라 문자 그대로 비교한다")
    void findPosts_instagramIdWithUnderscore_matchesLiterally() {
        // When
        AdminPage<AdminPostSummaryProjection> result = adminPostQueryRepository.findPosts(
                consentCriteria(null, "k_u"),
                1,
                20
        );

        // Then
        assertThat(result.items()).extracting(AdminPostSummaryProjection::postId)
                .containsExactly(PENDING_POST_ID);
    }

    @Test
    @DisplayName("인스타그램 ID 검색어의 퍼센트와 역슬래시는 와일드카드나 이스케이프로 해석하지 않는다")
    void findPosts_instagramIdWithPercentOrBackslash_matchesNothing() {
        // When
        AdminPage<AdminPostSummaryProjection> percent = adminPostQueryRepository.findPosts(
                consentCriteria(null, "%"),
                1,
                20
        );
        AdminPage<AdminPostSummaryProjection> backslash = adminPostQueryRepository.findPosts(
                consentCriteria(null, "\\"),
                1,
                20
        );

        // Then
        assertThat(percent.items()).isEmpty();
        assertThat(backslash.items()).isEmpty();
    }

    @Test
    @DisplayName("동의 상태와 ID 필터는 상태·주제·작성자 필터와 함께 적용한다")
    void findPosts_instagramFiltersCombinedWithExistingFilters_returnsIntersection() {
        // When
        AdminPage<AdminPostSummaryProjection> consentAndStatus = adminPostQueryRepository
                .findPosts(
                        new AdminPostQueryCriteria(
                                ModerationStatus.APPROVED,
                                null,
                                null,
                                AdminPostSort.CREATED_AT_DESC,
                                AdminPostInstagramConsentFilter.AGREED,
                                null
                        ),
                        1,
                        20
                );
        AdminPage<AdminPostSummaryProjection> consentAndTopic = adminPostQueryRepository
                .findPosts(
                        new AdminPostQueryCriteria(
                                null,
                                FIRST_TOPIC_ID,
                                null,
                                AdminPostSort.CREATED_AT_DESC,
                                AdminPostInstagramConsentFilter.AGREED,
                                null
                        ),
                        1,
                        20
                );
        AdminPage<AdminPostSummaryProjection> idAndUser = adminPostQueryRepository
                .findPosts(
                        new AdminPostQueryCriteria(
                                null,
                                null,
                                APPROVED_USER_ID,
                                AdminPostSort.CREATED_AT_DESC,
                                null,
                                "user"
                        ),
                        1,
                        20
                );
        AdminPage<AdminPostSummaryProjection> consentAndIdMismatch = adminPostQueryRepository
                .findPosts(
                        consentCriteria(AdminPostInstagramConsentFilter.REVOKED, "user"),
                        1,
                        20
                );

        // Then
        assertThat(consentAndStatus.items()).extracting(AdminPostSummaryProjection::postId)
                .containsExactly(APPROVED_POST_ID);
        assertThat(consentAndTopic.items()).extracting(AdminPostSummaryProjection::postId)
                .containsExactly(PENDING_POST_ID);
        assertThat(idAndUser.items()).extracting(AdminPostSummaryProjection::postId)
                .containsExactly(APPROVED_POST_ID);
        assertThat(consentAndIdMismatch.items()).isEmpty();
    }

    @Test
    @DisplayName("목록 항목은 인스타그램 게시 동의 여부와 ID를 반환한다")
    void findPosts_noFilters_returnsInstagramConsentFields() {
        // When
        AdminPage<AdminPostSummaryProjection> result = adminPostQueryRepository.findPosts(
                criteria(AdminPostSort.CREATED_AT_DESC),
                1,
                20
        );

        // Then
        assertThat(summaryOf(result, PENDING_POST_ID).instagramUploadAgreed()).isTrue();
        assertThat(summaryOf(result, PENDING_POST_ID).instagramId()).isEqualTo("chalkak_user");
        assertThat(summaryOf(result, REJECTED_POST_ID).instagramUploadAgreed()).isFalse();
        assertThat(summaryOf(result, REJECTED_POST_ID).instagramId()).isNull();
        assertThat(summaryOf(result, DELETED_POST_ID).instagramUploadAgreed()).isFalse();
        assertThat(summaryOf(result, DELETED_POST_ID).instagramId()).isNull();
    }

    @Test
    @DisplayName("상세 조회는 철회 후 다시 동의한 게시물의 동의·철회 시각을 함께 반환한다")
    void findPostById_reAgreedPost_returnsInstagramConsent() {
        // When
        AdminPostDetailProjection detail = adminPostQueryRepository.findPostById(
                APPROVED_POST_ID).orElseThrow();

        // Then
        assertThat(detail.instagramUploadAgreed()).isTrue();
        assertThat(detail.instagramId()).isEqualTo("chalkakxuser.re");
        assertThat(detail.instagramAgreedAt()).isEqualTo(INSTAGRAM_AGREED_AT.plusSeconds(60));
        assertThat(detail.instagramRevokedAt()).isEqualTo(INSTAGRAM_REVOKED_AT);
    }

    @Test
    @DisplayName("상세 조회는 삭제된 철회 게시물의 ID 없는 동의 이력을 반환한다")
    void findPostById_deletedRevokedPost_returnsConsentHistoryWithoutId() {
        // When
        AdminPostDetailProjection detail = adminPostQueryRepository.findPostById(
                DELETED_POST_ID).orElseThrow();

        // Then
        assertThat(detail.instagramUploadAgreed()).isFalse();
        assertThat(detail.instagramId()).isNull();
        assertThat(detail.instagramAgreedAt()).isEqualTo(INSTAGRAM_AGREED_AT);
        assertThat(detail.instagramRevokedAt()).isEqualTo(INSTAGRAM_REVOKED_AT);
    }

    @Test
    @DisplayName("상세 조회는 한 번도 동의하지 않은 게시물의 동의 이력을 비워서 반환한다")
    void findPostById_neverAgreedPost_returnsEmptyConsentHistory() {
        // When
        AdminPostDetailProjection detail = adminPostQueryRepository.findPostById(
                REJECTED_POST_ID).orElseThrow();

        // Then
        assertThat(detail.instagramUploadAgreed()).isFalse();
        assertThat(detail.instagramId()).isNull();
        assertThat(detail.instagramAgreedAt()).isNull();
        assertThat(detail.instagramRevokedAt()).isNull();
    }

    private AdminPostQueryCriteria consentCriteria(
            AdminPostInstagramConsentFilter instagramConsent,
            String instagramId
    ) {
        return new AdminPostQueryCriteria(
                null,
                null,
                null,
                AdminPostSort.CREATED_AT_DESC,
                instagramConsent,
                instagramId
        );
    }

    private AdminPostQueryCriteria criteria(AdminPostSort sort) {
        return new AdminPostQueryCriteria(
                null,
                null,
                null,
                sort,
                null,
                null
        );
    }

    private AdminPostQueryCriteria popularCriteria(UUID topicId) {
        return new AdminPostQueryCriteria(
                null,
                topicId,
                null,
                AdminPostSort.LIKE_COUNT_DESC,
                null,
                null
        );
    }

    private void insertLike(UUID postId, UUID userId) {
        jdbcTemplate.update(
                "INSERT INTO post_likes (post_id, user_id) VALUES (?, ?)",
                postId,
                userId
        );
        synchronizeFixtureCounts(jdbcTemplate);
    }

    private AdminPostSummaryProjection summaryOf(
            AdminPage<AdminPostSummaryProjection> page,
            UUID postId
    ) {
        return page.items().stream()
                .filter(post -> post.postId().equals(postId))
                .findFirst()
                .orElseThrow();
    }

    private void insertUsers() {
        insertUser(
                VALIDATING_USER_ID,
                "validating@example.com",
                "ACTIVE",
                null
        );
        insertUser(
                PENDING_USER_ID,
                "pending@example.com",
                "BANNED",
                null
        );
        insertUser(
                APPROVED_USER_ID,
                "approved@example.com",
                "ACTIVE",
                null
        );
        insertUser(
                REJECTED_USER_ID,
                "rejected@example.com",
                "ACTIVE",
                null
        );
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
        jdbcTemplate.update(
                """
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
        jdbcTemplate.update(
                """
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
        jdbcTemplate.update(
                """
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
        jdbcTemplate.update(
                """
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
        jdbcTemplate.update(
                """
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
        jdbcTemplate.update(
                """
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
        synchronizeFixtureCounts(jdbcTemplate);
    }

    private void insertInstagramConsents() {
        updateInstagramConsent(
                VALIDATING_POST_ID,
                true,
                "validating_id",
                INSTAGRAM_AGREED_AT,
                null
        );
        updateInstagramConsent(
                PENDING_POST_ID,
                true,
                "chalkak_user",
                INSTAGRAM_AGREED_AT,
                null
        );
        updateInstagramConsent(
                APPROVED_POST_ID,
                true,
                "chalkakxuser.re",
                INSTAGRAM_AGREED_AT.plusSeconds(60),
                INSTAGRAM_REVOKED_AT
        );
        updateInstagramConsent(
                DELETED_POST_ID,
                false,
                null,
                INSTAGRAM_AGREED_AT,
                INSTAGRAM_REVOKED_AT
        );
    }

    private void updateInstagramConsent(
            UUID postId,
            boolean agreed,
            String instagramId,
            Instant agreedAt,
            Instant revokedAt
    ) {
        jdbcTemplate.update(
                """
                        UPDATE posts
                        SET instagram_upload_agreed = ?,
                            instagram_id = ?,
                            instagram_agreed_at = ?,
                            instagram_revoked_at = ?
                        WHERE id = ?
                        """,
                agreed,
                instagramId,
                timestamp(agreedAt),
                timestamp(revokedAt),
                postId
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
        synchronizeFixtureCounts(jdbcTemplate);
        jdbcTemplate.update(
                "INSERT INTO post_likes (post_id, user_id) VALUES (?, ?)",
                PENDING_POST_ID,
                APPROVED_USER_ID
        );
        synchronizeFixtureCounts(jdbcTemplate);
    }
}
