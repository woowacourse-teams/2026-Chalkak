package com.chalkak.backend.post.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.chalkak.backend.post.domain.ModerationStatus;
import com.chalkak.backend.post.domain.Post;
import com.chalkak.backend.post.repository.PostRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.YearMonth;
import org.junit.jupiter.params.provider.EnumSource;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(PostRepositoryImpl.class)
class PostCalendarRepositoryTest {

    private static final UUID USER_ID =
            UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID OTHER_USER_ID =
            UUID.fromString("00000000-0000-0000-0000-0000000000a2");
    private static final UUID APPROVED_POST_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000201");
    private static final UUID PENDING_POST_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000202");

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @BeforeEach
    void setUp() {
        insertUser(USER_ID, "calendar@example.com");
        insertUser(OTHER_USER_ID, "other-calendar@example.com");

        insertPost(
                APPROVED_POST_ID,
                USER_ID,
                LocalDate.of(2026, 8, 1),
                ModerationStatus.APPROVED
        );
        insertPost(
                PENDING_POST_ID,
                USER_ID,
                LocalDate.of(2026, 8, 31),
                ModerationStatus.PENDING
        );
        insertPost(
                UUID.fromString("00000000-0000-0000-0000-000000000203"),
                USER_ID,
                LocalDate.of(2026, 8, 19),
                ModerationStatus.VALIDATING
        );
        insertPost(
                UUID.fromString("00000000-0000-0000-0000-000000000206"),
                USER_ID,
                LocalDate.of(2026, 8, 20),
                ModerationStatus.REJECTED
        );
        insertPost(
                UUID.fromString("00000000-0000-0000-0000-000000000204"),
                OTHER_USER_ID,
                LocalDate.of(2026, 8, 31),
                ModerationStatus.APPROVED
        );
        insertPost(
                UUID.fromString("00000000-0000-0000-0000-000000000205"),
                USER_ID,
                LocalDate.of(2026, 9, 1),
                ModerationStatus.APPROVED
        );

        insertPost(
                UUID.fromString("00000000-0000-0000-0000-000000000207"),
                OTHER_USER_ID,
                LocalDate.of(2026, 8, 12),
                ModerationStatus.PENDING
        );
        insertPost(
                UUID.fromString("00000000-0000-0000-0000-000000000208"),
                USER_ID,
                LocalDate.of(2026, 7, 31),
                ModerationStatus.PENDING
        );
        insertPost(
                UUID.fromString("00000000-0000-0000-0000-000000000209"),
                USER_ID,
                LocalDate.of(2026, 9, 2),
                ModerationStatus.PENDING
        );

        entityManager.flush();
        entityManager.clear();
    }

    @Test
    @DisplayName("본인의 해당 월 승인 대기·승인 게시물만 주제 날짜순으로 조회한다")
    void findCalendarPostsByAuthorIdAndTopicDateBetween_matchingPosts_returnsOrderedPosts() {
        // When
        List<Post> result = postRepository.findCalendarPostsByAuthorIdAndTopicDateBetween(
                USER_ID,
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 31)
        );

        // Then
        assertThat(result).extracting(Post::getId)
                .containsExactly(APPROVED_POST_ID, PENDING_POST_ID);
        assertThat(result).extracting(Post::getModerationStatus)
                .containsExactly(ModerationStatus.APPROVED, ModerationStatus.PENDING);
        assertThat(result).allSatisfy(post -> {
            assertThat(Hibernate.isInitialized(post.getTopic())).isTrue();
            assertThat(Hibernate.isInitialized(post.getPhoto())).isTrue();
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "UPDATE posts SET deleted_at = CURRENT_TIMESTAMP",
            "UPDATE topics SET deleted_at = CURRENT_TIMESTAMP",
            "UPDATE photos SET deleted_at = CURRENT_TIMESTAMP"
    })
    @DisplayName("삭제된 게시물과 연결 자원은 캘린더에서 제외한다")
    void findCalendarPostsByAuthorIdAndTopicDateBetween_deletedResource_returnsEmpty(
            String updateStatement
    ) {
        // Given
        jdbcTemplate.update(updateStatement);
        entityManager.flush();
        entityManager.clear();

        // When
        List<Post> result = postRepository.findCalendarPostsByAuthorIdAndTopicDateBetween(
                USER_ID,
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 31)
        );

        // Then
        assertThat(result).isEmpty();
    }

    private void insertUser(UUID userId, String email) {
        jdbcTemplate.update("""
                INSERT INTO users (
                    id, email, status, signature_original_storage_key, created_at, updated_at
                ) VALUES (
                    ?, ?, 'ACTIVE', ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """, userId, email, "chalkak/dev/signatures/" + userId + ".png");
    }

    private void insertPost(
            UUID postId,
            UUID userId,
            LocalDate topicDate,
            ModerationStatus moderationStatus
    ) {
        UUID topicId = UUID.nameUUIDFromBytes(("topic-" + topicDate).getBytes());
        UUID photoId = UUID.nameUUIDFromBytes(("photo-" + postId).getBytes());
        jdbcTemplate.update("""
                INSERT INTO topics (
                    id, title, topic_date, starts_at, ends_at, created_at, updated_at
                ) SELECT
                    ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP + INTERVAL '1 day',
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                WHERE NOT EXISTS (
                    SELECT 1
                    FROM topics
                    WHERE topic_date = ?
                      AND deleted_at IS NULL
                )
                """, topicId, "캘린더 주제", topicDate, topicDate);
        jdbcTemplate.update("""
                INSERT INTO photos (
                    id, original_storage_key, thumbnail_storage_key, created_at, updated_at
                ) VALUES (
                    ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """,
                photoId,
                "chalkak/dev/posts/original/" + postId + ".webp",
                "chalkak/dev/posts/thumbnail/" + postId + ".webp"
        );
        jdbcTemplate.update("""
                INSERT INTO posts (
                    id, user_id, topic_id, photo_id, moderation_status, created_at, updated_at
                ) VALUES (
                    ?, ?, (SELECT id FROM topics WHERE topic_date = ?), ?, ?::moderation_status,
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """, postId, userId, topicDate, photoId, moderationStatus.name());
    }
    @Test
    @DisplayName("주제 날짜 기준 전체 연월을 중복 없이 최신순으로 조회한다")
    void findCalendarMonthsByAuthorId_multipleYears_returnsDistinctMonthsDescending() {
        // Given
        insertPost(
                UUID.fromString("00000000-0000-0000-0000-000000000210"),
                USER_ID,
                LocalDate.of(2025, 12, 31),
                ModerationStatus.PENDING);
        insertPost(UUID.randomUUID(), USER_ID, LocalDate.of(2026, 1, 1), ModerationStatus.APPROVED);
        insertPost(UUID.randomUUID(), USER_ID, LocalDate.of(2025, 8, 31), ModerationStatus.PENDING);
        entityManager.flush();
        entityManager.clear();

        // When
        List<YearMonth> result = postRepository.findCalendarMonthsByAuthorId(USER_ID);

        // Then
        assertThat(result).containsExactly(
                YearMonth.of(2026, 9),
                YearMonth.of(2026, 8),
                YearMonth.of(2026, 7),
                YearMonth.of(2026, 1),
                YearMonth.of(2025, 12),
                YearMonth.of(2025, 8));
    }

    @ParameterizedTest
    @EnumSource(ModerationStatus.class)
    @DisplayName("캘린더에 표시되는 검수 상태의 연월만 포함한다")
    void findCalendarMonthsByAuthorId_moderationStatus_returnsOnlyVisibleMonths(
            ModerationStatus moderationStatus
    ) {
        // Given
        insertPost(UUID.randomUUID(), USER_ID, LocalDate.of(2026, 4, 1), moderationStatus);
        entityManager.flush();
        entityManager.clear();

        // When
        List<YearMonth> result = postRepository.findCalendarMonthsByAuthorId(USER_ID);

        // Then
        if (moderationStatus == ModerationStatus.PENDING
                || moderationStatus == ModerationStatus.APPROVED) {
            assertThat(result).contains(YearMonth.of(2026, 4));
        } else {
            assertThat(result).doesNotContain(YearMonth.of(2026, 4));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"posts", "topics", "photos", "users"})
    @DisplayName("게시물이나 연결 자원이 모두 삭제되면 연월 목록은 비어 있다")
    void findCalendarMonthsByAuthorId_deletedResource_returnsEmpty(String table) {
        // Given
        jdbcTemplate.update("UPDATE " + table + " SET deleted_at = CURRENT_TIMESTAMP");
        entityManager.flush();
        entityManager.clear();

        // When
        List<YearMonth> result = postRepository.findCalendarMonthsByAuthorId(USER_ID);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("다른 사용자의 기록만 있는 달은 연월 목록에서 제외한다")
    void findCalendarMonthsByAuthorId_otherUserMonth_excludesMonth() {
        // Given
        insertPost(
                UUID.randomUUID(),
                OTHER_USER_ID,
                LocalDate.of(2026, 4, 1),
                ModerationStatus.APPROVED);
        entityManager.flush();
        entityManager.clear();

        // When
        List<YearMonth> result = postRepository.findCalendarMonthsByAuthorId(USER_ID);

        // Then
        assertThat(result).containsExactly(
                YearMonth.of(2026, 9), YearMonth.of(2026, 8), YearMonth.of(2026, 7));
    }

    @Test
    @DisplayName("기록이 없는 사용자의 연월 목록은 비어 있다")
    void findCalendarMonthsByAuthorId_noPosts_returnsEmpty() {
        // Given
        UUID userId = UUID.randomUUID();
        insertUser(userId, "empty-calendar@example.com");
        entityManager.flush();
        entityManager.clear();

        // When
        List<YearMonth> result = postRepository.findCalendarMonthsByAuthorId(userId);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("같은 달의 기록이 남아 있으면 유지하고 마지막 기록 삭제 시 그 달을 제외한다")
    void findCalendarMonthsByAuthorId_lastPostDeleted_removesOnlyThatMonth() {
        // Given
        jdbcTemplate.update("UPDATE posts SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?",
                APPROVED_POST_ID);
        entityManager.flush();
        entityManager.clear();
        assertThat(postRepository.findCalendarMonthsByAuthorId(USER_ID))
                .contains(YearMonth.of(2026, 8));

        // When
        jdbcTemplate.update("UPDATE posts SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?",
                PENDING_POST_ID);
        entityManager.flush();
        entityManager.clear();
        List<YearMonth> result = postRepository.findCalendarMonthsByAuthorId(USER_ID);

        // Then
        assertThat(result).containsExactly(YearMonth.of(2026, 9), YearMonth.of(2026, 7));
    }

}
