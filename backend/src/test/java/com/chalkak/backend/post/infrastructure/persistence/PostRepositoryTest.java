package com.chalkak.backend.post.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.chalkak.backend.post.domain.Post;
import com.chalkak.backend.post.repository.PostRepository;
import jakarta.persistence.EntityManager;
import java.util.Optional;
import java.util.UUID;
import org.hibernate.Hibernate;
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
@Import(PostRepositoryImpl.class)
class PostRepositoryTest {

    private static final UUID USER_ID = UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6570a1");
    private static final UUID TOPIC_ID = UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6570b2");
    private static final UUID PHOTO_ID = UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6570c3");
    private static final UUID POST_ID = UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6570d4");

    @Autowired
    private PostRepository postRepository;

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
                    'post-detail@example.com',
                    'ACTIVE',
                    'chalkak/dev/signatures/signature.png',
                    CURRENT_TIMESTAMP,
                    CURRENT_TIMESTAMP
                )
                """);
        jdbcTemplate.update("""
                INSERT INTO topics (
                    id, title, topic_date, start_at, end_at, created_at, updated_at
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
                    id, original_storage_key, thumbnail_storage_key, created_at, updated_at
                ) VALUES (
                    '0198f6c1-62ba-7d30-8b12-0f733b6570c3',
                    'chalkak/dev/posts/original.jpg',
                    'chalkak/dev/posts/thumbnail.jpg',
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
    @DisplayName("승인되고 삭제되지 않은 게시물을 관계와 함께 조회한다")
    void findVisibleById_approvedPost_returnsPost() {
        // When
        Optional<Post> result = postRepository.findVisibleById(POST_ID);

        // Then
        assertThat(result).isPresent();
        Post post = result.orElseThrow();
        assertThat(Hibernate.isInitialized(post.getTopic())).isTrue();
        assertThat(Hibernate.isInitialized(post.getPhoto())).isTrue();
        assertThat(Hibernate.isInitialized(post.getAuthor())).isTrue();
        assertThat(post.getId()).isEqualTo(POST_ID);
        assertThat(post.getTopic().getId()).isEqualTo(TOPIC_ID);
        assertThat(post.getPhoto().getOriginalStorageKey())
                .isEqualTo("chalkak/dev/posts/original.jpg");
        assertThat(post.getAuthor().getSignatureOriginalStorageKey())
                .isEqualTo("chalkak/dev/signatures/signature.png");
    }

    @Test
    @DisplayName("존재하지 않는 게시물은 조회하지 않는다")
    void findVisibleById_unknownPost_returnsEmpty() {
        // Given
        UUID unknownPostId = UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6570e5");

        // When
        Optional<Post> result = postRepository.findVisibleById(unknownPostId);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("미승인 게시물은 조회하지 않는다")
    void findVisibleById_rejectedPost_returnsEmpty() {
        // Given
        jdbcTemplate.update(
                "UPDATE posts SET moderation_status = 'REJECTED' WHERE id = ?",
                POST_ID
        );
        entityManager.flush();
        entityManager.clear();

        // When
        Optional<Post> result = postRepository.findVisibleById(POST_ID);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("삭제된 게시물은 조회하지 않는다")
    void findVisibleById_deletedPost_returnsEmpty() {
        // Given
        jdbcTemplate.update(
                "UPDATE posts SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?",
                POST_ID
        );
        entityManager.flush();
        entityManager.clear();

        // When
        Optional<Post> result = postRepository.findVisibleById(POST_ID);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("삭제된 주제의 게시물은 조회하지 않는다")
    void findVisibleById_deletedTopic_returnsEmpty() {
        // Given
        jdbcTemplate.update(
                "UPDATE topics SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?",
                TOPIC_ID
        );
        entityManager.flush();
        entityManager.clear();

        // When
        Optional<Post> result = postRepository.findVisibleById(POST_ID);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("삭제된 사진의 게시물은 조회하지 않는다")
    void findVisibleById_deletedPhoto_returnsEmpty() {
        // Given
        jdbcTemplate.update(
                "UPDATE photos SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?",
                PHOTO_ID
        );
        entityManager.flush();
        entityManager.clear();

        // When
        Optional<Post> result = postRepository.findVisibleById(POST_ID);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("삭제된 작성자의 게시물은 조회하지 않는다")
    void findVisibleById_deletedAuthor_returnsEmpty() {
        // Given
        jdbcTemplate.update(
                "UPDATE users SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?",
                USER_ID
        );
        entityManager.flush();
        entityManager.clear();

        // When
        Optional<Post> result = postRepository.findVisibleById(POST_ID);

        // Then
        assertThat(result).isEmpty();
    }
}
