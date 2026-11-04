package com.chalkak.backend.post.service;

import static com.chalkak.backend.like.support.PostLikeCountFixtures.synchronizeFixtureCounts;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

import com.chalkak.backend.exception.NotFoundException;
import com.chalkak.backend.photo.domain.Photo;
import com.chalkak.backend.photo.service.ImageUrlProvider;
import com.chalkak.backend.post.domain.Post;
import com.chalkak.backend.post.domain.PostImageUpload;
import com.chalkak.backend.support.IntegrationTestSupport;
import com.chalkak.backend.topic.domain.ParticipationPeriod;
import com.chalkak.backend.topic.domain.Topic;
import com.chalkak.backend.user.domain.SignatureStorageKeys;
import com.chalkak.backend.user.domain.User;
import com.chalkak.backend.user.domain.UserFixture;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class PostQueryPersistenceTest extends IntegrationTestSupport {

    private static final LocalDate TOPIC_DATE = LocalDate.of(
            2026,
            8,
            12
    );

    private static final String SIGNATURE_STORAGE_KEY = "chalkak/dev/signatures/signature.png";

    private static final String SIGNATURE_IMAGE_URL = "https://cdn.example.com/dev/signatures/signature.png";

    @Autowired
    private PostQueryService postQueryService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @MockitoBean
    private ImageUrlProvider imageUrlProvider;

    private User author;
    private Topic topic;
    private UUID postId;
    private UUID secondUserId;

    @BeforeEach
    void setUp() {
        author = User.create(
                "post-service@example.com",
                new SignatureStorageKeys(SIGNATURE_STORAGE_KEY,
                        "chalkak/dev/signatures/signature-thumbnail.png"));
        Instant startsAt = Instant.parse("2026-08-12T00:00:00Z");
        topic = Topic.create(
                "오늘 가장 기억에 남은 순간",
                TOPIC_DATE,
                new ParticipationPeriod(startsAt, startsAt.plusSeconds(86400)),
                startsAt.minusSeconds(1)
        );
        entityManager.persist(author);
        entityManager.persist(topic);
        postId = persistApprovedPost(author);
        entityManager.clear();
    }

    @Test
    @DisplayName("사인을 교체해도 피드·상세의 작성자 ID는 유지하고 현재 사인만 바뀐다")
    void getPostsAndGetPost_changedSignature_preserveAuthorIdAndShowCurrentSignature() {
        // Given
        insertSecondVisiblePost();
        String newSignatureKey = "chalkak/dev/signatures/new-signature.png";
        String newSignatureUrl = "https://cdn.example.com/dev/signatures/new-signature.png";
        given(imageUrlProvider.getUrl(SIGNATURE_STORAGE_KEY)).willReturn(SIGNATURE_IMAGE_URL);
        given(imageUrlProvider.getUrl(newSignatureKey)).willReturn(newSignatureUrl);
        PostListResult beforeFeed = postQueryService.getPosts(
                TOPIC_DATE,
                PostSort.RECENT,
                null,
                1,
                20,
                Optional.of(secondUserId)
        );
        PostDetail beforeDetail = postQueryService.getPost(postId, secondUserId);
        jdbcTemplate.update(
                """
                        UPDATE users
                        SET signature_original_storage_key = ?, signature_thumbnail_storage_key = ?
                        WHERE id = ?
                        """,
                newSignatureKey,
                "chalkak/dev/signatures/new-signature-thumbnail.png",
                author.getId()
        );
        entityManager.flush();
        entityManager.clear();

        // When
        PostListResult afterFeed = postQueryService.getPosts(
                TOPIC_DATE,
                PostSort.RECENT,
                null,
                1,
                20,
                Optional.of(secondUserId)
        );
        PostDetail afterDetail = postQueryService.getPost(postId, secondUserId);

        // Then
        assertThat(beforeFeed.posts()).extracting(
                PostListResult.PostSummary::id,
                PostListResult.PostSummary::authorId,
                PostListResult.PostSummary::signatureOriginalImageUrl
        )
                .contains(tuple(
                        postId,
                        author.getId(),
                        SIGNATURE_IMAGE_URL
                ));
        assertThat(afterFeed.posts()).extracting(
                PostListResult.PostSummary::id,
                PostListResult.PostSummary::authorId,
                PostListResult.PostSummary::signatureOriginalImageUrl
        )
                .contains(tuple(
                        postId,
                        author.getId(),
                        newSignatureUrl
                ));
        assertThat(beforeDetail.authorId()).isEqualTo(author.getId());
        assertThat(afterDetail.authorId()).isEqualTo(author.getId()).isNotEqualTo(secondUserId);
        assertThat(beforeDetail.signatureOriginalImageUrl()).isEqualTo(SIGNATURE_IMAGE_URL);
        assertThat(afterDetail.signatureOriginalImageUrl()).isEqualTo(newSignatureUrl);
    }

    @Test
    @DisplayName("진입 게시물이 삭제돼도 피드에서 받은 작성자 ID로 공개 사진 목록을 조회한다")
    void getAuthorPosts_deletedEntryPost_keepsAuthorLookupAvailable() {
        // Given
        insertSecondVisiblePost();
        PostListResult feed = postQueryService.getPosts(
                TOPIC_DATE,
                PostSort.RECENT,
                null,
                1,
                20,
                Optional.empty()
        );
        UUID authorId = feed.posts().stream().filter(post -> post.id().equals(postId))
                .findFirst().orElseThrow().authorId();
        jdbcTemplate.update("UPDATE posts SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?",
                postId);
        entityManager.flush();
        entityManager.clear();

        // When
        AuthorPostListResult result = postQueryService.getAuthorPosts(
                authorId,
                1,
                20,
                secondUserId
        );

        // Then
        assertThat(authorId).isEqualTo(author.getId());
        assertThat(result.posts()).isEmpty();
        assertThat(result.currentPage()).isEqualTo(1);
        assertThat(result.hasNext()).isFalse();
    }

    @Test
    @DisplayName("작성자 본인은 승인 대기 게시물의 현재 인스타그램 동의를 상세에서 읽고 다른 사용자는 찾지 못한다")
    void getPost_pendingPost_visibleOnlyToAuthorWithConsent() {
        // Given
        insertSecondVisiblePost();
        jdbcTemplate.update(
                """
                        UPDATE posts
                        SET moderation_status = 'PENDING'::moderation_status,
                            instagram_upload_agreed = TRUE,
                            instagram_id = 'chalkak'
                        WHERE id = ?
                        """,
                postId
        );
        entityManager.flush();
        entityManager.clear();
        given(imageUrlProvider.getUrl(anyString())).willReturn(SIGNATURE_IMAGE_URL);

        // When
        PostDetail authorDetail = postQueryService.getPost(postId, author.getId());
        NotFoundException otherViewerException = catchThrowableOfType(
                NotFoundException.class,
                () -> postQueryService.getPost(postId, secondUserId)
        );

        // Then
        assertThat(authorDetail.instagramConsent())
                .isEqualTo(new PostDetail.InstagramConsentDetail(
                        true,
                        "chalkak"
                ));
        assertThat(otherViewerException).hasMessage("게시물을 찾을 수 없습니다.");
    }

    @ParameterizedTest
    @CsvSource({"false, false, 3", "true, false, 5", "false, true, 2", "true, true, 3"})
    @DisplayName("피드는 비로그인 상태 조회와 빈 페이지의 좋아요 쿼리를 생략한다")
    void getPosts_viewerAndEmptyPage_skipsUnneededLikeQueries(
            boolean authenticated,
            boolean empty,
            long expectedQueries
    ) {
        // Given
        insertPostLike();
        if (empty) {
            jdbcTemplate.update("UPDATE posts SET moderation_status = 'REJECTED' WHERE id = ?",
                    postId);
        }
        entityManager.flush();
        entityManager.clear();
        Statistics statistics = entityManager.getEntityManagerFactory().unwrap(SessionFactory.class)
                .getStatistics();
        boolean wasEnabled = statistics.isStatisticsEnabled();
        statistics.setStatisticsEnabled(true);
        long beforeCount = statistics.getPrepareStatementCount();

        // When & Then
        try {
            PostListResult result = postQueryService.getPosts(
                    TOPIC_DATE,
                    PostSort.RECENT,
                    null,
                    1,
                    20,
                    authenticated ? Optional.of(author.getId()) : Optional.empty()
            );
            assertThat(result.posts()).hasSize(empty ? 0 : 1).allSatisfy(post -> {
                assertThat(post.likeCount()).isEqualTo(1L);
                assertThat(post.isLiked()).isEqualTo(authenticated);
            });
            assertThat(statistics.getPrepareStatementCount() - beforeCount)
                    .isEqualTo(expectedQueries);
        } finally {
            statistics.setStatisticsEnabled(wasEnabled);
        }
    }

    private void insertPostLike() {
        jdbcTemplate.update(
                "INSERT INTO post_likes (post_id, user_id) VALUES (?, ?)",
                postId,
                author.getId()
        );
        synchronizeFixtureCounts(jdbcTemplate);
    }

    private void insertSecondVisiblePost() {
        User other = UserFixture.create();
        entityManager.persist(other);
        persistApprovedPost(other);
        secondUserId = other.getId();
        entityManager.clear();
    }

    private UUID persistApprovedPost(User user) {
        PostImageUpload upload = PostImageUpload.createPostImageUpload(user, Instant.now());
        upload.completeProcessing(Map.of());
        upload.claim(Instant.now());
        entityManager.persist(upload);
        Photo photo = Photo.createPhoto("chalkak/posts/" + user.getId() + ".webp");
        photo.completeProcessing("chalkak/posts/" + user.getId() + "-thumbnail.webp", null);
        entityManager.persist(photo);
        Post post = Post.createPost(
                user,
                topic,
                photo,
                upload.getId(),
                "오늘의 순간",
                null,
                false,
                null,
                Instant.now()
        );
        post.requestModeration();
        post.approve(Instant.now());
        entityManager.persist(post);
        entityManager.flush();
        synchronizeFixtureCounts(jdbcTemplate);
        return post.getId();
    }

}
