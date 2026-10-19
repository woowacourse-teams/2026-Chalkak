package com.chalkak.backend.post.service;

import static com.chalkak.backend.post.support.AuthorPostFixtures.createPost;
import static com.chalkak.backend.post.support.AuthorPostFixtures.createUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chalkak.backend.exception.NotFoundException;
import com.chalkak.backend.exception.UnauthorizedException;
import com.chalkak.backend.post.domain.ModerationStatus;
import com.chalkak.backend.support.IntegrationTestSupport;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class AuthorPostQueryServiceTest extends IntegrationTestSupport {

    @Autowired
    private PostQueryService postQueryService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @ParameterizedTest
    @ValueSource(strings = {"ACTIVE", "BANNED"})
    @DisplayName("정상·정지 작성자의 공개 사진을 실제 이미지 URL과 주제 정보로 반환한다")
    void getAuthorPosts_existingAuthor_returnsPhotoAndTopic(String authorStatus) {
        // Given
        UUID authorId = createUser(jdbcTemplate);
        UUID viewerId = createUser(jdbcTemplate);
        jdbcTemplate.update("UPDATE users SET status = ?::user_status WHERE id = ?",
                authorStatus, authorId);
        UUID postId = createPost(jdbcTemplate, authorId,
                LocalDate.of(2026, 4, 1), ModerationStatus.APPROVED);

        // When
        AuthorPostListResult result = postQueryService.getAuthorPosts(authorId, 20, viewerId);

        // Then
        assertThat(result.posts()).singleElement().satisfies(post -> {
            assertThat(post.id()).isEqualTo(postId);
            assertThat(post.originalImageUrl())
                    .isEqualTo("https://cdn.example.com/test/posts/" + postId + ".webp");
            assertThat(post.thumbnailImageUrl())
                    .isEqualTo("https://cdn.example.com/test/posts/" + postId + "-thumbnail.webp");
            assertThat(post.topic().id()).isNotNull();
            assertThat(post.topic().title()).isEqualTo("주제 2026-04-01");
            assertThat(post.topic().topicDate()).isEqualTo(LocalDate.of(2026, 4, 1));
        });
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2})
    @DisplayName("요청한 개수만큼 최근 공개 사진을 조회한다")
    void getAuthorPosts_requestedPageSize_limitsReturnedPosts(int pageSize) {
        // Given
        UUID authorId = createUser(jdbcTemplate);
        UUID viewerId = createUser(jdbcTemplate);
        createPost(jdbcTemplate, authorId, LocalDate.of(2026, 4, 1), ModerationStatus.APPROVED);
        UUID latestPostId = createPost(jdbcTemplate, authorId,
                LocalDate.of(2026, 4, 2), ModerationStatus.APPROVED);

        // When
        AuthorPostListResult result = postQueryService.getAuthorPosts(authorId, pageSize, viewerId);

        // Then
        assertThat(result.posts()).hasSize(pageSize);
        assertThat(result.posts().getFirst().id()).isEqualTo(latestPostId);
    }

    @Test
    @DisplayName("본인 조회에도 승인 대기 사진을 제외하고 공개 사진이 없으면 빈 목록을 반환한다")
    void getAuthorPosts_onlyOwnPendingPost_returnsEmpty() {
        // Given
        UUID authorId = createUser(jdbcTemplate);
        createPost(jdbcTemplate, authorId, LocalDate.of(2026, 4, 1), ModerationStatus.PENDING);

        // When
        AuthorPostListResult result = postQueryService.getAuthorPosts(authorId, 20, authorId);

        // Then
        assertThat(result.posts()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @DisplayName("없거나 탈퇴한 작성자는 찾을 수 없음 예외를 반환한다")
    void getAuthorPosts_missingOrWithdrawnAuthor_throwsNotFound(boolean withdrawn) {
        // Given
        UUID viewerId = createUser(jdbcTemplate);
        UUID authorId = withdrawn ? createUser(jdbcTemplate) : UUID.randomUUID();
        if (withdrawn) {
            jdbcTemplate.update("UPDATE users SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?",
                    authorId);
        }

        // When & Then
        assertThatThrownBy(() -> postQueryService.getAuthorPosts(authorId, 20, viewerId))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("작성자를 찾을 수 없습니다.");
    }

    @Test
    @DisplayName("유효하지 않은 조회자는 인증 예외를 반환한다")
    void getAuthorPosts_missingViewer_throwsUnauthorized() {
        // Given
        UUID authorId = createUser(jdbcTemplate);

        // When & Then
        assertThatThrownBy(() -> postQueryService.getAuthorPosts(authorId, 20, UUID.randomUUID()))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("유효하지 않은 인증 정보입니다.");
    }
}
