package com.chalkak.backend.admin.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.chalkak.backend.admin.repository.AdminUserDetailProjection;
import com.chalkak.backend.admin.repository.AdminUserQueryCriteria;
import com.chalkak.backend.admin.repository.AdminUserQueryPage;
import com.chalkak.backend.admin.repository.AdminUserQueryRepository;
import com.chalkak.backend.admin.repository.AdminUserQuerySort;
import com.chalkak.backend.admin.repository.AdminUserQueryStatus;
import com.chalkak.backend.admin.repository.AdminUserSummaryProjection;
import com.chalkak.backend.auth.domain.SocialProvider;
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
@Import(AdminUserQueryRepositoryImpl.class)
class AdminUserQueryRepositoryTest {

    private static final UUID ACTIVE_USER_ID =
            UUID.fromString("0198fb00-0000-7000-8000-000000000001");
    private static final UUID BANNED_USER_ID =
            UUID.fromString("0198fb00-0000-7000-8000-000000000002");
    private static final UUID WITHDRAWN_USER_ID =
            UUID.fromString("0198fb00-0000-7000-8000-000000000003");
    private static final Instant ACTIVE_CREATED_AT = Instant.parse("2026-08-20T01:00:00Z");
    private static final Instant BANNED_CREATED_AT = Instant.parse("2026-08-20T02:00:00Z");
    private static final Instant WITHDRAWN_CREATED_AT = Instant.parse("2026-08-20T03:00:00Z");
    private static final Instant WITHDRAWN_AT = Instant.parse("2026-08-21T03:00:00Z");

    @Autowired
    private AdminUserQueryRepository adminUserQueryRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @BeforeEach
    void setUp() {
        insertUsers();
        insertSocialAccounts();
        insertPostsForActiveUser();
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    @DisplayName("관리자 사용자 목록은 탈퇴자를 포함해 안정적인 최신순으로 조회한다")
    void findUsers_withoutFilters_returnsEveryDerivedStatusInStableOrder() {
        // Given
        AdminUserQueryCriteria criteria = new AdminUserQueryCriteria(
                null,
                null,
                AdminUserQuerySort.CREATED_AT_DESC);

        // When
        AdminUserQueryPage result = adminUserQueryRepository.findUsers(criteria, 1, 2);

        // Then
        assertThat(result.hasNext()).isTrue();
        assertThat(result.users()).extracting(AdminUserSummaryProjection::userId)
                .containsExactly(WITHDRAWN_USER_ID, BANNED_USER_ID);
        assertThat(result.users().getFirst().deletedAt()).isEqualTo(WITHDRAWN_AT);
        assertThat(result.users().get(1).socialProvider()).isEqualTo(SocialProvider.KAKAO);
    }

    @Test
    @DisplayName("파생 상태와 대소문자를 무시한 이메일 부분 검색을 함께 적용한다")
    void findUsers_statusAndEmailFilter_returnsMatchingUser() {
        // Given
        AdminUserQueryCriteria criteria = new AdminUserQueryCriteria(
                AdminUserQueryStatus.BANNED,
                "BANNED@EXAMPLE",
                AdminUserQuerySort.CREATED_AT_ASC);

        // When
        AdminUserQueryPage result = adminUserQueryRepository.findUsers(criteria, 1, 20);

        // Then
        assertThat(result.users()).singleElement().satisfies(user -> {
            assertThat(user.userId()).isEqualTo(BANNED_USER_ID);
            assertThat(user.email()).isEqualTo("banned@example.com");
        });
    }

    @Test
    @DisplayName("활성 사용자 상세에 소셜 제공자와 게시물 상태별 개수를 제공한다")
    void findUserById_activeUser_returnsProviderSignatureAndPostCounts() {
        // When
        AdminUserDetailProjection result = adminUserQueryRepository
                .findUserById(ACTIVE_USER_ID)
                .orElseThrow();

        // Then
        assertThat(result.socialProvider()).isEqualTo(SocialProvider.GOOGLE);
        assertThat(result.signatureOriginalStorageKey()).isEqualTo("signatures/active-original");
        assertThat(result.signatureThumbnailStorageKey()).isEqualTo("signatures/active-thumbnail");
        assertThat(result.validatingPostCount()).isEqualTo(1);
        assertThat(result.pendingPostCount()).isEqualTo(1);
        assertThat(result.approvedPostCount()).isEqualTo(1);
        assertThat(result.rejectedPostCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("탈퇴 사용자 상세도 비식별 정보로 조회한다")
    void findUserById_withdrawnUser_returnsAnonymizedData() {
        // When
        AdminUserDetailProjection result = adminUserQueryRepository
                .findUserById(WITHDRAWN_USER_ID)
                .orElseThrow();

        // Then
        assertThat(result.email()).isEqualTo(
                "withdrawn+" + WITHDRAWN_USER_ID + "@chalkak.invalid");
        assertThat(result.signatureOriginalStorageKey())
                .isEqualTo("withdrawn/" + WITHDRAWN_USER_ID);
        assertThat(result.signatureThumbnailStorageKey()).isNull();
        assertThat(result.deletedAt()).isEqualTo(WITHDRAWN_AT);
    }

    private void insertUsers() {
        jdbcTemplate.update("""
                INSERT INTO users (
                    id, email, status, signature_original_storage_key,
                    signature_thumbnail_storage_key, app_version,
                    created_at, updated_at, deleted_at
                ) VALUES (
                    ?, 'active@example.com', 'ACTIVE',
                    'signatures/active-original', 'signatures/active-thumbnail',
                    '1.2.3', ?, ?, NULL
                ), (
                    ?, 'banned@example.com', 'BANNED',
                    'signatures/banned-original', 'signatures/banned-thumbnail',
                    '1.2.2', ?, ?, NULL
                ), (
                    ?, ?, 'ACTIVE', ?, NULL, NULL, ?, ?, ?
                )
                """,
                ACTIVE_USER_ID,
                Timestamp.from(ACTIVE_CREATED_AT),
                Timestamp.from(ACTIVE_CREATED_AT),
                BANNED_USER_ID,
                Timestamp.from(BANNED_CREATED_AT),
                Timestamp.from(BANNED_CREATED_AT),
                WITHDRAWN_USER_ID,
                "withdrawn+" + WITHDRAWN_USER_ID + "@chalkak.invalid",
                "withdrawn/" + WITHDRAWN_USER_ID,
                Timestamp.from(WITHDRAWN_CREATED_AT),
                Timestamp.from(WITHDRAWN_AT),
                Timestamp.from(WITHDRAWN_AT));
    }

    private void insertSocialAccounts() {
        jdbcTemplate.update("""
                INSERT INTO social_accounts (user_id, provider, subject)
                VALUES (?, 'GOOGLE', 'active-subject'), (?, 'KAKAO', 'banned-subject')
                """, ACTIVE_USER_ID, BANNED_USER_ID);
    }

    private void insertPostsForActiveUser() {
        for (int index = 0; index < 4; index++) {
            UUID topicId = UUID.fromString(
                    "0198fb10-0000-7000-8000-00000000000" + (index + 1));
            UUID photoId = UUID.fromString(
                    "0198fb20-0000-7000-8000-00000000000" + (index + 1));
            UUID postId = UUID.fromString(
                    "0198fb30-0000-7000-8000-00000000000" + (index + 1));
            LocalDate topicDate = LocalDate.of(2026, 8, 10 + index);
            jdbcTemplate.update("""
                    INSERT INTO topics (
                        id, title, topic_date, starts_at, ends_at, created_at, updated_at
                    ) VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """,
                    topicId,
                    "주제 " + index,
                    topicDate,
                    Timestamp.from(Instant.parse("2026-08-10T00:00:00Z").plusSeconds(index * 86400L)),
                    Timestamp.from(Instant.parse("2026-08-11T00:00:00Z").plusSeconds(index * 86400L)));
            jdbcTemplate.update("""
                    INSERT INTO photos (
                        id, original_storage_key, thumbnail_storage_key,
                        created_at, updated_at
                    ) VALUES (?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """,
                    photoId,
                    "posts/original/" + index,
                    "posts/thumbnail/" + index);
            jdbcTemplate.update("""
                    INSERT INTO posts (
                        id, user_id, topic_id, photo_id, title,
                        moderation_status, created_at, updated_at
                    ) VALUES (?, ?, ?, ?, ?, CAST(? AS moderation_status),
                              CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """,
                    postId,
                    ACTIVE_USER_ID,
                    topicId,
                    photoId,
                    "게시물 " + index,
                    new String[]{"VALIDATING", "PENDING", "APPROVED", "REJECTED"}[index]);
        }
    }
}
