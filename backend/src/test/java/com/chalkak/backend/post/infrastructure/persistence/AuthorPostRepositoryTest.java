package com.chalkak.backend.post.infrastructure.persistence;

import static com.chalkak.backend.post.support.AuthorPostFixtures.createPost;
import static com.chalkak.backend.post.support.AuthorPostFixtures.createUser;
import static org.assertj.core.api.Assertions.assertThat;

import com.chalkak.backend.post.domain.ModerationStatus;
import com.chalkak.backend.post.domain.Post;
import com.chalkak.backend.post.repository.PostRepository;
import com.chalkak.backend.post.repository.PostSlice;
import jakarta.persistence.EntityManager;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(PostRepositoryImpl.class)
class AuthorPostRepositoryTest {

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @ParameterizedTest
    @ValueSource(ints = {19, 20, 21})
    @DisplayName("제출 순서와 관계없이 주제 날짜 최신순으로 사진과 주제를 최대 20개 조회한다")
    void findVisibleRecentByAuthorId_boundaryCount_returnsLatestTopics(int count) {
        // Given
        UUID authorId = createUser(jdbcTemplate);
        List<UUID> expectedIds = new ArrayList<>();
        for (int day = count - 1; day >= 0; day--) {
            UUID postId = createPost(jdbcTemplate, authorId,
                    LocalDate.of(2026, 4, 1).plusDays(day), ModerationStatus.APPROVED);
            expectedIds.add(postId);
            jdbcTemplate.update("UPDATE posts SET created_at = ? WHERE id = ?",
                    Timestamp.from(Instant.parse("2026-04-01T00:00:00Z").minusSeconds(day)),
                    postId);
        }
        entityManager.flush();
        entityManager.clear();

        // When
        PostSlice slice = postRepository.findVisibleRecentByAuthorId(authorId, null, 20);
        List<Post> posts = slice.posts();

        // Then
        assertThat(posts).extracting(Post::getId)
                .containsExactlyElementsOf(expectedIds.subList(0, Math.min(count, 20)));
        assertThat(slice.hasNext()).isEqualTo(count > 20);
        assertThat(posts).allSatisfy(post -> {
            assertThat(Hibernate.isInitialized(post.getPhoto())).isTrue();
            assertThat(Hibernate.isInitialized(post.getTopic())).isTrue();
        });
    }

    @ParameterizedTest
    @EnumSource(value = ModerationStatus.class, names = {"VALIDATING", "PENDING", "REJECTED"})
    @DisplayName("지정한 작성자의 승인된 사진만 조회한다")
    void findVisibleRecentByAuthorId_otherAuthorAndUnapprovedPosts_excludesBoth(
            ModerationStatus status
    ) {
        // Given
        UUID authorId = createUser(jdbcTemplate);
        UUID approvedId = createPost(jdbcTemplate, authorId,
                LocalDate.of(2026, 4, 1), ModerationStatus.APPROVED);
        createPost(jdbcTemplate, authorId, LocalDate.of(2026, 4, 2), status);
        createPost(jdbcTemplate, createUser(jdbcTemplate),
                LocalDate.of(2026, 4, 3), ModerationStatus.APPROVED);
        entityManager.flush();
        entityManager.clear();

        // When
        PostSlice slice = postRepository.findVisibleRecentByAuthorId(authorId, null, 20);
        List<Post> posts = slice.posts();

        // Then
        assertThat(posts).extracting(Post::getId).containsExactly(approvedId);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "UPDATE posts SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?",
            "UPDATE topics SET deleted_at = CURRENT_TIMESTAMP WHERE id = (SELECT topic_id FROM posts WHERE id = ?)",
            "UPDATE photos SET deleted_at = CURRENT_TIMESTAMP WHERE id = (SELECT photo_id FROM posts WHERE id = ?)",
            "UPDATE users SET deleted_at = CURRENT_TIMESTAMP WHERE id = (SELECT user_id FROM posts WHERE id = ?)"
    })
    @DisplayName("게시물과 연결 자원 중 하나라도 삭제되면 공개 사진에서 제외한다")
    void findVisibleRecentByAuthorId_deletedResource_returnsEmpty(String deletion) {
        // Given
        UUID authorId = createUser(jdbcTemplate);
        UUID postId = createPost(jdbcTemplate, authorId,
                LocalDate.of(2026, 4, 1), ModerationStatus.APPROVED);
        jdbcTemplate.update(deletion, postId);
        entityManager.flush();
        entityManager.clear();

        // When
        PostSlice slice = postRepository.findVisibleRecentByAuthorId(authorId, null, 20);
        List<Post> posts = slice.posts();

        // Then
        assertThat(posts).isEmpty();
    }
}
