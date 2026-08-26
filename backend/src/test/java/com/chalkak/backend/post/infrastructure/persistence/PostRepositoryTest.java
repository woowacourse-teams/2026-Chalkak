package com.chalkak.backend.post.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.photo.domain.Photo;
import com.chalkak.backend.post.domain.Post;
import com.chalkak.backend.post.repository.PostRepository;
import com.chalkak.backend.topic.domain.Topic;
import com.chalkak.backend.user.domain.User;
import jakarta.persistence.EntityManager;
import java.util.Optional;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(PostRepositoryImpl.class)
class PostRepositoryTest {

    private static final UUID USER_ID = UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6570a1");
    private static final UUID SECOND_USER_ID =
            UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6570a2");
    private static final UUID TOPIC_ID = UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6570b2");
    private static final UUID PHOTO_ID = UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6570c3");
    private static final UUID POST_ID = UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6570d4");
    private static final UUID SECOND_POST_ID =
            UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6570e5");

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

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
    @DisplayName("공개 게시물을 최신순으로 슬라이스 조회한다")
    void findVisibleRecentByTopicId_visiblePosts_returnsRecentSlice() {
        // Given
        insertSecondVisiblePost();

        // When
        var result = postRepository.findVisibleRecentByTopicId(TOPIC_ID, 0, 1);

        // Then
        assertThat(result.posts()).extracting(Post::getId).containsExactly(SECOND_POST_ID);
        assertThat(result.hasNext()).isTrue();
        assertThat(Hibernate.isInitialized(result.posts().getFirst().getTopic())).isFalse();
        assertThat(Hibernate.isInitialized(result.posts().getFirst().getPhoto())).isTrue();
        assertThat(Hibernate.isInitialized(result.posts().getFirst().getAuthor())).isTrue();
    }

    @Test
    @DisplayName("생성 시각이 같으면 게시물 ID 내림차순으로 조회한다")
    void findVisibleRecentByTopicId_sameCreatedAt_returnsIdDescendingSlice() {
        // Given
        insertSecondVisiblePost();
        jdbcTemplate.update(
                "UPDATE posts SET created_at = '2026-08-12T02:00:00Z' WHERE id IN (?, ?)",
                POST_ID,
                SECOND_POST_ID
        );
        entityManager.flush();
        entityManager.clear();

        // When
        var result = postRepository.findVisibleRecentByTopicId(TOPIC_ID, 0, 20);

        // Then
        assertThat(result.posts()).extracting(Post::getId)
                .containsExactly(SECOND_POST_ID, POST_ID);
        assertThat(result.hasNext()).isFalse();
    }

    @Test
    @DisplayName("공개 게시물을 좋아요 개수 내림차순으로 조회한다")
    void findVisiblePopularByTopicId_visiblePosts_returnsLikeCountDescendingSlice() {
        // Given
        insertSecondVisiblePost();
        insertPostLikesForPopularOrder();

        // When
        var result = postRepository.findVisiblePopularByTopicId(TOPIC_ID, 0, 20);

        // Then
        assertThat(result.posts()).extracting(Post::getId)
                .containsExactly(POST_ID, SECOND_POST_ID);
        assertThat(result.hasNext()).isFalse();
        assertThat(Hibernate.isInitialized(result.posts().getFirst().getTopic())).isFalse();
        assertThat(Hibernate.isInitialized(result.posts().getFirst().getPhoto())).isTrue();
        assertThat(Hibernate.isInitialized(result.posts().getFirst().getAuthor())).isTrue();
    }

    @Test
    @DisplayName("좋아요 개수가 같으면 생성 시각 내림차순으로 조회한다")
    void findVisiblePopularByTopicId_sameLikeCount_returnsCreatedAtDescendingSlice() {
        // Given
        insertSecondVisiblePost();

        // When
        var result = postRepository.findVisiblePopularByTopicId(TOPIC_ID, 0, 20);

        // Then
        assertThat(result.posts()).extracting(Post::getId)
                .containsExactly(SECOND_POST_ID, POST_ID);
        assertThat(result.hasNext()).isFalse();
    }

    @Test
    @DisplayName("인기순 조회를 페이지로 나누면 정렬 순서가 이어진다")
    void findVisiblePopularByTopicId_multiplePages_returnsStablePages() {
        // Given
        insertSecondVisiblePost();
        insertPostLikesForPopularOrder();

        // When
        var firstPage = postRepository.findVisiblePopularByTopicId(TOPIC_ID, 0, 1);
        var secondPage = postRepository.findVisiblePopularByTopicId(TOPIC_ID, 1, 1);

        // Then
        assertThat(firstPage.posts()).extracting(Post::getId).containsExactly(POST_ID);
        assertThat(secondPage.posts()).extracting(Post::getId).containsExactly(SECOND_POST_ID);
        assertThat(firstPage.hasNext()).isTrue();
        assertThat(secondPage.hasNext()).isFalse();
    }

    @Test
    @DisplayName("좋아요 개수와 생성 시각이 같으면 게시물 ID 오름차순으로 조회한다")
    void findVisiblePopularByTopicId_sameLikeCountAndCreatedAt_returnsIdAscendingSlice() {
        // Given
        insertSecondVisiblePost();
        jdbcTemplate.update(
                "UPDATE posts SET created_at = '2026-08-12T02:00:00Z' WHERE id IN (?, ?)",
                POST_ID,
                SECOND_POST_ID
        );
        entityManager.flush();
        entityManager.clear();

        // When
        var result = postRepository.findVisiblePopularByTopicId(TOPIC_ID, 0, 20);

        // Then
        assertThat(result.posts()).extracting(Post::getId)
                .containsExactly(POST_ID, SECOND_POST_ID);
        assertThat(result.hasNext()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "UPDATE posts SET moderation_status = 'REJECTED'",
            "UPDATE posts SET deleted_at = CURRENT_TIMESTAMP",
            "UPDATE topics SET deleted_at = CURRENT_TIMESTAMP",
            "UPDATE photos SET deleted_at = CURRENT_TIMESTAMP",
            "UPDATE users SET deleted_at = CURRENT_TIMESTAMP"
    })
    @DisplayName("공개 조건을 충족하지 않는 게시물은 목록에서 제외한다")
    void findVisibleByTopicId_invisiblePost_returnsEmptySlice(String updateStatement) {
        // Given
        jdbcTemplate.update(updateStatement);
        entityManager.flush();
        entityManager.clear();

        // When
        var recentResult = postRepository.findVisibleRecentByTopicId(TOPIC_ID, 0, 20);
        var randomResult = postRepository.findVisibleRandomByTopicId(
                TOPIC_ID,
                "f4c3a091",
                0,
                20
        );
        var popularResult = postRepository.findVisiblePopularByTopicId(TOPIC_ID, 0, 20);

        // Then
        assertThat(recentResult.posts()).isEmpty();
        assertThat(recentResult.hasNext()).isFalse();
        assertThat(randomResult.posts()).isEmpty();
        assertThat(randomResult.hasNext()).isFalse();
        assertThat(popularResult.posts()).isEmpty();
        assertThat(popularResult.hasNext()).isFalse();
    }

    @Test
    @DisplayName("같은 시드로 공개 게시물을 조회하면 동일한 랜덤 순서를 반환한다")
    void findVisibleRandomByTopicId_sameSeed_returnsSameOrder() {
        // Given
        insertSecondVisiblePost();

        // When
        var firstResult = postRepository.findVisibleRandomByTopicId(TOPIC_ID, "f4c3a091", 0, 20);
        var secondResult = postRepository.findVisibleRandomByTopicId(TOPIC_ID, "f4c3a091", 0, 20);

        // Then
        assertThat(firstResult.posts()).extracting(Post::getId)
                .containsExactlyElementsOf(
                        secondResult.posts().stream().map(Post::getId).toList()
                );
        assertThat(firstResult.hasNext()).isFalse();
        assertThat(Hibernate.isInitialized(firstResult.posts().getFirst().getTopic())).isFalse();
        assertThat(Hibernate.isInitialized(firstResult.posts().getFirst().getPhoto())).isTrue();
        assertThat(Hibernate.isInitialized(firstResult.posts().getFirst().getAuthor())).isTrue();
    }

    @Test
    @DisplayName("같은 시드로 페이지를 나누면 랜덤 정렬 순서가 이어진다")
    void findVisibleRandomByTopicId_sameSeed_returnsStablePages() {
        // Given
        insertSecondVisiblePost();
        String randomSeed = "f4c3a091";
        var fullResult = postRepository.findVisibleRandomByTopicId(
                TOPIC_ID,
                randomSeed,
                0,
                20
        );

        // When
        var firstPage = postRepository.findVisibleRandomByTopicId(TOPIC_ID, randomSeed, 0, 1);
        var secondPage = postRepository.findVisibleRandomByTopicId(TOPIC_ID, randomSeed, 1, 1);

        // Then
        assertThat(firstPage.posts()).extracting(Post::getId)
                .containsExactly(fullResult.posts().get(0).getId());
        assertThat(secondPage.posts()).extracting(Post::getId)
                .containsExactly(fullResult.posts().get(1).getId());
        assertThat(firstPage.hasNext()).isTrue();
        assertThat(secondPage.hasNext()).isFalse();
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

    @Test
    @DisplayName("동시 생성으로 사용자와 주제 유니크 제약을 위반하면 비즈니스 예외로 변환한다")
    void save_duplicateUserAndTopic_throwsBusinessException() {
        // Given
        User author = entityManager.find(User.class, USER_ID);
        Topic topic = entityManager.find(Topic.class, TOPIC_ID);
        Photo photo = Photo.createPhoto("chalkak/dev/posts/concurrent-original.png");
        Post duplicatePost = Post.createPost(author, topic, photo, UUID.randomUUID(), null);

        // When & Then
        assertThatThrownBy(() -> postRepository.save(duplicatePost))
                .isInstanceOf(BusinessException.class)
                .hasMessage("이미 사용된 게시물 생성 정보입니다.");
    }

    @Test
    @DisplayName("동시 생성으로 사진 스토리지 키 유니크 제약을 위반하면 비즈니스 예외로 변환한다")
    void save_duplicatePhotoStorageKey_throwsBusinessException() {
        // Given
        UUID secondUserId = UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6570a2");
        UUID secondTopicId = UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6570b3");
        jdbcTemplate.update("""
                INSERT INTO users (
                    id, email, status, signature_original_storage_key, created_at, updated_at
                ) VALUES (
                    ?, 'post-duplicate-photo@example.com', 'ACTIVE',
                    'chalkak/dev/signatures/duplicate-photo.png',
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """, secondUserId);
        jdbcTemplate.update("""
                INSERT INTO topics (
                    id, title, topic_date, starts_at, ends_at, created_at, updated_at
                ) VALUES (
                    ?, '사진 중복 테스트 주제', '2026-08-13',
                    '2026-08-13T00:00:00Z', '2026-08-14T00:00:00Z',
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """, secondTopicId);
        User author = entityManager.find(User.class, secondUserId);
        Topic topic = entityManager.find(Topic.class, secondTopicId);
        Photo photo = Photo.createPhoto("chalkak/dev/posts/original.jpg");
        Post duplicatePhotoPost = Post.createPost(author, topic, photo, UUID.randomUUID(), null);

        // When & Then
        assertThatThrownBy(() -> postRepository.save(duplicatePhotoPost))
                .isInstanceOf(BusinessException.class)
                .hasMessage("이미 사용된 게시물 생성 정보입니다.");
    }

    @Test
    @DisplayName("게시물 저장 실패 시 cascade로 저장한 사진도 함께 롤백한다")
    void save_postConstraintViolation_rollsBackCascadedPhoto() {
        // Given
        UUID authorId = UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6570b4");
        UUID topicId = UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6570b5");
        String orphanStorageKey = "chalkak/dev/posts/rolled-back-photo.png";
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        // When
        BusinessException exception = catchThrowableOfType(
                BusinessException.class,
                () -> transactionTemplate.executeWithoutResult(status -> {
                    insertPostCreationConflictFixtures(authorId, topicId);
                    User author = entityManager.find(User.class, authorId);
                    Topic topic = entityManager.find(Topic.class, topicId);
                    Photo photo = Photo.createPhoto(orphanStorageKey);
                    Post duplicatePost = Post.createPost(author, topic, photo, UUID.randomUUID(), null);

                    postRepository.save(duplicatePost);
                })
        );

        // Then
        assertThat(exception).hasMessage("이미 사용된 게시물 생성 정보입니다.");
        Integer photoCount = transactionTemplate.execute(status -> jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM photos WHERE original_storage_key = ?",
                Integer.class,
                orphanStorageKey
        ));
        assertThat(photoCount).isZero();
    }

    private void insertPostCreationConflictFixtures(UUID authorId, UUID topicId) {
        UUID photoId = UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6570b6");
        jdbcTemplate.update("""
                INSERT INTO users (
                    id, email, status, signature_original_storage_key, created_at, updated_at
                ) VALUES (
                    ?, 'post-rollback@example.com', 'ACTIVE',
                    'chalkak/dev/signatures/rollback.png',
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """, authorId);
        jdbcTemplate.update("""
                INSERT INTO topics (
                    id, title, topic_date, starts_at, ends_at, created_at, updated_at
                ) VALUES (
                    ?, '롤백 테스트 주제', '2099-01-01',
                    '2099-01-01T00:00:00Z', '2099-01-02T00:00:00Z',
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """, topicId);
        jdbcTemplate.update("""
                INSERT INTO photos (
                    id, original_storage_key, created_at, updated_at
                ) VALUES (
                    ?, 'chalkak/dev/posts/rollback-existing.png',
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """, photoId);
        jdbcTemplate.update("""
                INSERT INTO posts (
                    user_id, topic_id, photo_id, moderation_status, created_at, updated_at
                ) VALUES (
                    ?, ?, ?, 'APPROVED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """, authorId, topicId, photoId);
    }

    private void insertSecondVisiblePost() {
        jdbcTemplate.update(
                "UPDATE posts SET created_at = '2026-08-12T01:00:00Z' WHERE id = ?",
                POST_ID
        );
        jdbcTemplate.update("""
                INSERT INTO users (
                    id, email, status, signature_original_storage_key, created_at, updated_at
                ) VALUES (
                    '0198f6c1-62ba-7d30-8b12-0f733b6570a2',
                    'post-list@example.com',
                    'ACTIVE',
                    'chalkak/dev/signatures/signature-2.png',
                    CURRENT_TIMESTAMP,
                    CURRENT_TIMESTAMP
                )
                """);
        jdbcTemplate.update("""
                INSERT INTO photos (
                    id, original_storage_key, thumbnail_storage_key, created_at, updated_at
                ) VALUES (
                    '0198f6c1-62ba-7d30-8b12-0f733b6570c4',
                    'chalkak/dev/posts/original-2.jpg',
                    'chalkak/dev/posts/thumbnail-2.jpg',
                    CURRENT_TIMESTAMP,
                    CURRENT_TIMESTAMP
                )
                """);
        jdbcTemplate.update("""
                INSERT INTO posts (
                    id, user_id, topic_id, photo_id, title, moderation_status, created_at, updated_at
                ) VALUES (
                    '0198f6c1-62ba-7d30-8b12-0f733b6570e5',
                    '0198f6c1-62ba-7d30-8b12-0f733b6570a2',
                    '0198f6c1-62ba-7d30-8b12-0f733b6570b2',
                    '0198f6c1-62ba-7d30-8b12-0f733b6570c4',
                    '두 번째 순간',
                    'APPROVED',
                    '2026-08-12T02:00:00Z',
                    CURRENT_TIMESTAMP
                )
                """);
        entityManager.flush();
        entityManager.clear();
    }

    private void insertPostLikesForPopularOrder() {
        jdbcTemplate.update(
                "INSERT INTO post_likes (post_id, user_id) VALUES (?, ?), (?, ?), (?, ?)",
                POST_ID,
                USER_ID,
                POST_ID,
                SECOND_USER_ID,
                SECOND_POST_ID,
                USER_ID
        );
        entityManager.flush();
        entityManager.clear();
    }
}
