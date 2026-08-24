package com.chalkak.backend.post.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.BDDMockito.given;

import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.exception.NotFoundException;
import com.chalkak.backend.photo.service.ImageUrlProvider;
import com.chalkak.backend.support.IntegrationTestSupport;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class PostServiceTest extends IntegrationTestSupport {

    private static final UUID POST_ID = UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6570d4");
    private static final UUID TOPIC_ID = UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6570b2");
    private static final UUID UNKNOWN_POST_ID = UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6570e5");

    private static final String ORIGINAL_STORAGE_KEY = "chalkak/dev/posts/original.jpg";
    private static final String THUMBNAIL_STORAGE_KEY = "chalkak/dev/posts/thumbnail.jpg";
    private static final String SIGNATURE_STORAGE_KEY = "chalkak/dev/signatures/signature.png";

    private static final String ORIGINAL_IMAGE_URL = "https://cdn.example.com/dev/posts/original.jpg";
    private static final String THUMBNAIL_IMAGE_URL = "https://cdn.example.com/dev/posts/thumbnail.jpg";
    private static final String SIGNATURE_IMAGE_URL = "https://cdn.example.com/dev/signatures/signature.png";

    @Autowired
    private PostService postService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private ImageUrlProvider imageUrlProvider;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("""
                INSERT INTO users (
                    id, email, status, signature_original_storage_key, created_at, updated_at
                ) VALUES (
                    '0198f6c1-62ba-7d30-8b12-0f733b6570a1',
                    'post-service@example.com',
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
}
