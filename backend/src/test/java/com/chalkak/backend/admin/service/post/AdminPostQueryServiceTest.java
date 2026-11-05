package com.chalkak.backend.admin.service.post;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.chalkak.backend.admin.repository.AdminPage;
import com.chalkak.backend.admin.repository.post.AdminPostDetailProjection;
import com.chalkak.backend.admin.repository.post.AdminPostInstagramConsentFilter;
import com.chalkak.backend.admin.repository.post.AdminPostQueryCriteria;
import com.chalkak.backend.admin.repository.post.AdminPostQueryRepository;
import com.chalkak.backend.admin.repository.post.AdminPostSort;
import com.chalkak.backend.admin.repository.post.AdminPostSummaryProjection;
import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.exception.NotFoundException;
import com.chalkak.backend.photo.service.ImageUrlProvider;
import com.chalkak.backend.post.domain.ModerationStatus;
import com.chalkak.backend.post.domain.PostImageUploadStatus;
import com.chalkak.backend.user.domain.UserStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminPostQueryServiceTest {

    private static final UUID SECOND_USER_ID = UUID
            .fromString("0198f6c1-62ba-7d30-8b12-0f733b658102");
    private static final UUID SECOND_TOPIC_ID = UUID
            .fromString("0198f6c1-62ba-7d30-8b12-0f733b658202");
    private static final UUID SECOND_PHOTO_ID = UUID
            .fromString("0198f6c1-62ba-7d30-8b12-0f733b658302");
    private static final UUID SECOND_POST_ID = UUID
            .fromString("0198f6c1-62ba-7d30-8b12-0f733b658402");
    private static final UUID SECOND_POST_IMAGE_UPLOAD_ID = UUID
            .fromString("0198f6c1-62ba-7d30-8b12-0f733b658501");
    private static final UUID UNKNOWN_POST_ID = UUID
            .fromString("0198f6c1-62ba-7d30-8b12-0f733b658499");

    private static final LocalDate SECOND_TOPIC_DATE = LocalDate.of(
            2026,
            8,
            13
    );
    private static final Instant SECOND_POST_CREATED_AT = Instant.parse("2026-08-13T04:30:00Z");
    private static final Instant SECOND_POST_DELETED_AT = Instant.parse("2026-08-13T06:00:00Z");

    private static final String SECOND_ORIGINAL_STORAGE_KEY = "chalkak/dev/admin-posts/original-2.webp";
    private static final String SECOND_THUMBNAIL_STORAGE_KEY = "chalkak/dev/admin-posts/thumbnail-2.webp";
    private static final String SECOND_ORIGINAL_IMAGE_URL = "https://cdn.example.com/dev/admin-posts/original-2.webp";
    private static final String SECOND_THUMBNAIL_IMAGE_URL = "https://cdn.example.com/dev/admin-posts/thumbnail-2.webp";

    @InjectMocks
    private AdminPostQueryService adminPostQueryService;

    @Mock
    private AdminPostQueryRepository repository;

    @Mock
    private ImageUrlProvider imageUrlProvider;

    @Test
    @DisplayName("필터가 없으면 내부 검증 중을 제외하고 삭제된 게시물도 최신순으로 조회한다")
    void getPosts_withoutFilters_returnsVisibleStatusesIncludingDeleted() {
        // Given
        given(repository.findPosts(
                new AdminPostQueryCriteria(
                        null,
                        null,
                        null,
                        AdminPostSort.CREATED_AT_DESC,
                        null,
                        null
                ),
                1,
                1
        )).willReturn(new AdminPage<>(
                List.of(summary()),
                1,
                1,
                true
        ));
        given(imageUrlProvider.getUrl(SECOND_ORIGINAL_STORAGE_KEY))
                .willReturn(SECOND_ORIGINAL_IMAGE_URL);
        given(imageUrlProvider.getUrl(SECOND_THUMBNAIL_STORAGE_KEY))
                .willReturn(SECOND_THUMBNAIL_IMAGE_URL);

        // When
        AdminPostListResult result = adminPostQueryService.getPosts(
                null,
                null,
                null,
                AdminPostSort.CREATED_AT_DESC,
                null,
                null,
                1,
                1
        );

        // Then
        assertThat(result.currentPage()).isEqualTo(1);
        assertThat(result.pageSize()).isEqualTo(1);
        assertThat(result.hasNext()).isTrue();
        assertThat(result.posts()).singleElement().satisfies(post -> {
            assertThat(post.postId()).isEqualTo(SECOND_POST_ID);
            assertThat(post.moderationStatus()).isEqualTo(ModerationStatus.REJECTED);
            assertThat(post.deletedAt()).isEqualTo(SECOND_POST_DELETED_AT);
            assertThat(post.likeCount()).isEqualTo(1L);
            assertThat(post.photo().originalImageUrl()).isEqualTo(SECOND_ORIGINAL_IMAGE_URL);
            assertThat(post.photo().thumbnailImageUrl())
                    .isEqualTo(SECOND_THUMBNAIL_IMAGE_URL);
        });
    }

    @Test
    @DisplayName("상태와 주제, 작성자 조건에 맞는 게시물만 조회한다")
    void getPosts_allFilters_returnsMatchingPost() {
        // Given
        given(repository.findPosts(
                new AdminPostQueryCriteria(
                        ModerationStatus.REJECTED,
                        SECOND_TOPIC_ID,
                        SECOND_USER_ID,
                        AdminPostSort.CREATED_AT_DESC,
                        null,
                        null
                ),
                1,
                20
        )).willReturn(new AdminPage<>(
                List.of(summary()),
                1,
                20,
                false
        ));

        // When
        AdminPostListResult result = adminPostQueryService.getPosts(
                ModerationStatus.REJECTED,
                SECOND_TOPIC_ID,
                SECOND_USER_ID,
                AdminPostSort.CREATED_AT_DESC,
                null,
                null,
                1,
                20
        );

        // Then
        assertThat(result.posts()).singleElement().satisfies(post -> {
            assertThat(post.postId()).isEqualTo(SECOND_POST_ID);
            assertThat(post.moderationStatus()).isEqualTo(ModerationStatus.REJECTED);
            assertThat(post.deletedAt()).isEqualTo(SECOND_POST_DELETED_AT);
        });
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                    "chalkak_user | chalkak_user",
                    "ChalKak_User | chalkak_user",
                    "@chalkak_user | chalkak_user",
                    "'  @Chalkak_User  ' | chalkak_user",
                    "@@chalkak | @chalkak"
            })
    @DisplayName("인스타그램 ID 검색어는 공백과 맨 앞 골뱅이 하나를 제거하고 소문자로 바꿔 조회한다")
    void getPosts_instagramId_normalizesKeyword(
            String rawKeyword,
            String expectedKeyword
    ) {
        // Given
        AdminPostQueryCriteria expected = new AdminPostQueryCriteria(
                null,
                null,
                null,
                AdminPostSort.CREATED_AT_DESC,
                AdminPostInstagramConsentFilter.AGREED,
                expectedKeyword
        );
        given(repository.findPosts(
                expected,
                1,
                20
        )).willReturn(new AdminPage<>(
                List.of(summary()),
                1,
                20,
                false
        ));

        // When
        AdminPostListResult result = adminPostQueryService.getPosts(
                null,
                null,
                null,
                AdminPostSort.CREATED_AT_DESC,
                AdminPostInstagramConsentFilter.AGREED,
                rawKeyword,
                1,
                20
        );

        // Then
        assertThat(result.posts()).singleElement().satisfies(post -> {
            assertThat(post.instagramUploadAgreed()).isTrue();
            assertThat(post.instagramId()).isEqualTo("chalkak_user");
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"   ", "@", " @ "})
    @DisplayName("비어 있거나 골뱅이만 있는 인스타그램 ID 검색어는 필터로 적용하지 않는다")
    void getPosts_blankInstagramId_doesNotFilter(String rawKeyword) {
        // Given
        AdminPostQueryCriteria expected = new AdminPostQueryCriteria(
                null,
                null,
                null,
                AdminPostSort.CREATED_AT_DESC,
                null,
                null
        );
        given(repository.findPosts(
                expected,
                1,
                20
        )).willReturn(new AdminPage<>(
                List.of(),
                1,
                20,
                false
        ));

        // When
        AdminPostListResult result = adminPostQueryService.getPosts(
                null,
                null,
                null,
                AdminPostSort.CREATED_AT_DESC,
                null,
                rawKeyword,
                1,
                20
        );

        // Then
        assertThat(result.posts()).isEmpty();
    }

    @Test
    @DisplayName("상세 조회는 인스타그램 동의 여부와 ID, 동의·철회 시각을 반환한다")
    void getPost_returnsInstagramConsent() {
        // Given
        given(repository.findPostById(SECOND_POST_ID)).willReturn(Optional.of(detail()));

        // When
        AdminPostDetail result = adminPostQueryService.getPost(SECOND_POST_ID);

        // Then
        assertThat(result.instagramConsent().agreed()).isTrue();
        assertThat(result.instagramConsent().instagramId()).isEqualTo("chalkak_user");
        assertThat(result.instagramConsent().agreedAt()).isEqualTo(SECOND_POST_CREATED_AT);
        assertThat(result.instagramConsent().revokedAt()).isEqualTo(SECOND_POST_DELETED_AT);
    }

    @Test
    @DisplayName("주제가 지정되면 인기순 조건으로 게시물을 조회한다")
    void getPosts_likeCountDescWithTopic_queriesPopularSort() {
        // Given
        given(repository.findPosts(
                new AdminPostQueryCriteria(
                        null,
                        SECOND_TOPIC_ID,
                        null,
                        AdminPostSort.LIKE_COUNT_DESC,
                        null,
                        null
                ),
                1,
                20
        )).willReturn(new AdminPage<>(
                List.of(summary()),
                1,
                20,
                false
        ));

        // When
        AdminPostListResult result = adminPostQueryService.getPosts(
                null,
                SECOND_TOPIC_ID,
                null,
                AdminPostSort.LIKE_COUNT_DESC,
                null,
                null,
                1,
                20
        );

        // Then
        assertThat(result.posts()).singleElement()
                .satisfies(post -> assertThat(post.postId()).isEqualTo(SECOND_POST_ID));
    }

    @Test
    @DisplayName("주제 없이 인기순으로 조회하면 잘못된 요청 예외를 발생시키고 조회하지 않는다")
    void getPosts_likeCountDescWithoutTopic_throwsBusinessException() {
        // When
        BusinessException exception = catchThrowableOfType(
                BusinessException.class,
                () -> adminPostQueryService.getPosts(
                        null,
                        null,
                        SECOND_USER_ID,
                        AdminPostSort.LIKE_COUNT_DESC,
                        null,
                        null,
                        1,
                        20
                ));

        // Then
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BUSINESS_ERROR);
        assertThat(exception.getMessage()).isEqualTo("조회 조건이 올바르지 않습니다.");
        then(repository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("내부 검증 중 상태는 관리자 게시물 조회 조건으로 요청할 수 없다")
    void getPosts_validatingStatus_throwsBusinessException() {
        // When
        BusinessException exception = catchThrowableOfType(
                BusinessException.class,
                () -> adminPostQueryService.getPosts(
                        ModerationStatus.VALIDATING,
                        null,
                        null,
                        AdminPostSort.CREATED_AT_DESC,
                        null,
                        null,
                        1,
                        20
                ));

        // Then
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BUSINESS_ERROR);
        assertThat(exception.getMessage()).isEqualTo("게시물 조회 상태가 올바르지 않습니다.");
    }

    @Test
    @DisplayName("페이지 오프셋이 정수 범위를 넘으면 잘못된 요청 예외를 발생시킨다")
    void getPosts_pageOffsetOverflow_throwsBusinessException() {
        // When
        BusinessException exception = catchThrowableOfType(
                BusinessException.class,
                () -> adminPostQueryService.getPosts(
                        null,
                        null,
                        null,
                        AdminPostSort.CREATED_AT_DESC,
                        null,
                        null,
                        Integer.MAX_VALUE,
                        100
                ));

        // Then
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BUSINESS_ERROR);
    }

    @Test
    @DisplayName("삭제된 거절 게시물도 작성자와 주제, 사진 메타데이터를 포함해 상세 조회한다")
    void getPost_deletedRejectedPost_returnsAdminDetail() {
        // Given
        given(repository.findPostById(SECOND_POST_ID)).willReturn(Optional.of(detail()));
        given(imageUrlProvider.getUrl(SECOND_ORIGINAL_STORAGE_KEY))
                .willReturn(SECOND_ORIGINAL_IMAGE_URL);
        given(imageUrlProvider.getUrl(SECOND_THUMBNAIL_STORAGE_KEY))
                .willReturn(SECOND_THUMBNAIL_IMAGE_URL);

        // When
        AdminPostDetail result = adminPostQueryService.getPost(SECOND_POST_ID);

        // Then
        assertThat(result.postId()).isEqualTo(SECOND_POST_ID);
        assertThat(result.moderationStatus()).isEqualTo(ModerationStatus.REJECTED);
        assertThat(result.deletedAt()).isEqualTo(SECOND_POST_DELETED_AT);
        assertThat(result.author().userId()).isEqualTo(SECOND_USER_ID);
        assertThat(result.topic().topicId()).isEqualTo(SECOND_TOPIC_ID);
        assertThat(result.photo().photoId()).isEqualTo(SECOND_PHOTO_ID);
        assertThat(result.photo().originalImageUrl()).isEqualTo(SECOND_ORIGINAL_IMAGE_URL);
        assertThat(result.photo().thumbnailImageUrl()).isEqualTo(SECOND_THUMBNAIL_IMAGE_URL);
        assertThat(result.photo().metadata()).containsEntry("width", 2048);
        assertThat(result.photo().metadata())
                .doesNotContainKeys(
                        "location",
                        "capturedAt",
                        "metaAttributes"
                );
        assertThat(result.imageUpload().uploadId()).isEqualTo(SECOND_POST_IMAGE_UPLOAD_ID);
        assertThat(result.imageUpload().status()).isEqualTo(PostImageUploadStatus.REJECTED);
        assertThat(result.imageUpload().rejectionReason()).isEqualTo("CORRUPTED_IMAGE");
        assertThat(result.likeCount()).isEqualTo(1L);
    }

    @Test
    @DisplayName("존재하지 않는 관리자 게시물 상세를 조회하면 찾을 수 없음 예외를 발생시킨다")
    void getPost_unknownPost_throwsNotFoundException() {
        // Given
        given(repository.findPostById(UNKNOWN_POST_ID)).willReturn(Optional.empty());

        // When
        NotFoundException exception = catchThrowableOfType(
                NotFoundException.class,
                () -> adminPostQueryService.getPost(UNKNOWN_POST_ID));

        // Then
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BUSINESS_ERROR);
    }

    private AdminPostSummaryProjection summary() {
        return new AdminPostSummaryProjection(
                SECOND_POST_ID,
                "거절 게시물",
                ModerationStatus.REJECTED,
                SECOND_TOPIC_ID,
                "두 번째 주제",
                SECOND_TOPIC_DATE,
                SECOND_USER_ID,
                "second-author@example.com",
                UserStatus.ACTIVE,
                null,
                SECOND_PHOTO_ID,
                SECOND_ORIGINAL_STORAGE_KEY,
                SECOND_THUMBNAIL_STORAGE_KEY,
                1L,
                SECOND_POST_CREATED_AT,
                null,
                SECOND_POST_DELETED_AT,
                true,
                "chalkak_user"
        );
    }

    private AdminPostDetailProjection detail() {
        return new AdminPostDetailProjection(
                SECOND_POST_ID,
                "거절 게시물",
                null,
                ModerationStatus.REJECTED,
                SECOND_POST_CREATED_AT,
                SECOND_POST_CREATED_AT,
                null,
                null,
                "검수 반려",
                SECOND_POST_DELETED_AT,
                SECOND_USER_ID,
                "second-author@example.com",
                UserStatus.ACTIVE,
                null,
                SECOND_TOPIC_ID,
                "두 번째 주제",
                SECOND_TOPIC_DATE,
                Instant.parse("2026-08-12T15:00:00Z"),
                Instant.parse("2026-08-13T15:00:00Z"),
                null,
                SECOND_PHOTO_ID,
                SECOND_ORIGINAL_STORAGE_KEY,
                SECOND_THUMBNAIL_STORAGE_KEY,
                Map.of(
                        "width",
                        2048,
                        "location",
                        "private",
                        "capturedAt",
                        "private",
                        "metaAttributes",
                        Map.of("private", true)
                ),
                SECOND_POST_CREATED_AT,
                SECOND_POST_CREATED_AT,
                null,
                SECOND_POST_IMAGE_UPLOAD_ID,
                PostImageUploadStatus.REJECTED,
                "CORRUPTED_IMAGE",
                SECOND_POST_CREATED_AT,
                SECOND_POST_CREATED_AT,
                1L,
                true,
                "chalkak_user",
                SECOND_POST_CREATED_AT,
                SECOND_POST_DELETED_AT
        );
    }
}
