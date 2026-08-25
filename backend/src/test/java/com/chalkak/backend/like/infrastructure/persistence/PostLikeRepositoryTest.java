package com.chalkak.backend.like.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.chalkak.backend.like.repository.PostLikeRepository;
import jakarta.persistence.EntityManager;
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
@Import(PostLikeRepositoryImpl.class)
class PostLikeRepositoryTest {

    private static final UUID USER_ID = UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6570a1");
    private static final UUID POST_ID = UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6570d4");

    @Autowired
    private PostLikeRepository postLikeRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("""
                INSERT INTO users (
                    id, email, status, signature_original_storage_key, created_at, updated_at
                ) VALUES (
                    '0198f6c1-62ba-7d30-8b12-0f733b6570a1',
                    'post-like-repository@example.com',
                    'ACTIVE',
                    'chalkak/dev/signatures/post-like-repository.png',
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
                    'chalkak/dev/posts/post-like-repository.jpg',
                    CURRENT_TIMESTAMP,
                    CURRENT_TIMESTAMP
                )
                """);
        jdbcTemplate.update("""
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

        entityManager.flush();
        entityManager.clear();
    }

    @Test
    @DisplayName("좋아요가 없으면 새 좋아요를 생성한다")
    void createIfAbsent_newPostLike_createsPostLike() {
        // When
        int createdCount = postLikeRepository.createIfAbsent(POST_ID, USER_ID);
        entityManager.clear();

        // Then
        assertThat(createdCount).isEqualTo(1);
        assertThat(postLikeRepository.countByPostId(POST_ID)).isEqualTo(1L);
    }

    @Test
    @DisplayName("같은 사용자의 좋아요가 이미 있으면 새 좋아요를 생성하지 않는다")
    void createIfAbsent_existingPostLike_doesNotCreateDuplicate() {
        // Given
        postLikeRepository.createIfAbsent(POST_ID, USER_ID);
        entityManager.clear();

        // When
        int createdCount = postLikeRepository.createIfAbsent(POST_ID, USER_ID);
        entityManager.clear();

        // Then
        assertThat(createdCount).isZero();
        assertThat(postLikeRepository.countByPostId(POST_ID)).isEqualTo(1L);
    }

    @Test
    @DisplayName("등록된 좋아요를 삭제한다")
    void deleteByPostIdAndUserId_existingPostLike_deletesPostLike() {
        // Given
        postLikeRepository.createIfAbsent(POST_ID, USER_ID);
        entityManager.clear();

        // When
        int deletedCount = postLikeRepository.deleteByPostIdAndUserId(POST_ID, USER_ID);
        entityManager.clear();

        // Then
        assertThat(deletedCount).isEqualTo(1);
        assertThat(postLikeRepository.countByPostId(POST_ID)).isZero();
    }

    @Test
    @DisplayName("등록된 좋아요가 없어도 삭제 요청을 정상 처리한다")
    void deleteByPostIdAndUserId_missingPostLike_deletesNothing() {
        // When
        int deletedCount = postLikeRepository.deleteByPostIdAndUserId(POST_ID, USER_ID);
        entityManager.clear();

        // Then
        assertThat(deletedCount).isZero();
        assertThat(postLikeRepository.countByPostId(POST_ID)).isZero();
    }
}
