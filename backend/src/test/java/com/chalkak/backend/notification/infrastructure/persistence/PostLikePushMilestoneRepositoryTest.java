package com.chalkak.backend.notification.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chalkak.backend.notification.domain.PostLikePushMilestone;
import com.chalkak.backend.notification.repository.PostLikePushMilestoneRepository;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(PostLikePushMilestoneRepositoryImpl.class)
class PostLikePushMilestoneRepositoryTest {

    private static final UUID USER_ID = UUID.fromString("019a36f0-3db8-72dc-8f2d-07a15432b101");
    private static final UUID POST_ID = UUID.fromString("019a36f0-3db8-72dc-8f2d-07a15432b104");
    private static final Instant CREATED_AT = Instant.parse("2026-10-30T01:00:00Z");

    @Autowired
    private PostLikePushMilestoneRepository milestoneRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("""
                INSERT INTO users (
                    id, email, status, signature_original_storage_key, created_at, updated_at
                ) VALUES (
                    ?, 'milestone-repository@example.com', 'ACTIVE',
                    'chalkak/dev/signatures/milestone-repository.png',
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """, USER_ID);
        jdbcTemplate.update("""
                INSERT INTO topics (
                    id, title, topic_date, starts_at, ends_at, created_at, updated_at
                ) VALUES (
                    '019a36f0-3db8-72dc-8f2d-07a15432b102',
                    '마일스톤 저장소', CURRENT_DATE,
                    CURRENT_TIMESTAMP - INTERVAL '1 hour',
                    CURRENT_TIMESTAMP + INTERVAL '1 hour',
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """);
        jdbcTemplate.update("""
                INSERT INTO photos (
                    id, original_storage_key, created_at, updated_at
                ) VALUES (
                    '019a36f0-3db8-72dc-8f2d-07a15432b103',
                    'chalkak/dev/posts/milestone-repository.jpg',
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """);
        jdbcTemplate.update(
                """
                        INSERT INTO posts (
                            id, user_id, topic_id, photo_id, title,
                            moderation_status, created_at, updated_at
                        ) VALUES (
                            ?, ?,
                            '019a36f0-3db8-72dc-8f2d-07a15432b102',
                            '019a36f0-3db8-72dc-8f2d-07a15432b103',
                            '마일스톤 게시물', 'APPROVED',
                            CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                        )
                        """,
                POST_ID,
                USER_ID
        );
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    @DisplayName("게시물에 기록된 가장 높은 좋아요 마일스톤을 조회한다")
    void findMaxCountByPostId_existingMilestones_returnsMaxCount() {
        // Given
        milestoneRepository.save(PostLikePushMilestone.create(
                POST_ID,
                USER_ID,
                5L,
                CREATED_AT
        ));
        milestoneRepository.save(PostLikePushMilestone.create(
                POST_ID,
                USER_ID,
                10L,
                CREATED_AT.plusSeconds(1)
        ));
        entityManager.flush();
        entityManager.clear();

        // When & Then
        assertThat(milestoneRepository.findMaxCountByPostId(POST_ID))
                .contains(10L);
    }

    @Test
    @DisplayName("마일스톤이 없으면 가장 높은 단계를 반환하지 않는다")
    void findMaxCountByPostId_missingMilestone_returnsEmpty() {
        assertThat(milestoneRepository.findMaxCountByPostId(POST_ID)).isEmpty();
    }

    @Test
    @DisplayName("같은 게시물의 같은 마일스톤은 한 번만 저장한다")
    void save_duplicatePostAndMilestone_violatesUniqueConstraint() {
        // Given
        milestoneRepository.save(PostLikePushMilestone.create(
                POST_ID,
                USER_ID,
                5L,
                CREATED_AT
        ));
        entityManager.flush();
        entityManager.clear();

        // When & Then
        assertThatThrownBy(() -> {
            milestoneRepository.save(PostLikePushMilestone.create(
                    POST_ID,
                    USER_ID,
                    5L,
                    CREATED_AT.plusSeconds(1)
            ));
            entityManager.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("5단위가 아닌 좋아요 수는 DB에도 저장할 수 없다")
    void save_nonMultipleMilestone_violatesCheckConstraint() {
        assertThatThrownBy(() -> jdbcTemplate.update(
                """
                        INSERT INTO post_like_push_milestones (
                            post_id, user_id, milestone_count, created_at,
                            sqs_publish_status, next_attempt_at
                        ) VALUES (?, ?, 6, CURRENT_TIMESTAMP, 'PENDING', CURRENT_TIMESTAMP)
                        """,
                POST_ID,
                USER_ID
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("발행 시각이 도래한 대기 작업을 다음 시도 시각 순으로 제한해 조회한다")
    void findDuePublicationIds_duePendingMilestones_returnsOrderedLimitedIds() {
        // Given
        UUID first = saveMilestone(5L, CREATED_AT);
        UUID second = saveMilestone(10L, CREATED_AT.plusSeconds(1));
        saveMilestone(15L, CREATED_AT.plusSeconds(2));
        entityManager.flush();
        entityManager.clear();

        // When & Then
        assertThat(milestoneRepository.findDuePublicationIds(CREATED_AT.plusSeconds(2), 2))
                .containsExactly(first, second);
    }

    @Test
    @DisplayName("사건 ID와 작성자가 일치하는 좋아요 마일스톤을 발송용으로 조회한다")
    void findForPushByIdAndUserId_matchingMilestone_returnsMilestone() {
        // Given
        UUID milestoneId = saveMilestone(5L, CREATED_AT);
        entityManager.flush();
        entityManager.clear();

        // When & Then
        assertThat(milestoneRepository.findForPushByIdAndUserId(milestoneId, USER_ID))
                .get()
                .extracting(PostLikePushMilestone::getPostId)
                .isEqualTo(POST_ID);
        assertThat(milestoneRepository.findForPushByIdAndUserId(
                milestoneId,
                UUID.randomUUID()
        )).isEmpty();
    }

    private UUID saveMilestone(long count, Instant createdAt) {
        return milestoneRepository.save(PostLikePushMilestone.create(
                POST_ID,
                USER_ID,
                count,
                createdAt
        )).getId();
    }
}
