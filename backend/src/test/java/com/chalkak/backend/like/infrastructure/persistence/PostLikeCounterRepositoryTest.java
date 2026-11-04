package com.chalkak.backend.like.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chalkak.backend.like.repository.PostLikeCount;
import com.chalkak.backend.like.repository.PostLikeCounterRepository;
import com.chalkak.backend.support.PostgresRepositoryTest;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

@PostgresRepositoryTest
class PostLikeCounterRepositoryTest {

    private static final UUID POST_ID = UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6570d4");

    @Autowired
    private PostLikeCounterRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("""
                INSERT INTO users (
                    id, email, status, signature_original_storage_key, created_at, updated_at
                ) VALUES
                    (
                        '0198f6c1-62ba-7d30-8b12-0f733b6570a1',
                        'post-like-author@example.com',
                        'ACTIVE',
                        'chalkak/dev/signatures/post-like-author.png',
                        CURRENT_TIMESTAMP,
                        CURRENT_TIMESTAMP
                    ),
                    (
                        '0198f6c1-62ba-7d30-8b12-0f733b6570a2',
                        'post-like-user@example.com',
                        'ACTIVE',
                        'chalkak/dev/signatures/post-like-user.png',
                        CURRENT_TIMESTAMP,
                        CURRENT_TIMESTAMP
                    )
                """);
        jdbcTemplate.update("""
                INSERT INTO topics (
                    id, title, topic_date, starts_at, ends_at, created_at, updated_at
                ) VALUES (
                    '0198f6c1-62ba-7d30-8b12-0f733b6570b2',
                    '오늘 가장 기억에 남은 순간',
                    '2026-08-12',
                    '2026-08-12T00:00:00Z',
                    '2026-08-13T00:00:00Z',
                    CURRENT_TIMESTAMP,
                    CURRENT_TIMESTAMP
                )
                """);
        jdbcTemplate.update("""
                INSERT INTO photos (
                    id, original_storage_key, created_at, updated_at
                ) VALUES (
                    '0198f6c1-62ba-7d30-8b12-0f733b6570c3',
                    'chalkak/dev/posts/post-like-service.jpg',
                    CURRENT_TIMESTAMP,
                    CURRENT_TIMESTAMP
                )
                """);
        jdbcTemplate
                .update("""
                        INSERT INTO posts (
                            id, user_id, topic_id, photo_id, title, moderation_status, created_at, updated_at
                        ) VALUES (
                            '0198f6c1-62ba-7d30-8b12-0f733b6570d4',
                            '0198f6c1-62ba-7d30-8b12-0f733b6570a1',
                            '0198f6c1-62ba-7d30-8b12-0f733b6570b2',
                            '0198f6c1-62ba-7d30-8b12-0f733b6570c3',
                            '오늘의 순간',
                            'APPROVED',
                            CURRENT_TIMESTAMP,
                            CURRENT_TIMESTAMP
                        )
                        """);
        jdbcTemplate.update("INSERT INTO post_like_counts (post_id) VALUES (?)", POST_ID);
    }

    @Test
    @DisplayName("최고 단계는 증가하며 좋아요 감소·집계 초기화 뒤에도 유지한다")
    void updateLastMilestoneCount_countChanges_preservesProgress() {
        // Given
        jdbcTemplate.update("UPDATE post_like_counts SET like_count = 5 WHERE post_id = ?",
                POST_ID);
        entityManager.flush();
        entityManager.clear();

        // When
        boolean updated = repository.updateLastMilestoneCount(POST_ID, 5L);
        repository.decrement(POST_ID);
        repository.reset(POST_ID);
        entityManager.flush();
        entityManager.clear();

        // Then
        assertThat(repository.getCountByPostId(POST_ID)).isZero();
        assertThat(updated).isTrue();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT last_milestone_count FROM post_like_counts WHERE post_id = ?",
                Long.class,
                POST_ID
        )).isEqualTo(5L);
    }

    @Test
    @DisplayName("최고 단계를 같은 값이나 낮은 값으로 갱신하려 하면 false를 반환하고 보존한다")
    void updateLastMilestoneCount_nonIncreasing_returnsFalse() {
        // Given
        repository.updateLastMilestoneCount(POST_ID, 10L);
        entityManager.flush();
        entityManager.clear();

        // When & Then
        assertThat(repository.updateLastMilestoneCount(POST_ID, 10L)).isFalse();
        assertThat(repository.updateLastMilestoneCount(POST_ID, 5L)).isFalse();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT last_milestone_count FROM post_like_counts WHERE post_id = ?",
                Long.class,
                POST_ID
        )).isEqualTo(10L);
    }

    @Test
    @DisplayName("최고 단계를 갱신할 집계가 없으면 false를 반환한다")
    void updateLastMilestoneCount_missingCounter_returnsFalse() {
        // Given
        UUID missing = UUID.randomUUID();
        entityManager.flush();
        entityManager.clear();

        // When & Then
        assertThat(repository.updateLastMilestoneCount(missing, 5L)).isFalse();
    }

    @Test
    @DisplayName("좋아요가 없는 집계 행은 0으로 조회한다")
    void getCountByPostId_zeroCounter_returnsZero() {
        // Given
        entityManager.flush();
        entityManager.clear();

        // When & Then
        assertThat(repository.getCountByPostId(POST_ID)).isZero();
    }

    @Test
    @DisplayName("int 범위를 넘는 집계도 long으로 조회한다")
    void getCountByPostId_largeCounter_returnsLong() {
        // Given
        jdbcTemplate.update("UPDATE post_like_counts SET like_count = 2147483648 WHERE post_id = ?",
                POST_ID);
        entityManager.flush();
        entityManager.clear();

        // When & Then
        assertThat(repository.getCountByPostId(POST_ID)).isEqualTo(2147483648L);
    }

    @Test
    @DisplayName("없는 집계는 0으로 대체하지 않고 무결성 오류로 처리한다")
    void getCountByPostId_missingCounter_throwsIntegrityException() {
        // Given
        entityManager.flush();
        entityManager.clear();

        // When & Then
        assertThatThrownBy(() -> repository.getCountByPostId(UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("집계 일괄 조회는 0개 행도 포함하고 중복 ID를 한 번만 반환한다")
    void getCountsByPostIds_duplicateIds_returnsZeroCounterOnce() {
        // Given
        entityManager.flush();
        entityManager.clear();

        // When & Then
        assertThat(repository.getCountsByPostIds(List.of(POST_ID, POST_ID)))
                .containsExactly(new PostLikeCount(POST_ID, 0));
    }

    @Test
    @DisplayName("일괄 조회에서 일부 집계가 누락되면 무결성 오류로 처리한다")
    void getCountsByPostIds_partiallyMissing_throwsIntegrityException() {
        // Given
        entityManager.flush();
        entityManager.clear();

        // When & Then
        assertThatThrownBy(() -> repository.getCountsByPostIds(List.of(POST_ID, UUID.randomUUID())))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("빈 게시물 목록의 집계 조회는 빈 결과를 반환한다")
    void getCountsByPostIds_emptyIds_returnsEmpty() {
        // Given
        entityManager.flush();
        entityManager.clear();

        // When & Then
        assertThat(repository.getCountsByPostIds(List.of())).isEmpty();
    }
}
