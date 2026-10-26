package com.chalkak.backend.post.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.exception.NotFoundException;
import com.chalkak.backend.exception.UnauthorizedException;
import com.chalkak.backend.photo.service.ImageUrlProvider;
import com.chalkak.backend.support.IntegrationTestSupport;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class PostQueryServiceTest extends IntegrationTestSupport {

    private static final UUID USER_ID = UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6570a1");
    private static final UUID SECOND_USER_ID = UUID
            .fromString("0198f6c1-62ba-7d30-8b12-0f733b6570a2");
    private static final UUID POST_ID = UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6570d4");
    private static final UUID SECOND_POST_ID = UUID
            .fromString("0198f6c1-62ba-7d30-8b12-0f733b6570e5");
    private static final UUID TOPIC_ID = UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6570b2");
    private static final UUID UNKNOWN_POST_ID = UUID
            .fromString("0198f6c1-62ba-7d30-8b12-0f733b6570f7");
    private static final UUID UNKNOWN_USER_ID = UUID
            .fromString("0198f6c1-62ba-7d30-8b12-0f733b6570f6");
    private static final LocalDate TOPIC_DATE = LocalDate.of(
            2026,
            8,
            12
    );

    private static final String ORIGINAL_STORAGE_KEY = "chalkak/dev/posts/original.jpg";
    private static final String THUMBNAIL_STORAGE_KEY = "chalkak/dev/posts/thumbnail.jpg";
    private static final String SIGNATURE_STORAGE_KEY = "chalkak/dev/signatures/signature.png";
    private static final String SIGNATURE_THUMBNAIL_STORAGE_KEY = "chalkak/dev/signatures/signature-thumbnail.png";

    private static final String ORIGINAL_IMAGE_URL = "https://cdn.example.com/dev/posts/original.jpg";
    private static final String THUMBNAIL_IMAGE_URL = "https://cdn.example.com/dev/posts/thumbnail.jpg";
    private static final String SIGNATURE_IMAGE_URL = "https://cdn.example.com/dev/signatures/signature.png";
    private static final String SIGNATURE_THUMBNAIL_IMAGE_URL = "https://cdn.example.com/dev/signatures/signature-thumbnail.png";

    @Autowired
    private PostQueryService postQueryService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @MockitoBean
    private ImageUrlProvider imageUrlProvider;

    @MockitoBean
    private RandomSeedGenerator randomSeedGenerator;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("""
                INSERT INTO users (
                    id, email, status, signature_original_storage_key,
                    signature_thumbnail_storage_key, created_at, updated_at
                ) VALUES (
                    '0198f6c1-62ba-7d30-8b12-0f733b6570a1',
                    'post-service@example.com',
                    'ACTIVE',
                    'chalkak/dev/signatures/signature.png',
                    'chalkak/dev/signatures/signature-thumbnail.png',
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
                            '2026-08-12T03:30:00Z',
                            CURRENT_TIMESTAMP
                        )
                        """);
    }

    @Test
    @DisplayName("특정 날짜의 공개 게시물을 최신순으로 조회한다")
    void getPosts_recentSort_returnsPostList() {
        // Given
        given(imageUrlProvider.getUrl(ORIGINAL_STORAGE_KEY)).willReturn(ORIGINAL_IMAGE_URL);
        given(imageUrlProvider.getUrl(THUMBNAIL_STORAGE_KEY)).willReturn(THUMBNAIL_IMAGE_URL);
        given(imageUrlProvider.getUrl(SIGNATURE_STORAGE_KEY)).willReturn(SIGNATURE_IMAGE_URL);
        given(imageUrlProvider.getUrl(SIGNATURE_THUMBNAIL_STORAGE_KEY))
                .willReturn(SIGNATURE_THUMBNAIL_IMAGE_URL);

        // When
        PostListResult result = postQueryService.getPosts(
                TOPIC_DATE,
                PostSort.RECENT,
                null,
                1,
                20,
                Optional.empty()
        );

        // Then
        assertThat(result).isEqualTo(new PostListResult(
                1,
                20,
                false,
                null,
                List.of(new PostListResult.PostSummary(
                        POST_ID,
                        USER_ID,
                        ORIGINAL_IMAGE_URL,
                        THUMBNAIL_IMAGE_URL,
                        SIGNATURE_IMAGE_URL,
                        SIGNATURE_THUMBNAIL_IMAGE_URL,
                        "오늘의 순간",
                        Instant.parse("2026-08-12T03:30:00Z"),
                        0L,
                        false,
                        false
                ))
        ));
    }

    @Test
    @DisplayName("로그인 사용자의 게시물 목록에 좋아요 개수와 여부를 반환한다")
    void getPosts_authenticatedUser_returnsLikeInformation() {
        // Given
        insertPostLike();

        // When
        PostListResult result = postQueryService.getPosts(
                TOPIC_DATE,
                PostSort.RECENT,
                null,
                1,
                20,
                Optional.of(USER_ID)
        );

        // Then
        assertThat(result.posts()).singleElement().satisfies(post -> {
            assertThat(post.likeCount()).isEqualTo(1L);
            assertThat(post.isLiked()).isTrue();
        });
    }

    @Test
    @DisplayName("비로그인 사용자의 게시물 목록에는 좋아요 개수와 false 상태를 반환한다")
    void getPosts_anonymousUser_returnsLikeCountAndUnlikedStatus() {
        // Given
        insertPostLike();

        // When
        PostListResult result = postQueryService.getPosts(
                TOPIC_DATE,
                PostSort.RECENT,
                null,
                1,
                20,
                Optional.empty()
        );

        // Then
        assertThat(result.posts()).singleElement().satisfies(post -> {
            assertThat(post.likeCount()).isEqualTo(1L);
            assertThat(post.isLiked()).isFalse();
        });
    }

    @Test
    @DisplayName("로그인 사용자의 게시물 목록에서 본인 게시물만 isMine이 true다")
    void getPosts_authenticatedUser_marksOwnPostAsMine() {
        // Given
        insertSecondVisiblePost();

        // When
        PostListResult result = postQueryService.getPosts(
                TOPIC_DATE,
                PostSort.RECENT,
                null,
                1,
                20,
                Optional.of(USER_ID)
        );

        // Then
        assertThat(result.posts())
                .extracting(PostListResult.PostSummary::id, PostListResult.PostSummary::isMine)
                .containsExactlyInAnyOrder(
                        tuple(POST_ID, true),
                        tuple(SECOND_POST_ID, false));
    }

    @Test
    @DisplayName("비로그인 사용자의 게시물 목록에는 isMine이 모두 false다")
    void getPosts_anonymousUser_returnsNoOwnedPost() {
        // Given
        insertSecondVisiblePost();

        // When
        PostListResult result = postQueryService.getPosts(
                TOPIC_DATE,
                PostSort.RECENT,
                null,
                1,
                20,
                Optional.empty()
        );

        // Then
        assertThat(result.posts()).extracting(PostListResult.PostSummary::isMine)
                .containsOnly(false);
    }

    @Test
    @DisplayName("랜덤 첫 페이지에 시드가 없으면 새 시드를 생성해 반환한다")
    void getPosts_randomFirstPageWithoutSeed_generatesRandomSeed() {
        // Given
        given(randomSeedGenerator.generateRandomSeed()).willReturn("f4c3a091");

        // When
        PostListResult result = postQueryService.getPosts(
                TOPIC_DATE,
                PostSort.RANDOM,
                null,
                1,
                20,
                Optional.empty()
        );

        // Then
        assertThat(result.randomSeed()).isEqualTo("f4c3a091");
        assertThat(result.posts()).extracting(PostListResult.PostSummary::id)
                .containsExactly(POST_ID);
    }

    @Test
    @DisplayName("랜덤 시드를 전달하면 새 시드를 생성하지 않고 기존 시드를 사용한다")
    void getPosts_randomSortWithSeed_reusesRandomSeed() {
        // When
        PostListResult result = postQueryService.getPosts(
                TOPIC_DATE,
                PostSort.RANDOM,
                "f4c3a091",
                1,
                20,
                Optional.empty()
        );

        // Then
        assertThat(result.randomSeed()).isEqualTo("f4c3a091");
        then(randomSeedGenerator).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("공개 게시물을 좋아요 개수가 많은 순서로 조회한다")
    void getPosts_popularSort_returnsPopularPostList() {
        // Given
        insertSecondVisiblePost();
        jdbcTemplate.update(
                "INSERT INTO post_likes (post_id, user_id) VALUES (?, ?), (?, ?)",
                POST_ID,
                USER_ID,
                POST_ID,
                SECOND_USER_ID
        );

        // When
        PostListResult result = postQueryService.getPosts(
                TOPIC_DATE,
                PostSort.POPULAR,
                null,
                1,
                20,
                Optional.empty()
        );

        // Then
        assertThat(result.posts()).extracting(PostListResult.PostSummary::id)
                .containsExactly(POST_ID, SECOND_POST_ID);
        assertThat(result.posts()).extracting(PostListResult.PostSummary::likeCount)
                .containsExactly(2L, 0L);
        assertThat(result.randomSeed()).isNull();
    }

    @Test
    @DisplayName("주제는 있지만 공개 게시물이 없으면 빈 목록을 반환한다")
    void getPosts_withoutVisiblePosts_returnsEmptyList() {
        // Given
        jdbcTemplate.update(
                "UPDATE posts SET moderation_status = 'REJECTED' WHERE id = ?",
                POST_ID);

        // When
        PostListResult result = postQueryService.getPosts(
                TOPIC_DATE,
                PostSort.RECENT,
                null,
                1,
                20,
                Optional.empty()
        );

        // Then
        assertThat(result.hasNext()).isFalse();
        assertThat(result.posts()).isEmpty();
    }

    @Test
    @DisplayName("해당 날짜의 주제가 없으면 찾을 수 없음 예외를 발생시킨다")
    void getPosts_unknownTopicDate_throwsNotFoundException() {
        // Given
        LocalDate unknownTopicDate = LocalDate.of(
                2026,
                8,
                11
        );

        // When
        NotFoundException exception = catchThrowableOfType(
                NotFoundException.class,
                () -> postQueryService.getPosts(
                        unknownTopicDate,
                        PostSort.RECENT,
                        null,
                        1,
                        20,
                        Optional.empty()
                ));

        // Then
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BUSINESS_ERROR);
        assertThat(exception).hasMessage("해당 날짜의 주제를 찾을 수 없습니다.");
    }

    @ParameterizedTest
    @MethodSource("invalidRandomSeedCombinations")
    @DisplayName("정렬과 랜덤 시드 조합이 유효하지 않으면 잘못된 요청 예외를 발생시킨다")
    void getPosts_invalidRandomSeedCombination_throwsBusinessException(
            PostSort sort,
            String randomSeed,
            int page
    ) {
        // When
        BusinessException exception = catchThrowableOfType(
                BusinessException.class,
                () -> postQueryService.getPosts(
                        TOPIC_DATE,
                        sort,
                        randomSeed,
                        page,
                        20,
                        Optional.empty()
                ));

        // Then
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BUSINESS_ERROR);
        assertThat(exception).hasMessage("조회 조건이 올바르지 않습니다.");
    }

    @Test
    @DisplayName("KST 기준 미래 날짜를 조회하면 잘못된 요청 예외를 발생시킨다")
    void getPosts_futureTopicDate_throwsBusinessException() {
        // Given
        LocalDate futureTopicDate = LocalDate.of(
                2999,
                1,
                1
        );

        // When
        BusinessException exception = catchThrowableOfType(
                BusinessException.class,
                () -> postQueryService.getPosts(
                        futureTopicDate,
                        PostSort.RECENT,
                        null,
                        1,
                        20,
                        Optional.empty()
                ));

        // Then
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BUSINESS_ERROR);
        assertThat(exception).hasMessage("미래 날짜의 게시물은 조회할 수 없습니다.");
    }

    @Test
    @DisplayName("공개 가능한 게시물을 조회하고 이미지 URL을 조립한다")
    void getPost_visiblePost_returnsPostDetailWithImageUrls() {
        // Given
        given(imageUrlProvider.getUrl(ORIGINAL_STORAGE_KEY)).willReturn(ORIGINAL_IMAGE_URL);
        given(imageUrlProvider.getUrl(THUMBNAIL_STORAGE_KEY)).willReturn(THUMBNAIL_IMAGE_URL);
        given(imageUrlProvider.getUrl(SIGNATURE_STORAGE_KEY)).willReturn(SIGNATURE_IMAGE_URL);

        // When
        insertPostLike();

        PostDetail result = postQueryService.getPost(POST_ID, USER_ID);

        // Then
        assertThat(result).isEqualTo(new PostDetail(
                POST_ID,
                USER_ID,
                new PostDetail.TopicDetail(
                        TOPIC_ID,
                        "오늘 가장 기억에 남은 순간",
                        LocalDate.of(
                                2026,
                                8,
                                12
                        )
                ),
                ORIGINAL_IMAGE_URL,
                THUMBNAIL_IMAGE_URL,
                SIGNATURE_IMAGE_URL,
                "오늘의 순간",
                1L,
                true
        ));
    }

    @Test
    @DisplayName("공개 가능한 게시물이 없으면 찾을 수 없음 예외를 발생시킨다")
    void getPost_invisiblePost_throwsNotFoundException() {
        // When
        NotFoundException exception = catchThrowableOfType(
                NotFoundException.class,
                () -> postQueryService.getPost(UNKNOWN_POST_ID, USER_ID));

        // Then
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BUSINESS_ERROR);
        assertThat(exception).hasMessage("게시물을 찾을 수 없습니다.");
    }

    @Test
    @DisplayName("상세 조회 사용자를 찾을 수 없으면 인증 예외를 발생시킨다")
    void getPost_unknownUser_throwsUnauthorizedException() {
        // When
        UnauthorizedException exception = catchThrowableOfType(
                UnauthorizedException.class,
                () -> postQueryService.getPost(POST_ID, UNKNOWN_USER_ID));

        // Then
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED);
        assertThat(exception).hasMessage("유효하지 않은 인증 정보입니다.");
    }

    @ParameterizedTest
    @CsvSource({"RECENT, false", "RECENT, true", "POPULAR, false", "POPULAR, true",
            "RANDOM, false", "RANDOM, true"})
    @DisplayName("모든 정렬의 로그인·비로그인 피드는 사인 URL이 같아도 실제 작성자 ID를 구분한다")
    void getPosts_allSorts_returnsActualAuthorIds(PostSort sort, boolean authenticated) {
        // Given
        insertSecondVisiblePost();
        given(imageUrlProvider.getUrl(SIGNATURE_STORAGE_KEY)).willReturn(SIGNATURE_IMAGE_URL);
        given(imageUrlProvider.getUrl("chalkak/dev/signatures/signature-2.png"))
                .willReturn(SIGNATURE_IMAGE_URL);
        String randomSeed = sort == PostSort.RANDOM ? "author-id-test" : null;
        Optional<UUID> viewerId = authenticated ? Optional.of(USER_ID) : Optional.empty();

        // When
        PostListResult result = postQueryService.getPosts(
                TOPIC_DATE,
                sort,
                randomSeed,
                1,
                20,
                viewerId
        );

        // Then
        assertThat(result.posts())
                .extracting(PostListResult.PostSummary::id, PostListResult.PostSummary::authorId)
                .containsExactlyInAnyOrder(tuple(POST_ID, USER_ID),
                        tuple(SECOND_POST_ID, SECOND_USER_ID));
        assertThat(result.posts()).extracting(PostListResult.PostSummary::signatureOriginalImageUrl)
                .containsOnly(SIGNATURE_IMAGE_URL);
        assertThat(result.randomSeed()).isEqualTo(randomSeed);
        assertThat(result.currentPage()).isEqualTo(1);
        assertThat(result.pageSize()).isEqualTo(20);
        assertThat(result.hasNext()).isFalse();
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
                Optional.of(SECOND_USER_ID)
        );
        PostDetail beforeDetail = postQueryService.getPost(POST_ID, SECOND_USER_ID);
        jdbcTemplate.update(
                """
                        UPDATE users
                        SET signature_original_storage_key = ?, signature_thumbnail_storage_key = ?
                        WHERE id = ?
                        """,
                newSignatureKey,
                "chalkak/dev/signatures/new-signature-thumbnail.png",
                USER_ID
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
                Optional.of(SECOND_USER_ID)
        );
        PostDetail afterDetail = postQueryService.getPost(POST_ID, SECOND_USER_ID);

        // Then
        assertThat(beforeFeed.posts()).extracting(
                PostListResult.PostSummary::id,
                PostListResult.PostSummary::authorId,
                PostListResult.PostSummary::signatureOriginalImageUrl
        )
                .contains(tuple(
                        POST_ID,
                        USER_ID,
                        SIGNATURE_IMAGE_URL
                ));
        assertThat(afterFeed.posts()).extracting(
                PostListResult.PostSummary::id,
                PostListResult.PostSummary::authorId,
                PostListResult.PostSummary::signatureOriginalImageUrl
        )
                .contains(tuple(
                        POST_ID,
                        USER_ID,
                        newSignatureUrl
                ));
        assertThat(beforeDetail.authorId()).isEqualTo(USER_ID);
        assertThat(afterDetail.authorId()).isEqualTo(USER_ID).isNotEqualTo(SECOND_USER_ID);
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
        UUID authorId = feed.posts().stream().filter(post -> post.id().equals(POST_ID))
                .findFirst().orElseThrow().authorId();
        jdbcTemplate.update("UPDATE posts SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?",
                POST_ID);
        entityManager.flush();
        entityManager.clear();

        // When
        AuthorPostListResult result = postQueryService.getAuthorPosts(
                authorId,
                1,
                20,
                SECOND_USER_ID
        );

        // Then
        assertThat(authorId).isEqualTo(USER_ID);
        assertThat(result.posts()).isEmpty();
        assertThat(result.currentPage()).isEqualTo(1);
        assertThat(result.hasNext()).isFalse();
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
                    POST_ID);
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
                    authenticated ? Optional.of(USER_ID) : Optional.empty()
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
                POST_ID,
                USER_ID
        );
    }

    private void insertSecondVisiblePost() {
        jdbcTemplate.update("""
                INSERT INTO users (
                    id, email, status, signature_original_storage_key,
                    signature_thumbnail_storage_key, created_at, updated_at
                ) VALUES (
                    '0198f6c1-62ba-7d30-8b12-0f733b6570a2',
                    'post-service-2@example.com',
                    'ACTIVE',
                    'chalkak/dev/signatures/signature-2.png',
                    'chalkak/dev/signatures/signature-thumbnail-2.png',
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
                    id, user_id, topic_id, photo_id, title, moderation_status,
                    created_at, updated_at
                ) VALUES (
                    '0198f6c1-62ba-7d30-8b12-0f733b6570e5',
                    '0198f6c1-62ba-7d30-8b12-0f733b6570a2',
                    '0198f6c1-62ba-7d30-8b12-0f733b6570b2',
                    '0198f6c1-62ba-7d30-8b12-0f733b6570c4',
                    '두 번째 순간',
                    'APPROVED',
                    '2026-08-12T04:00:00Z',
                    CURRENT_TIMESTAMP
                )
                """);
    }

    private static Stream<Arguments> invalidRandomSeedCombinations() {
        return Stream.of(
                Arguments.of(
                        PostSort.RECENT,
                        "f4c3a091",
                        1
                ),
                Arguments.of(
                        PostSort.RANDOM,
                        null,
                        2
                ),
                Arguments.of(
                        PostSort.POPULAR,
                        "f4c3a091",
                        1
                )
        );
    }
}
