package com.chalkak.backend.admin.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.chalkak.backend.admin.repository.AdminFeedbackQueryPage;
import com.chalkak.backend.admin.repository.AdminFeedbackQueryRepository;
import com.chalkak.backend.admin.repository.AdminFeedbackQuerySort;
import com.chalkak.backend.admin.repository.AdminFeedbackSummaryProjection;
import com.chalkak.backend.user.domain.UserStatus;
import jakarta.persistence.EntityManager;
import java.sql.Timestamp;
import java.time.Instant;
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
@Import(AdminFeedbackQueryRepositoryImpl.class)
class AdminFeedbackQueryRepositoryTest {

    private static final UUID ACTIVE_USER_ID =
            UUID.fromString("0198fc00-0000-7000-8000-000000000001");
    private static final UUID BANNED_USER_ID =
            UUID.fromString("0198fc00-0000-7000-8000-000000000002");
    private static final UUID WITHDRAWN_USER_ID =
            UUID.fromString("0198fc00-0000-7000-8000-000000000003");
    private static final UUID OLDEST_FEEDBACK_ID =
            UUID.fromString("0198fc10-0000-7000-8000-000000000001");
    private static final UUID MIDDLE_FEEDBACK_ID =
            UUID.fromString("0198fc10-0000-7000-8000-000000000002");
    private static final UUID NEWEST_FEEDBACK_ID =
            UUID.fromString("0198fc10-0000-7000-8000-000000000003");
    private static final Instant OLDEST_CREATED_AT = Instant.parse("2026-09-14T01:00:00Z");
    private static final Instant MIDDLE_CREATED_AT = Instant.parse("2026-09-15T01:00:00Z");
    private static final Instant NEWEST_CREATED_AT = Instant.parse("2026-09-16T01:00:00Z");
    private static final Instant WITHDRAWN_AT = Instant.parse("2026-09-15T12:00:00Z");

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
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    @DisplayName("피드백 목록은 작성자 정보와 함께 최신순으로 조회한다")
    void findFeedbacks_createdAtDesc_returnsNewestFirstWithAuthor() {
        // When
        AdminFeedbackQueryPage page = adminFeedbackQueryRepository.findFeedbacks(
                AdminFeedbackQuerySort.CREATED_AT_DESC, 1, 20);

        // Then
        assertThat(page.feedbacks())
                .extracting(AdminFeedbackSummaryProjection::feedbackId)
                .containsExactly(NEWEST_FEEDBACK_ID, MIDDLE_FEEDBACK_ID, OLDEST_FEEDBACK_ID);
        assertThat(page.hasNext()).isFalse();

        AdminFeedbackSummaryProjection newest = page.feedbacks().getFirst();
        assertThat(newest.content()).isEqualTo("사진 업로드가 느려요.");
        assertThat(newest.createdAt()).isEqualTo(NEWEST_CREATED_AT);
        assertThat(newest.authorId()).isEqualTo(ACTIVE_USER_ID);
        assertThat(newest.authorEmail()).isEqualTo("active@example.com");
        assertThat(newest.authorStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(newest.authorAppVersion()).isEqualTo("1.2.3");
        assertThat(newest.authorDeletedAt()).isNull();
    }

    @Test
    @DisplayName("오래된 순 정렬을 선택할 수 있다")
    void findFeedbacks_createdAtAsc_returnsOldestFirst() {
        // When
        AdminFeedbackQueryPage page = adminFeedbackQueryRepository.findFeedbacks(
                AdminFeedbackQuerySort.CREATED_AT_ASC, 1, 20);

        // Then
        assertThat(page.feedbacks())
                .extracting(AdminFeedbackSummaryProjection::feedbackId)
                .containsExactly(OLDEST_FEEDBACK_ID, MIDDLE_FEEDBACK_ID, NEWEST_FEEDBACK_ID);
    }

    @Test
    @DisplayName("다음 페이지가 남으면 hasNext로 알린다")
    void findFeedbacks_withMorePages_reportsHasNext() {
        // When
        AdminFeedbackQueryPage firstPage = adminFeedbackQueryRepository.findFeedbacks(
                AdminFeedbackQuerySort.CREATED_AT_DESC, 1, 2);
        AdminFeedbackQueryPage lastPage = adminFeedbackQueryRepository.findFeedbacks(
                AdminFeedbackQuerySort.CREATED_AT_DESC, 2, 2);

        // Then
        assertThat(firstPage.feedbacks())
                .extracting(AdminFeedbackSummaryProjection::feedbackId)
                .containsExactly(NEWEST_FEEDBACK_ID, MIDDLE_FEEDBACK_ID);
        assertThat(firstPage.hasNext()).isTrue();
        assertThat(lastPage.feedbacks())
                .extracting(AdminFeedbackSummaryProjection::feedbackId)
                .containsExactly(OLDEST_FEEDBACK_ID);
        assertThat(lastPage.hasNext()).isFalse();
    }

    @Test
    @DisplayName("정지·탈퇴 회원이 남긴 피드백도 상태와 함께 조회한다")
    void findFeedbacks_bannedAndWithdrawnAuthors_areIncluded() {
        // When
        AdminFeedbackQueryPage page = adminFeedbackQueryRepository.findFeedbacks(
                AdminFeedbackQuerySort.CREATED_AT_ASC, 1, 20);

        // Then
        AdminFeedbackSummaryProjection bannedFeedback = page.feedbacks().getFirst();
        assertThat(bannedFeedback.authorId()).isEqualTo(BANNED_USER_ID);
        assertThat(bannedFeedback.authorStatus()).isEqualTo(UserStatus.BANNED);
        assertThat(bannedFeedback.authorDeletedAt()).isNull();

        AdminFeedbackSummaryProjection withdrawnFeedback = page.feedbacks().get(1);
        assertThat(withdrawnFeedback.authorId()).isEqualTo(WITHDRAWN_USER_ID);
        assertThat(withdrawnFeedback.authorDeletedAt()).isEqualTo(WITHDRAWN_AT);
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
                Timestamp.from(WITHDRAWN_AT));
    }

    private void insertFeedbacks() {
        jdbcTemplate.update("""
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
                Timestamp.from(NEWEST_CREATED_AT));
    }
}
