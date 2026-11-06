package com.chalkak.backend.admin.infrastructure.persistence.feedback;

import static org.assertj.core.api.Assertions.assertThat;

import com.chalkak.backend.admin.repository.feedback.AdminFeedbackQueryCriteria;
import com.chalkak.backend.admin.repository.feedback.AdminFeedbackQueryCriteria.InquiryStatus;
import com.chalkak.backend.admin.repository.AdminPage;
import com.chalkak.backend.admin.repository.feedback.AdminFeedbackQueryRepository;
import com.chalkak.backend.admin.repository.feedback.AdminFeedbackSort;
import com.chalkak.backend.admin.repository.feedback.AdminFeedbackDetailProjection;
import com.chalkak.backend.admin.repository.feedback.AdminFeedbackProjection;
import com.chalkak.backend.support.PostgresRepositoryTest;
import com.chalkak.backend.user.domain.UserStatus;
import jakarta.persistence.EntityManager;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ConnectionCallback;
import org.postgresql.PGConnection;

@PostgresRepositoryTest
class AdminFeedbackQueryRepositoryTest {

    private static final AdminFeedbackQueryCriteria ALL_FEEDBACKS = new AdminFeedbackQueryCriteria(
            null,
            InquiryStatus.ALL,
            false
    );
    private static final UUID ACTIVE_USER_ID = UUID
            .fromString("0198fc00-0000-7000-8000-000000000001");
    private static final UUID BANNED_USER_ID = UUID
            .fromString("0198fc00-0000-7000-8000-000000000002");
    private static final UUID WITHDRAWN_USER_ID = UUID
            .fromString("0198fc00-0000-7000-8000-000000000003");
    private static final UUID OLDEST_FEEDBACK_ID = UUID
            .fromString("0198fc10-0000-7000-8000-000000000001");
    private static final UUID MIDDLE_FEEDBACK_ID = UUID
            .fromString("0198fc10-0000-7000-8000-000000000002");
    private static final UUID NEWEST_FEEDBACK_ID = UUID
            .fromString("0198fc10-0000-7000-8000-000000000003");
    private static final Instant OLDEST_CREATED_AT = Instant.parse("2026-09-14T01:00:00Z");
    private static final Instant MIDDLE_CREATED_AT = Instant.parse("2026-09-15T01:00:00Z");
    private static final Instant NEWEST_CREATED_AT = Instant.parse("2026-09-16T01:00:00Z");
    private static final Instant WITHDRAWN_AT = Instant.parse("2026-09-15T12:00:00Z");
    private static final Instant DELETED_AT = Instant.parse("2026-11-03T09:00:00Z");
    private static final UUID TIED_FIRST_ID = UUID
            .fromString("0198fc10-0000-7000-8000-000000000011");
    private static final UUID TIED_SECOND_ID = UUID
            .fromString("0198fc10-0000-7000-8000-000000000012");

    @Autowired
    private AdminFeedbackQueryRepository adminFeedbackQueryRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @BeforeEach
    void setUp() {
        insertUsers();
        insertFeedbacks();
        jdbcTemplate.update(
                "UPDATE feedbacks SET deleted_at = ? WHERE id = ?",
                Timestamp.from(DELETED_AT),
                OLDEST_FEEDBACK_ID
        );
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    @DisplayName("피드백 목록은 작성자 정보와 함께 최신순으로 조회한다")
    void findFeedbacks_createdAtDesc_returnsNewestFirstWithAuthor() {
        // When
        AdminPage<AdminFeedbackProjection> page = adminFeedbackQueryRepository.findFeedbacks(
                ALL_FEEDBACKS,
                AdminFeedbackSort.CREATED_AT_DESC,
                1,
                20
        );

        // Then
        assertThat(page.items())
                .extracting(AdminFeedbackProjection::feedbackId)
                .containsExactly(
                        NEWEST_FEEDBACK_ID,
                        MIDDLE_FEEDBACK_ID,
                        OLDEST_FEEDBACK_ID
                );
        assertThat(page.hasNext()).isFalse();

        AdminFeedbackProjection newest = page.items().getFirst();
        assertThat(newest.content()).isEqualTo("사진 업로드가 느려요.");
        assertThat(newest.createdAt()).isEqualTo(NEWEST_CREATED_AT);
        assertThat(newest.authorId()).isEqualTo(ACTIVE_USER_ID);
        assertThat(newest.authorEmail()).isEqualTo("active@example.com");
        assertThat(newest.authorStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(newest.authorAppVersion()).isEqualTo("1.2.3");
        assertThat(newest.authorDeletedAt()).isNull();
        assertThat(newest.deletedAt()).isNull();
        assertThat(page.items().getLast().deletedAt()).isEqualTo(DELETED_AT);
    }

    @Test
    @DisplayName("오래된 순 정렬을 선택할 수 있다")
    void findFeedbacks_createdAtAsc_returnsOldestFirst() {
        // When
        AdminPage<AdminFeedbackProjection> page = adminFeedbackQueryRepository.findFeedbacks(
                ALL_FEEDBACKS,
                AdminFeedbackSort.CREATED_AT_ASC,
                1,
                20
        );

        // Then
        assertThat(page.items())
                .extracting(AdminFeedbackProjection::feedbackId)
                .containsExactly(
                        OLDEST_FEEDBACK_ID,
                        MIDDLE_FEEDBACK_ID,
                        NEWEST_FEEDBACK_ID
                );
    }

    @Test
    @DisplayName("다음 페이지가 남으면 hasNext로 알린다")
    void findFeedbacks_withMorePages_reportsHasNext() {
        // When
        AdminPage<AdminFeedbackProjection> firstPage = adminFeedbackQueryRepository
                .findFeedbacks(
                        ALL_FEEDBACKS,
                        AdminFeedbackSort.CREATED_AT_DESC,
                        1,
                        2
                );
        AdminPage<AdminFeedbackProjection> lastPage = adminFeedbackQueryRepository
                .findFeedbacks(
                        ALL_FEEDBACKS,
                        AdminFeedbackSort.CREATED_AT_DESC,
                        2,
                        2
                );

        // Then
        assertThat(firstPage.items())
                .extracting(AdminFeedbackProjection::feedbackId)
                .containsExactly(NEWEST_FEEDBACK_ID, MIDDLE_FEEDBACK_ID);
        assertThat(firstPage.hasNext()).isTrue();
        assertThat(lastPage.items())
                .extracting(AdminFeedbackProjection::feedbackId)
                .containsExactly(OLDEST_FEEDBACK_ID);
        assertThat(lastPage.hasNext()).isFalse();
    }

    @Test
    @DisplayName("정지·탈퇴 회원이 남긴 피드백도 상태와 함께 조회한다")
    void findFeedbacks_bannedAndWithdrawnAuthors_areIncluded() {
        // When
        AdminPage<AdminFeedbackProjection> page = adminFeedbackQueryRepository.findFeedbacks(
                ALL_FEEDBACKS,
                AdminFeedbackSort.CREATED_AT_ASC,
                1,
                20
        );

        // Then
        AdminFeedbackProjection bannedFeedback = page.items().getFirst();
        assertThat(bannedFeedback.authorId()).isEqualTo(BANNED_USER_ID);
        assertThat(bannedFeedback.authorStatus()).isEqualTo(UserStatus.BANNED);
        assertThat(bannedFeedback.authorDeletedAt()).isNull();

        AdminFeedbackProjection withdrawnFeedback = page.items().get(1);
        assertThat(withdrawnFeedback.authorId()).isEqualTo(WITHDRAWN_USER_ID);
        assertThat(withdrawnFeedback.authorDeletedAt()).isEqualTo(WITHDRAWN_AT);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "0198fc00-0000-7000-8000-000000000001",
            "0198fc00-0000-7000-8000-000000000002",
            "0198fc00-0000-7000-8000-000000000003"
    })
    @DisplayName("정상·정지·탈퇴 회원별 필터는 삭제 여부와 무관하게 해당 회원의 기록만 반환한다")
    void findFeedbacks_userFilter_returnsOnlySelectedAuthor(String userId) {
        // Given
        UUID authorId = UUID.fromString(userId);

        // When
        AdminPage<AdminFeedbackProjection> page = adminFeedbackQueryRepository.findFeedbacks(
                new AdminFeedbackQueryCriteria(
                        authorId,
                        InquiryStatus.ALL,
                        false
                ),
                AdminFeedbackSort.CREATED_AT_DESC,
                1,
                20
        );

        // Then
        assertThat(page.items()).hasSize(1)
                .allMatch(feedback -> feedback.authorId().equals(authorId));
        assertThat(page.hasNext()).isFalse();
    }

    @Test
    @DisplayName("없는 사용자 ID로 필터링하면 빈 목록을 반환한다")
    void findFeedbacks_unknownUser_returnsEmptyPage() {
        // When
        AdminPage<AdminFeedbackProjection> page = adminFeedbackQueryRepository.findFeedbacks(
                new AdminFeedbackQueryCriteria(
                        UUID.randomUUID(),
                        InquiryStatus.ALL,
                        false
                ),
                AdminFeedbackSort.CREATED_AT_DESC,
                1,
                20
        );

        // Then
        assertThat(page.items()).isEmpty();
        assertThat(page.hasNext()).isFalse();
    }

    @ParameterizedTest
    @MethodSource("inquiryStatusResults")
    @DisplayName("문의 상태별로 현재 답변과 문의 삭제 여부를 적용한다")
    void findFeedbacks_inquiryStatus_filtersRelevantInquiries(InquiryStatus inquiryStatus,
            List<UUID> expectedIds) {
        // Given
        insertFeedback(
                TIED_FIRST_ID,
                ACTIVE_USER_ID,
                NEWEST_CREATED_AT.plusSeconds(1)
        );
        saveAnswer(NEWEST_FEEDBACK_ID, null);
        saveAnswer(OLDEST_FEEDBACK_ID, null);
        saveAnswer(TIED_FIRST_ID, NEWEST_CREATED_AT.plusSeconds(120));
        entityManager.flush();
        entityManager.clear();

        // When
        AdminPage<AdminFeedbackProjection> page = adminFeedbackQueryRepository.findFeedbacks(
                new AdminFeedbackQueryCriteria(
                        null,
                        inquiryStatus,
                        false
                ),
                AdminFeedbackSort.CREATED_AT_DESC,
                1,
                20
        );

        // Then
        assertThat(page.items()).extracting(AdminFeedbackProjection::feedbackId)
                .containsExactlyElementsOf(expectedIds);
    }

    @ParameterizedTest
    @MethodSource("bannedAuthorResults")
    @DisplayName("현재 차단된 작성자만 조회하며 답변 필요 조건과 조합할 수 있다")
    void findFeedbacks_bannedAuthorOnly_excludesWithdrawnAndActiveAuthors(
            InquiryStatus inquiryStatus,
            List<UUID> expectedIds
    ) {
        // Given
        jdbcTemplate.update("UPDATE users SET status = 'BANNED' WHERE id = ?", WITHDRAWN_USER_ID);
        insertFeedback(
                TIED_FIRST_ID,
                BANNED_USER_ID,
                NEWEST_CREATED_AT.plusSeconds(1)
        );
        entityManager.flush();
        entityManager.clear();

        // When
        AdminPage<AdminFeedbackProjection> page = adminFeedbackQueryRepository.findFeedbacks(
                new AdminFeedbackQueryCriteria(
                        null,
                        inquiryStatus,
                        true
                ),
                AdminFeedbackSort.CREATED_AT_DESC,
                1,
                20
        );

        // Then
        assertThat(page.items()).extracting(AdminFeedbackProjection::feedbackId)
                .containsExactlyElementsOf(expectedIds);
    }

    @Test
    @DisplayName("선택한 사용자의 답변 필요 문의만 골라낸 뒤 페이지를 나눈다")
    void findFeedbacks_needsAnswerByUser_filtersBeforePagination() {
        // Given
        insertFeedback(
                TIED_FIRST_ID,
                ACTIVE_USER_ID,
                NEWEST_CREATED_AT.plusSeconds(1)
        );
        insertFeedback(
                TIED_SECOND_ID,
                ACTIVE_USER_ID,
                NEWEST_CREATED_AT.plusSeconds(2)
        );
        insertFeedback(
                UUID.randomUUID(),
                BANNED_USER_ID,
                NEWEST_CREATED_AT.plusSeconds(3)
        );
        saveAnswer(TIED_FIRST_ID, NEWEST_CREATED_AT.plusSeconds(120));
        saveAnswer(TIED_SECOND_ID, null);
        entityManager.flush();
        entityManager.clear();
        AdminFeedbackQueryCriteria criteria = new AdminFeedbackQueryCriteria(
                ACTIVE_USER_ID,
                InquiryStatus.NEEDS_ANSWER,
                false
        );

        // When
        AdminPage<AdminFeedbackProjection> first = adminFeedbackQueryRepository.findFeedbacks(
                criteria,
                AdminFeedbackSort.CREATED_AT_DESC,
                1,
                1
        );
        AdminPage<AdminFeedbackProjection> last = adminFeedbackQueryRepository.findFeedbacks(
                criteria,
                AdminFeedbackSort.CREATED_AT_DESC,
                2,
                1
        );

        // Then
        assertThat(first.items()).extracting(AdminFeedbackProjection::feedbackId)
                .containsExactly(TIED_FIRST_ID);
        assertThat(first.hasNext()).isTrue();
        assertThat(last.items()).extracting(AdminFeedbackProjection::feedbackId)
                .containsExactly(NEWEST_FEEDBACK_ID);
        assertThat(last.hasNext()).isFalse();
    }

    @ParameterizedTest
    @MethodSource("genericPlanSettings")
    @DisplayName("전체·답변 필요 사용자별 조회는 증분 정렬 여부와 무관하게 인덱스 조건을 유지한다")
    void findFeedbacks_genericPlan_usesUserIndexCondition(InquiryStatus inquiryStatus,
            boolean incrementalSortEnabled) {
        // Given
        insertFeedbacksForOtherUsers();
        jdbcTemplate.execute("ANALYZE feedbacks");
        jdbcTemplate.execute("ANALYZE users");

        // When
        String plan = jdbcTemplate.execute((ConnectionCallback<String>) connection -> {
            PGConnection postgres = connection.unwrap(PGConnection.class);
            int previousThreshold = postgres.getPrepareThreshold();
            postgres.setPrepareThreshold(1);
            try {
                jdbcTemplate.execute("SET LOCAL plan_cache_mode = force_generic_plan");
                // 사용자 조건이 인덱스 검색 조건으로 유지되는지 확인한다.
                jdbcTemplate.execute("SET LOCAL enable_seqscan = off");
                jdbcTemplate
                        .execute("SET LOCAL enable_incremental_sort = " + incrementalSortEnabled);
                // 같은 연결에 남은 계획 대신 이 테스트의 통계와 설정으로 다시 계획한다.
                jdbcTemplate.execute("DISCARD PLANS");
                adminFeedbackQueryRepository.findFeedbacks(
                        new AdminFeedbackQueryCriteria(
                                ACTIVE_USER_ID,
                                inquiryStatus,
                                false
                        ),
                        AdminFeedbackSort.CREATED_AT_DESC,
                        1,
                        20
                );
                // Hibernate가 실제로 준비한 사용자별 쿼리의 실행 계획을 확인한다.
                String statementName = jdbcTemplate.queryForObject("""
                        SELECT name FROM pg_prepared_statements
                        WHERE statement LIKE '%from feedbacks%'
                          AND statement LIKE '%join users%'
                          AND statement LIKE '%where%user_id%answer_deleted_at%'
                          AND statement NOT LIKE '%pg_prepared_statements%'
                        ORDER BY prepare_time DESC LIMIT 1
                        """, String.class);
                return jdbcTemplate.queryForObject(
                        "EXPLAIN (FORMAT JSON) EXECUTE \"%s\" ('%s', '%3$s', '%3$s', '%3$s', '%3$s', false, 'BANNED', 21)"
                                .formatted(
                                        statementName,
                                        ACTIVE_USER_ID,
                                        inquiryStatus
                                ),
                        String.class
                );
            } finally {
                postgres.setPrepareThreshold(previousThreshold);
            }
        });

        // Then
        assertThat(plan).contains("\"Index Name\": \"idx_feedbacks_user_created_at\"",
                "\"Index Cond\": \"(user_id = $1)\"");
    }

    @ParameterizedTest
    @MethodSource("filteredPageOrders")
    @DisplayName("사용자 필터 후 페이지를 나누고 같은 시각은 ID로 안정적으로 정렬한다")
    void findFeedbacks_filteredTiedTimes_preservesSortAndPages(
            AdminFeedbackSort sort,
            List<UUID> firstIds,
            UUID lastId
    ) {
        // Given
        insertFeedback(
                TIED_FIRST_ID,
                ACTIVE_USER_ID,
                NEWEST_CREATED_AT
        );
        insertFeedback(
                TIED_SECOND_ID,
                ACTIVE_USER_ID,
                NEWEST_CREATED_AT
        );
        insertFeedback(
                UUID.randomUUID(),
                BANNED_USER_ID,
                NEWEST_CREATED_AT.plusSeconds(60)
        );

        // When
        AdminPage<AdminFeedbackProjection> first = adminFeedbackQueryRepository
                .findFeedbacks(
                        new AdminFeedbackQueryCriteria(
                                ACTIVE_USER_ID,
                                InquiryStatus.ALL,
                                false
                        ),
                        sort,
                        1,
                        2
                );
        AdminPage<AdminFeedbackProjection> last = adminFeedbackQueryRepository.findFeedbacks(
                new AdminFeedbackQueryCriteria(
                        ACTIVE_USER_ID,
                        InquiryStatus.ALL,
                        false
                ),
                sort,
                2,
                2
        );
        AdminPage<AdminFeedbackProjection> beyond = adminFeedbackQueryRepository
                .findFeedbacks(
                        new AdminFeedbackQueryCriteria(
                                ACTIVE_USER_ID,
                                InquiryStatus.ALL,
                                false
                        ),
                        sort,
                        3,
                        2
                );

        // Then
        assertThat(first.items()).extracting(AdminFeedbackProjection::feedbackId)
                .containsExactlyElementsOf(firstIds);
        assertThat(first.hasNext()).isTrue();
        assertThat(last.items()).extracting(AdminFeedbackProjection::feedbackId)
                .containsExactly(lastId);
        assertThat(last.hasNext()).isFalse();
        assertThat(beyond.items()).isEmpty();
        assertThat(beyond.hasNext()).isFalse();
    }

    @Test
    @DisplayName("관리자 상세는 삭제된 문의의 원문과 작성자 정보를 조회한다")
    void findFeedbackById_deletedFeedback_returnsContentAndAuthor() {
        // When
        AdminFeedbackDetailProjection detail = adminFeedbackQueryRepository
                .findFeedbackById(OLDEST_FEEDBACK_ID).orElseThrow();

        // Then
        assertThat(detail.feedbackId()).isEqualTo(OLDEST_FEEDBACK_ID);
        assertThat(detail.content()).isEqualTo("정지 사유가 궁금해요.");
        assertThat(detail.createdAt()).isEqualTo(OLDEST_CREATED_AT);
        assertThat(detail.deletedAt()).isEqualTo(DELETED_AT);
        assertThat(detail.authorId()).isEqualTo(BANNED_USER_ID);
        assertThat(detail.authorStatus()).isEqualTo(UserStatus.BANNED);
    }

    @Test
    @DisplayName("관리자 상세는 탈퇴한 작성자의 문의도 제외하지 않는다")
    void findFeedbackById_withdrawnAuthor_returnsFeedback() {
        // When
        AdminFeedbackDetailProjection detail = adminFeedbackQueryRepository
                .findFeedbackById(MIDDLE_FEEDBACK_ID).orElseThrow();

        // Then
        assertThat(detail.authorId()).isEqualTo(WITHDRAWN_USER_ID);
        assertThat(detail.authorDeletedAt()).isEqualTo(WITHDRAWN_AT);
        assertThat(detail.deletedAt()).isNull();
    }

    @Test
    @DisplayName("존재하지 않는 문의 ID는 관리자 상세 조회에서도 빈 결과다")
    void findFeedbackById_missingFeedback_returnsEmpty() {
        // When & Then
        assertThat(adminFeedbackQueryRepository.findFeedbackById(UUID.randomUUID())).isEmpty();
    }

    private void insertFeedbacksForOtherUsers() {
        // 사용자마다 문의를 한 건씩 두어 사용자 ID로 좁히는 조건의 선택도를 명확히 한다.
        jdbcTemplate.update("""
                WITH authors AS (
                    INSERT INTO users (id, email, signature_original_storage_key)
                    SELECT md5('plan-user-' || number)::uuid,
                           'plan-user-' || number || '@example.com',
                           'signatures/plan-user-' || number
                    FROM generate_series(1, 1000) AS number
                    RETURNING id
                )
                INSERT INTO feedbacks (id, user_id, content, created_at)
                SELECT md5('plan-feedback-' || id)::uuid, id, '다른 사용자의 문의', ?
                FROM authors
                """, Timestamp.from(NEWEST_CREATED_AT));
    }

    private static Stream<Arguments> genericPlanSettings() {
        return Stream.of(
                Arguments.of(InquiryStatus.ALL, true),
                Arguments.of(InquiryStatus.ALL, false),
                Arguments.of(InquiryStatus.NEEDS_ANSWER, true),
                Arguments.of(InquiryStatus.NEEDS_ANSWER, false)
        );
    }

    private static Stream<Arguments> inquiryStatusResults() {
        return Stream.of(
                Arguments.of(
                        InquiryStatus.ALL,
                        List.of(
                                TIED_FIRST_ID,
                                NEWEST_FEEDBACK_ID,
                                MIDDLE_FEEDBACK_ID,
                                OLDEST_FEEDBACK_ID
                        )
                ),
                Arguments.of(InquiryStatus.NEEDS_ANSWER, List.of(TIED_FIRST_ID)),
                Arguments.of(InquiryStatus.ANSWERED, List.of(NEWEST_FEEDBACK_ID)),
                Arguments.of(InquiryStatus.INQUIRY_DELETED, List.of(OLDEST_FEEDBACK_ID))
        );
    }

    private static Stream<Arguments> bannedAuthorResults() {
        return Stream.of(
                Arguments.of(InquiryStatus.ALL, List.of(TIED_FIRST_ID, OLDEST_FEEDBACK_ID)),
                Arguments.of(InquiryStatus.NEEDS_ANSWER, List.of(TIED_FIRST_ID))
        );
    }

    private void saveAnswer(UUID feedbackId, Instant answerDeletedAt) {
        jdbcTemplate.update(
                """
                        UPDATE feedbacks
                        SET answer_content = '답변 원문', answered_at = ?, answer_deleted_at = ?
                        WHERE id = ?
                        """,
                Timestamp.from(NEWEST_CREATED_AT.plusSeconds(60)),
                answerDeletedAt == null ? null : Timestamp.from(answerDeletedAt),
                feedbackId
        );
    }

    private static Stream<Arguments> filteredPageOrders() {
        return Stream.of(
                Arguments.of(
                        AdminFeedbackSort.CREATED_AT_DESC,
                        List.of(TIED_SECOND_ID, TIED_FIRST_ID),
                        NEWEST_FEEDBACK_ID
                ),
                Arguments.of(
                        AdminFeedbackSort.CREATED_AT_ASC,
                        List.of(NEWEST_FEEDBACK_ID, TIED_FIRST_ID),
                        TIED_SECOND_ID
                )
        );
    }

    private void insertFeedback(
            UUID feedbackId,
            UUID userId,
            Instant createdAt
    ) {
        jdbcTemplate.update(
                "INSERT INTO feedbacks (id, user_id, content, created_at) VALUES (?, ?, '추가 문의', ?)",
                feedbackId,
                userId,
                Timestamp.from(createdAt)
        );
    }

    private void insertUsers() {
        jdbcTemplate.update(
                """
                        INSERT INTO users (
                            id, email, status, signature_original_storage_key,
                            signature_thumbnail_storage_key, app_version,
                            created_at, updated_at, deleted_at
                        ) VALUES (
                            ?, 'active@example.com', 'ACTIVE',
                            'signatures/active-original', 'signatures/active-thumbnail',
                            '1.2.3', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, NULL
                        ), (
                            ?, 'banned@example.com', 'BANNED',
                            'signatures/banned-original', 'signatures/banned-thumbnail',
                            '1.2.2', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, NULL
                        ), (
                            ?, ?, 'ACTIVE', ?, NULL, NULL,
                            CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, ?
                        )
                        """,
                ACTIVE_USER_ID,
                BANNED_USER_ID,
                WITHDRAWN_USER_ID,
                "withdrawn+" + WITHDRAWN_USER_ID + "@chalkak.invalid",
                "withdrawn/" + WITHDRAWN_USER_ID,
                Timestamp.from(WITHDRAWN_AT)
        );
    }

    private void insertFeedbacks() {
        jdbcTemplate.update(
                """
                        INSERT INTO feedbacks (id, user_id, content, created_at)
                        VALUES (?, ?, ?, ?), (?, ?, ?, ?), (?, ?, ?, ?)
                        """,
                OLDEST_FEEDBACK_ID,
                BANNED_USER_ID,
                "정지 사유가 궁금해요.",
                Timestamp.from(OLDEST_CREATED_AT),
                MIDDLE_FEEDBACK_ID,
                WITHDRAWN_USER_ID,
                "탈퇴 전에 남기는 의견이에요.",
                Timestamp.from(MIDDLE_CREATED_AT),
                NEWEST_FEEDBACK_ID,
                ACTIVE_USER_ID,
                "사진 업로드가 느려요.",
                Timestamp.from(NEWEST_CREATED_AT)
        );
    }
}
