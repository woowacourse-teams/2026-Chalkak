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
import java.util.ArrayList;
import java.util.List;
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
        jdbcTemplate.update(
                "UPDATE users SET status = ?::user_status WHERE id = ?",
                authorStatus,
                authorId
        );
        UUID postId = createPost(
                jdbcTemplate,
                authorId,
                LocalDate.of(
                        2026,
                        4,
                        1
                ),
                ModerationStatus.APPROVED
        );

        // When
        AuthorPostListResult result = postQueryService.getAuthorPosts(
                authorId,
                1,
                20,
                viewerId
        );

        // Then
        assertThat(result.posts()).singleElement().satisfies(post -> {
            assertThat(post.id()).isEqualTo(postId);
            assertThat(post.originalImageUrl())
                    .isEqualTo("https://cdn.example.com/test/posts/" + postId + ".webp");
            assertThat(post.thumbnailImageUrl())
                    .isEqualTo("https://cdn.example.com/test/posts/" + postId + "-thumbnail.webp");
            assertThat(post.topic().id()).isNotNull();
            assertThat(post.topic().title()).isEqualTo("주제 2026-04-01");
            assertThat(post.topic().topicDate()).isEqualTo(LocalDate.of(
                    2026,
                    4,
                    1
            ));
        });
    }

    @Test
    @DisplayName("썸네일이 없는 사진도 원본 URL과 null 썸네일로 조회한다")
    void getAuthorPosts_missingThumbnail_returnsOriginalUrlAndNullThumbnail() {
        // Given
        UUID authorId = createUser(jdbcTemplate);
        UUID viewerId = createUser(jdbcTemplate);
        UUID postId = createPost(
                jdbcTemplate,
                authorId,
                LocalDate.of(
                        2026,
                        4,
                        1
                ),
                ModerationStatus.APPROVED
        );
        jdbcTemplate.update("""
                UPDATE photos
                SET thumbnail_storage_key = NULL
                WHERE id = (SELECT photo_id FROM posts WHERE id = ?)
                """, postId);

        // When
        AuthorPostListResult result = postQueryService.getAuthorPosts(
                authorId,
                1,
                20,
                viewerId
        );

        // Then
        assertThat(result.posts()).singleElement().satisfies(post -> {
            assertThat(post.id()).isEqualTo(postId);
            assertThat(post.originalImageUrl())
                    .isEqualTo("https://cdn.example.com/test/posts/" + postId + ".webp");
            assertThat(post.thumbnailImageUrl()).isNull();
        });
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2})
    @DisplayName("요청한 개수만큼 최근 공개 사진을 조회한다")
    void getAuthorPosts_requestedPageSize_limitsReturnedPosts(int pageSize) {
        // Given
        UUID authorId = createUser(jdbcTemplate);
        UUID viewerId = createUser(jdbcTemplate);
        createPost(
                jdbcTemplate,
                authorId,
                LocalDate.of(
                        2026,
                        4,
                        1
                ),
                ModerationStatus.APPROVED
        );
        UUID latestPostId = createPost(
                jdbcTemplate,
                authorId,
                LocalDate.of(
                        2026,
                        4,
                        2
                ),
                ModerationStatus.APPROVED
        );

        // When
        AuthorPostListResult result = postQueryService.getAuthorPosts(
                authorId,
                1,
                pageSize,
                viewerId
        );

        // Then
        assertThat(result.posts()).hasSize(pageSize);
        assertThat(result.posts().getFirst().id()).isEqualTo(latestPostId);
    }

    @Test
    @DisplayName("본인 조회에도 승인 대기 사진을 제외하고 공개 사진이 없으면 빈 목록을 반환한다")
    void getAuthorPosts_onlyOwnPendingPost_returnsEmpty() {
        // Given
        UUID authorId = createUser(jdbcTemplate);
        createPost(
                jdbcTemplate,
                authorId,
                LocalDate.of(
                        2026,
                        4,
                        1
                ),
                ModerationStatus.PENDING
        );

        // When
        AuthorPostListResult result = postQueryService.getAuthorPosts(
                authorId,
                1,
                20,
                authorId
        );

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
        assertThatThrownBy(() -> postQueryService.getAuthorPosts(
                authorId,
                1,
                20,
                viewerId
        ))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("작성자를 찾을 수 없습니다.");
    }

    @Test
    @DisplayName("유효하지 않은 조회자는 인증 예외를 반환한다")
    void getAuthorPosts_missingViewer_throwsUnauthorized() {
        // Given
        UUID authorId = createUser(jdbcTemplate);

        // When & Then
        assertThatThrownBy(
                () -> postQueryService.getAuthorPosts(
                        authorId,
                        1,
                        20,
                        UUID.randomUUID()
                ))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("유효하지 않은 인증 정보입니다.");
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 20, 100})
    @DisplayName("월과 연도를 넘어 21개 사진을 요청한 크기로 중복 없이 이어보고 종료한다")
    void getAuthorPosts_twentyOnePhotosAcrossYear_returnsAllWithoutDuplicates(int pageSize) {
        // Given
        UUID authorId = createUser(jdbcTemplate);
        UUID viewerId = createUser(jdbcTemplate);
        List<UUID> expectedIds = new ArrayList<>();
        for (int day = 20; day >= 0; day--) {
            expectedIds.add(createPost(
                    jdbcTemplate,
                    authorId,
                    LocalDate.of(
                            2025,
                            12,
                            15
                    ).plusDays(day),
                    ModerationStatus.APPROVED
            ));
        }
        List<UUID> actualIds = new ArrayList<>();
        int currentPage = 0;
        boolean hasNext = true;

        // When
        for (int page = 1; page <= 21 && hasNext; page++) {
            AuthorPostListResult result = postQueryService.getAuthorPosts(
                    authorId,
                    page,
                    pageSize,
                    viewerId
            );
            assertThat(result.posts()).hasSizeLessThanOrEqualTo(pageSize);
            actualIds.addAll(
                    result.posts().stream().map(AuthorPostListResult.PostSummary::id).toList());
            if (result.hasNext()) {
                assertThat(result.posts()).hasSize(pageSize);
            }
            assertThat(result.currentPage()).isEqualTo(page);
            assertThat(result.pageSize()).isEqualTo(pageSize);
            currentPage = result.currentPage();
            hasNext = result.hasNext();
        }

        // Then
        assertThat(actualIds).containsExactlyElementsOf(expectedIds).doesNotHaveDuplicates();
        assertThat(hasNext).isFalse();
        assertThat(currentPage).isEqualTo((21 + pageSize - 1) / pageSize);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 20})
    @DisplayName("빈 목록과 정확히 한 페이지인 목록은 요청한 페이지 정보와 함께 종료한다")
    void getAuthorPosts_noExtraPhoto_returnsEndMetadata(int count) {
        // Given
        UUID authorId = createUser(jdbcTemplate);
        UUID viewerId = createUser(jdbcTemplate);
        for (int day = 0; day < count; day++) {
            createPost(
                    jdbcTemplate,
                    authorId,
                    LocalDate.of(
                            2026,
                            4,
                            1
                    ).plusDays(day),
                    ModerationStatus.APPROVED
            );
        }

        // When
        AuthorPostListResult result = postQueryService.getAuthorPosts(
                authorId,
                1,
                20,
                viewerId
        );

        // Then
        assertThat(result.posts()).hasSize(count);
        assertThat(result.hasNext()).isFalse();
        assertThat(result.currentPage()).isEqualTo(1);
        assertThat(result.pageSize()).isEqualTo(20);
    }

    @Test
    @DisplayName("조회 중 승인된 사진을 현재 순서에 반영하며 페이지 사이의 중복을 허용한다")
    void getAuthorPosts_lateApproval_returnsCurrentPageOrder() {
        // Given
        UUID authorId = createUser(jdbcTemplate);
        UUID viewerId = createUser(jdbcTemplate);
        UUID newestId = createPost(
                jdbcTemplate,
                authorId,
                LocalDate.of(
                        2026,
                        4,
                        5
                ),
                ModerationStatus.APPROVED
        );
        UUID pastApprovalId = createPost(
                jdbcTemplate,
                authorId,
                LocalDate.of(
                        2026,
                        4,
                        4
                ),
                ModerationStatus.PENDING
        );
        UUID boundaryId = createPost(
                jdbcTemplate,
                authorId,
                LocalDate.of(
                        2026,
                        4,
                        3
                ),
                ModerationStatus.APPROVED
        );
        UUID nextApprovalId = createPost(
                jdbcTemplate,
                authorId,
                LocalDate.of(
                        2026,
                        4,
                        2
                ),
                ModerationStatus.PENDING
        );
        UUID oldestId = createPost(
                jdbcTemplate,
                authorId,
                LocalDate.of(
                        2026,
                        4,
                        1
                ),
                ModerationStatus.APPROVED
        );
        AuthorPostListResult first = postQueryService.getAuthorPosts(
                authorId,
                1,
                2,
                viewerId
        );
        assertThat(first.posts()).extracting(AuthorPostListResult.PostSummary::id)
                .containsExactly(newestId, boundaryId);
        jdbcTemplate.update(
                "UPDATE posts SET moderation_status = 'APPROVED' WHERE id IN (?, ?)",
                pastApprovalId,
                nextApprovalId
        );

        // When
        AuthorPostListResult second = postQueryService.getAuthorPosts(
                authorId,
                2,
                2,
                viewerId
        );
        AuthorPostListResult third = postQueryService.getAuthorPosts(
                authorId,
                3,
                2,
                viewerId
        );
        AuthorPostListResult refreshed = postQueryService.getAuthorPosts(
                authorId,
                1,
                2,
                viewerId
        );

        // Then
        assertThat(second.posts()).extracting(AuthorPostListResult.PostSummary::id)
                .containsExactly(boundaryId, nextApprovalId);
        assertThat(second.currentPage()).isEqualTo(2);
        assertThat(second.hasNext()).isTrue();
        assertThat(third.posts()).extracting(AuthorPostListResult.PostSummary::id)
                .containsExactly(oldestId);
        assertThat(third.hasNext()).isFalse();
        assertThat(refreshed.posts()).extracting(AuthorPostListResult.PostSummary::id)
                .containsExactly(newestId, pastApprovalId);
    }

    @Test
    @DisplayName("조회 중 삭제된 사진을 제외한 현재 순서를 사용하며 페이지 이동 중 누락을 허용한다")
    void getAuthorPosts_deletedPhotos_returnsCurrentOffsetWithoutSnapshot() {
        // Given
        UUID authorId = createUser(jdbcTemplate);
        UUID viewerId = createUser(jdbcTemplate);
        List<UUID> ids = new ArrayList<>();
        for (int day = 1; day <= 5; day++) {
            ids.add(createPost(
                    jdbcTemplate,
                    authorId,
                    LocalDate.of(
                            2026,
                            4,
                            day
                    ),
                    ModerationStatus.APPROVED
            ));
        }
        AuthorPostListResult first = postQueryService.getAuthorPosts(
                authorId,
                1,
                2,
                viewerId
        );
        assertThat(first.posts()).extracting(AuthorPostListResult.PostSummary::id)
                .containsExactly(ids.get(4), ids.get(3));
        jdbcTemplate.update("UPDATE posts SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?",
                ids.get(3));
        jdbcTemplate.update("""
                UPDATE photos SET deleted_at = CURRENT_TIMESTAMP
                WHERE id = (SELECT photo_id FROM posts WHERE id = ?)
                """, ids.get(2));

        // When
        AuthorPostListResult second = postQueryService.getAuthorPosts(
                authorId,
                2,
                2,
                viewerId
        );
        AuthorPostListResult refreshed = postQueryService.getAuthorPosts(
                authorId,
                1,
                2,
                viewerId
        );
        AuthorPostListResult pastEnd = postQueryService.getAuthorPosts(
                authorId,
                3,
                2,
                viewerId
        );

        // Then
        assertThat(second.posts()).extracting(AuthorPostListResult.PostSummary::id)
                .containsExactly(ids.get(0));
        assertThat(second.hasNext()).isFalse();
        assertThat(refreshed.posts()).extracting(AuthorPostListResult.PostSummary::id)
                .containsExactly(ids.get(4), ids.get(1));
        assertThat(pastEnd.posts()).isEmpty();
        assertThat(pastEnd.currentPage()).isEqualTo(3);
        assertThat(pastEnd.pageSize()).isEqualTo(2);
        assertThat(pastEnd.hasNext()).isFalse();
    }
}
