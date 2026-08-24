package com.chalkak.backend.post.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.exception.NotFoundException;
import com.chalkak.backend.photo.service.ImageUrlProvider;
import com.chalkak.backend.support.IntegrationTestSupport;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class PostServiceTest extends IntegrationTestSupport {

    private static final UUID POST_ID = UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6570d4");
    private static final UUID TOPIC_ID = UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6570b2");
    private static final UUID UNKNOWN_POST_ID = UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6570e5");
    private static final LocalDate TOPIC_DATE = LocalDate.of(2026, 8, 12);

    private static final String ORIGINAL_STORAGE_KEY = "chalkak/dev/posts/original.jpg";
    private static final String THUMBNAIL_STORAGE_KEY = "chalkak/dev/posts/thumbnail.jpg";
    private static final String SIGNATURE_STORAGE_KEY = "chalkak/dev/signatures/signature.png";
    private static final String SIGNATURE_THUMBNAIL_STORAGE_KEY =
            "chalkak/dev/signatures/signature-thumbnail.png";

    private static final String ORIGINAL_IMAGE_URL = "https://cdn.example.com/dev/posts/original.jpg";
    private static final String THUMBNAIL_IMAGE_URL = "https://cdn.example.com/dev/posts/thumbnail.jpg";
    private static final String SIGNATURE_IMAGE_URL = "https://cdn.example.com/dev/signatures/signature.png";
    private static final String SIGNATURE_THUMBNAIL_IMAGE_URL =
            "https://cdn.example.com/dev/signatures/signature-thumbnail.png";

    @Autowired
    private PostService postService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

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
        PostListResult result = postService.getPosts(TOPIC_DATE, PostSort.RECENT, null, 1, 20);

        // Then
        assertThat(result).isEqualTo(new PostListResult(
                1,
                20,
                false,
                null,
                List.of(new PostListResult.PostSummary(
                        POST_ID,
                        ORIGINAL_IMAGE_URL,
                        THUMBNAIL_IMAGE_URL,
                        SIGNATURE_IMAGE_URL,
                        SIGNATURE_THUMBNAIL_IMAGE_URL,
                        "오늘의 순간",
                        Instant.parse("2026-08-12T03:30:00Z")
                ))
        ));
    }

    @Test
    @DisplayName("랜덤 첫 페이지에 시드가 없으면 새 시드를 생성해 반환한다")
    void getPosts_randomFirstPageWithoutSeed_generatesRandomSeed() {
        // Given
        given(randomSeedGenerator.generateRandomSeed()).willReturn("f4c3a091");

        // When
        PostListResult result = postService.getPosts(TOPIC_DATE, PostSort.RANDOM, null, 1, 20);

        // Then
        assertThat(result.randomSeed()).isEqualTo("f4c3a091");
        assertThat(result.posts()).extracting(PostListResult.PostSummary::id)
                .containsExactly(POST_ID);
    }

    @Test
    @DisplayName("랜덤 시드를 전달하면 새 시드를 생성하지 않고 기존 시드를 사용한다")
    void getPosts_randomSortWithSeed_reusesRandomSeed() {
        // When
        PostListResult result = postService.getPosts(
                TOPIC_DATE,
                PostSort.RANDOM,
                "f4c3a091",
                1,
                20
        );

        // Then
        assertThat(result.randomSeed()).isEqualTo("f4c3a091");
        then(randomSeedGenerator).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("주제는 있지만 공개 게시물이 없으면 빈 목록을 반환한다")
    void getPosts_withoutVisiblePosts_returnsEmptyList() {
        // Given
        jdbcTemplate.update(
                "UPDATE posts SET moderation_status = 'REJECTED' WHERE id = ?",
                POST_ID
        );

        // When
        PostListResult result = postService.getPosts(TOPIC_DATE, PostSort.RECENT, null, 1, 20);

        // Then
        assertThat(result.hasNext()).isFalse();
        assertThat(result.posts()).isEmpty();
    }

    @Test
    @DisplayName("해당 날짜의 주제가 없으면 찾을 수 없음 예외를 발생시킨다")
    void getPosts_unknownTopicDate_throwsNotFoundException() {
        // Given
        LocalDate unknownTopicDate = LocalDate.of(2026, 8, 11);

        // When
        NotFoundException exception = catchThrowableOfType(
                NotFoundException.class,
                () -> postService.getPosts(unknownTopicDate, PostSort.RECENT, null, 1, 20)
        );

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
                () -> postService.getPosts(TOPIC_DATE, sort, randomSeed, page, 20)
        );

        // Then
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BUSINESS_ERROR);
        assertThat(exception).hasMessage("조회 조건이 올바르지 않습니다.");
    }

    @Test
    @DisplayName("KST 기준 미래 날짜를 조회하면 잘못된 요청 예외를 발생시킨다")
    void getPosts_futureTopicDate_throwsBusinessException() {
        // Given
        LocalDate futureTopicDate = LocalDate.of(2999, 1, 1);

        // When
        BusinessException exception = catchThrowableOfType(
                BusinessException.class,
                () -> postService.getPosts(futureTopicDate, PostSort.RECENT, null, 1, 20)
        );

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
        PostDetail result = postService.getPost(POST_ID);

        // Then
        assertThat(result).isEqualTo(new PostDetail(
                POST_ID,
                new PostDetail.TopicDetail(
                        TOPIC_ID,
                        "오늘 가장 기억에 남은 순간",
                        LocalDate.of(2026, 8, 12)
                ),
                ORIGINAL_IMAGE_URL,
                THUMBNAIL_IMAGE_URL,
                SIGNATURE_IMAGE_URL,
                "오늘의 순간"
        ));
    }

    @Test
    @DisplayName("공개 가능한 게시물이 없으면 찾을 수 없음 예외를 발생시킨다")
    void getPost_invisiblePost_throwsNotFoundException() {
        // When
        NotFoundException exception = catchThrowableOfType(
                NotFoundException.class,
                () -> postService.getPost(UNKNOWN_POST_ID)
        );

        // Then
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BUSINESS_ERROR);
        assertThat(exception).hasMessage("게시물을 찾을 수 없습니다.");
    }

    private static Stream<Arguments> invalidRandomSeedCombinations() {
        return Stream.of(
                Arguments.of(PostSort.RECENT, "f4c3a091", 1),
                Arguments.of(PostSort.RANDOM, null, 2)
        );
    }
}
