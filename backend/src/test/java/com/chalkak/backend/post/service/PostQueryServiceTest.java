package com.chalkak.backend.post.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.exception.NotFoundException;
import com.chalkak.backend.exception.UnauthorizedException;
import com.chalkak.backend.photo.service.ImageUrlProvider;
import com.chalkak.backend.post.domain.Post;
import com.chalkak.backend.post.repository.PostRepository;
import com.chalkak.backend.post.repository.PostSlice;
import com.chalkak.backend.topic.domain.Topic;
import com.chalkak.backend.topic.domain.ParticipationPeriod;
import com.chalkak.backend.topic.repository.TopicRepository;
import com.chalkak.backend.user.domain.User;
import com.chalkak.backend.user.domain.UserFixture;
import com.chalkak.backend.user.domain.SignatureStorageKeys;
import com.chalkak.backend.user.repository.UserRepository;
import com.chalkak.backend.photo.domain.Photo;
import com.chalkak.backend.like.repository.PostLikeRepository;
import com.chalkak.backend.like.repository.PostLikeCounterRepository;
import com.chalkak.backend.like.repository.PostLikeCount;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PostQueryServiceTest {

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

    @InjectMocks
    private PostQueryService postQueryService;

    @Mock
    private PostRepository postRepository;
    @Mock
    private TopicRepository topicRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private PostLikeRepository postLikeRepository;
    @Mock
    private PostLikeCounterRepository postLikeCounterRepository;
    @Mock
    private ImageUrlProvider imageUrlProvider;
    @Mock
    private RandomSeedGenerator randomSeedGenerator;

    private Topic topic;
    private Post post;
    private Post secondPost;

    @BeforeEach
    void setUp() {
        Instant startsAt = Instant.parse("2026-08-12T00:00:00Z");
        topic = Topic.create(
                "오늘 가장 기억에 남은 순간",
                TOPIC_DATE,
                new ParticipationPeriod(startsAt, startsAt.plusSeconds(86400)),
                startsAt.minusSeconds(1)
        );
        ReflectionTestUtils.setField(
                topic,
                "id",
                TOPIC_ID
        );
        post = createPost(
                POST_ID,
                USER_ID,
                ORIGINAL_STORAGE_KEY,
                THUMBNAIL_STORAGE_KEY,
                SIGNATURE_STORAGE_KEY,
                SIGNATURE_THUMBNAIL_STORAGE_KEY,
                "오늘의 순간"
        );
        secondPost = createPost(
                SECOND_POST_ID,
                SECOND_USER_ID,
                "chalkak/dev/posts/original-2.jpg",
                "chalkak/dev/posts/thumbnail-2.jpg",
                "chalkak/dev/signatures/signature-2.png",
                "chalkak/dev/signatures/signature-thumbnail-2.png",
                "두 번째 순간"
        );
    }

    @Test
    @DisplayName("특정 날짜의 공개 게시물을 최신순으로 조회한다")
    void getPosts_recentSort_returnsPostList() {
        // Given
        givenFeed(
                PostSort.RECENT,
                null,
                Optional.empty(),
                List.of(post)
        );
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
        givenFeed(
                PostSort.RECENT,
                null,
                Optional.of(USER_ID),
                List.of(post),
                List.of(new PostLikeCount(POST_ID, 1L))
        );
        given(postLikeRepository.findLikedPostIds(List.of(POST_ID), USER_ID))
                .willReturn(Set.of(POST_ID));

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
        givenFeed(
                PostSort.RECENT,
                null,
                Optional.empty(),
                List.of(post),
                List.of(new PostLikeCount(POST_ID, 1L))
        );

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
        givenFeed(
                PostSort.RECENT,
                null,
                Optional.of(USER_ID),
                List.of(post, secondPost)
        );

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
        givenFeed(
                PostSort.RECENT,
                null,
                Optional.empty(),
                List.of(post, secondPost)
        );

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
        givenFeed(
                PostSort.RANDOM,
                "f4c3a091",
                Optional.empty(),
                List.of(post)
        );

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
        // Given
        givenFeed(
                PostSort.RANDOM,
                "f4c3a091",
                Optional.empty(),
                List.of(post)
        );

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
        givenFeed(
                PostSort.POPULAR,
                null,
                Optional.empty(),
                List.of(post, secondPost),
                List.of(new PostLikeCount(POST_ID, 2L), new PostLikeCount(SECOND_POST_ID, 0L))
        );
        given(postLikeCounterRepository.getCountsByPostIds(List.of(POST_ID, SECOND_POST_ID)))
                .willReturn(List.of(new PostLikeCount(POST_ID, 2L),
                        new PostLikeCount(SECOND_POST_ID, 0L)));

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
        givenFeed(
                PostSort.RECENT,
                null,
                Optional.empty(),
                List.of()
        );

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
        LocalDate unknownTopicDate = TOPIC_DATE.minusDays(1);
        given(topicRepository.findActiveByTopicDate(unknownTopicDate)).willReturn(Optional.empty());

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
        given(userRepository.findActiveById(USER_ID))
                .willReturn(Optional.of(UserFixture.create(USER_ID)));
        given(postRepository.findVisibleById(POST_ID)).willReturn(Optional.of(post));
        given(postLikeCounterRepository.getCountByPostId(POST_ID)).willReturn(1L);
        given(postLikeRepository.existsByPostIdAndUserId(POST_ID, USER_ID)).willReturn(true);
        given(imageUrlProvider.getUrl(ORIGINAL_STORAGE_KEY)).willReturn(ORIGINAL_IMAGE_URL);
        given(imageUrlProvider.getUrl(THUMBNAIL_STORAGE_KEY)).willReturn(THUMBNAIL_IMAGE_URL);
        given(imageUrlProvider.getUrl(SIGNATURE_STORAGE_KEY)).willReturn(SIGNATURE_IMAGE_URL);

        // When

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
        // Given
        given(userRepository.findActiveById(USER_ID))
                .willReturn(Optional.of(UserFixture.create(USER_ID)));
        given(postRepository.findVisibleById(UNKNOWN_POST_ID)).willReturn(Optional.empty());

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
        // Given
        given(userRepository.findActiveById(UNKNOWN_USER_ID)).willReturn(Optional.empty());

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
        String randomSeed = sort == PostSort.RANDOM ? "author-id-test" : null;
        Optional<UUID> viewerId = authenticated ? Optional.of(USER_ID) : Optional.empty();
        givenFeed(
                sort,
                randomSeed,
                viewerId,
                List.of(post, secondPost)
        );
        Map<String, String> signatureUrls = Map.of(
                SIGNATURE_STORAGE_KEY,
                SIGNATURE_IMAGE_URL,
                "chalkak/dev/signatures/signature-2.png",
                SIGNATURE_IMAGE_URL
        );
        given(imageUrlProvider.getUrl(anyString()))
                .willAnswer(call -> signatureUrls.get(call.getArgument(0)));

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

    private void givenFeed(
            PostSort sort,
            String seed,
            Optional<UUID> viewer,
            List<Post> content
    ) {
        givenFeed(
                sort,
                seed,
                viewer,
                content,
                content.stream().map(item -> new PostLikeCount(item.getId(), 0L)).toList()
        );
    }

    private void givenFeed(
            PostSort sort,
            String seed,
            Optional<UUID> viewer,
            List<Post> content,
            List<PostLikeCount> counts
    ) {
        given(topicRepository.findActiveByTopicDate(TOPIC_DATE)).willReturn(Optional.of(topic));
        viewer.ifPresent(id -> given(userRepository.findActiveById(id))
                .willReturn(Optional.of(UserFixture.create(id))));
        if (!content.isEmpty()) {
            List<UUID> ids = content.stream().map(Post::getId).toList();
            given(postLikeCounterRepository.getCountsByPostIds(ids)).willReturn(counts);
        }
        PostSlice slice = new PostSlice(content, false);
        switch (sort) {
            case RECENT -> given(postRepository.findVisibleRecentByTopicId(
                    TOPIC_ID,
                    0,
                    20
            )).willReturn(slice);
            case POPULAR -> given(postRepository.findVisiblePopularByTopicId(
                    TOPIC_ID,
                    0,
                    20
            )).willReturn(slice);
            case RANDOM -> given(postRepository.findVisibleRandomByTopicId(
                    TOPIC_ID,
                    seed,
                    0,
                    20
            )).willReturn(slice);
        }
    }

    private Post createPost(
            UUID id,
            UUID authorId,
            String originalKey,
            String thumbnailKey,
            String signatureKey,
            String signatureThumbnailKey,
            String title
    ) {
        User author = User.create(null,
                new SignatureStorageKeys(signatureKey, signatureThumbnailKey));
        ReflectionTestUtils.setField(
                author,
                "id",
                authorId
        );
        Photo photo = Photo.createPhoto(originalKey);
        photo.completeProcessing(thumbnailKey, null);
        Post created = Post.createPost(
                author,
                topic,
                photo,
                id,
                title
        );
        ReflectionTestUtils.setField(
                created,
                "id",
                id
        );
        ReflectionTestUtils.setField(
                created,
                "createdAt",
                Instant.parse("2026-08-12T03:30:00Z")
        );
        created.requestModeration();
        created.approve(Instant.parse("2026-08-12T03:30:00Z"));
        return created;
    }
}
