package com.chalkak.backend.post.api.v1.controller;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.exception.GlobalExceptionHandler;
import com.chalkak.backend.exception.NotFoundException;
import com.chalkak.backend.post.service.PostDetail;
import com.chalkak.backend.post.service.PostListResult;
import com.chalkak.backend.post.service.PostQueryService;
import com.chalkak.backend.post.service.PostSort;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PostController.class)
@Import(GlobalExceptionHandler.class)
class PostControllerTest {

    private static final String USER_ID_HEADER = "X-User-Id";
    private static final UUID USER_ID = UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6570a1");
    private static final UUID POST_ID = UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6570d4");
    private static final UUID TOPIC_ID = UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6570b2");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PostQueryService postQueryService;

    @Test
    @DisplayName("특정 날짜의 게시물 목록을 기본 조회 조건으로 조회한다")
    void getPosts_validTopicDate_returnsPostList() throws Exception {
        // Given
        LocalDate topicDate = LocalDate.of(2026, 8, 12);
        PostListResult result = new PostListResult(
                1,
                20,
                true,
                null,
                List.of(new PostListResult.PostSummary(
                        POST_ID,
                        "https://cdn.example.com/dev/posts/original.jpg",
                        "https://cdn.example.com/dev/posts/thumbnail.jpg",
                        "https://cdn.example.com/dev/signatures/signature.png",
                        "https://cdn.example.com/dev/signatures/signature-thumbnail.png",
                        "오늘의 순간",
                        Instant.parse("2026-08-12T03:30:00Z"),
                        43L,
                        false
                ))
        );
        given(postQueryService.getPosts(
                topicDate,
                PostSort.RECENT,
                null,
                1,
                20,
                Optional.empty()
        ))
                .willReturn(result);

        // When & Then
        mockMvc.perform(get("/api/v1/posts")
                        .queryParam("topicDate", "2026-08-12"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").doesNotExist())
                .andExpect(jsonPath("$.currentPage").value(1))
                .andExpect(jsonPath("$.pageSize").value(20))
                .andExpect(jsonPath("$.totalPages").doesNotExist())
                .andExpect(jsonPath("$.hasNext").value(true))
                .andExpect(jsonPath("$.randomSeed").value(nullValue()))
                .andExpect(jsonPath("$.topic").doesNotExist())
                .andExpect(jsonPath("$.posts[0].id").value(POST_ID.toString()))
                .andExpect(jsonPath("$.posts[0].originalImageUrl")
                        .value("https://cdn.example.com/dev/posts/original.jpg"))
                .andExpect(jsonPath("$.posts[0].thumbnailImageUrl")
                        .value("https://cdn.example.com/dev/posts/thumbnail.jpg"))
                .andExpect(jsonPath("$.posts[0].signatureOriginalImageUrl")
                        .value("https://cdn.example.com/dev/signatures/signature.png"))
                .andExpect(jsonPath("$.posts[0].signatureThumbnailImageUrl")
                        .value("https://cdn.example.com/dev/signatures/signature-thumbnail.png"))
                .andExpect(jsonPath("$.posts[0].title").value("오늘의 순간"))
                .andExpect(jsonPath("$.posts[0].submittedAt")
                        .value("2026-08-12T03:30:00Z"))
                .andExpect(jsonPath("$.posts[0].likeCount").value(43L))
                .andExpect(jsonPath("$.posts[0].isLiked").value(false));
    }

    @Test
    @DisplayName("로그인 사용자는 게시물 목록에서 자신의 좋아요 여부를 조회한다")
    void getPosts_authenticatedUser_returnsPersonalLikeStatus() throws Exception {
        // Given
        LocalDate topicDate = LocalDate.of(2026, 8, 12);
        PostListResult result = new PostListResult(
                1,
                20,
                false,
                null,
                List.of(new PostListResult.PostSummary(
                        POST_ID,
                        "https://cdn.example.com/dev/posts/original.jpg",
                        "https://cdn.example.com/dev/posts/thumbnail.jpg",
                        "https://cdn.example.com/dev/signatures/signature.png",
                        "https://cdn.example.com/dev/signatures/signature-thumbnail.png",
                        "오늘의 순간",
                        Instant.parse("2026-08-12T03:30:00Z"),
                        43L,
                        true
                ))
        );
        given(postQueryService.getPosts(
                topicDate,
                PostSort.RECENT,
                null,
                1,
                20,
                Optional.of(USER_ID)
        )).willReturn(result);

        // When & Then
        mockMvc.perform(get("/api/v1/posts")
                        .header(USER_ID_HEADER, USER_ID.toString())
                        .queryParam("topicDate", "2026-08-12"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.posts[0].likeCount").value(43L))
                .andExpect(jsonPath("$.posts[0].isLiked").value(true));
    }

    @Test
    @DisplayName("목록 조회의 사용자 식별 헤더가 올바르지 않으면 401을 반환한다")
    void getPosts_invalidUserIdHeader_returnsUnauthorized() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/v1/posts")
                        .header(USER_ID_HEADER, "invalid-user-id")
                        .queryParam("topicDate", "2026-08-12"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("유효하지 않은 인증 정보입니다."));

        then(postQueryService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("소문자 랜덤 정렬 파라미터를 enum으로 변환한다")
    void getPosts_randomSort_bindsPostSortEnum() throws Exception {
        // Given
        LocalDate topicDate = LocalDate.of(2026, 8, 12);
        PostListResult result = new PostListResult(
                1,
                20,
                false,
                "f4c3a091",
                List.of()
        );
        given(postQueryService.getPosts(
                topicDate,
                PostSort.RANDOM,
                null,
                1,
                20,
                Optional.empty()
        ))
                .willReturn(result);

        // When & Then
        mockMvc.perform(get("/api/v1/posts")
                        .queryParam("topicDate", "2026-08-12")
                        .queryParam("sort", "random"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.randomSeed").value("f4c3a091"));
    }

    @Test
    @DisplayName("소문자 인기순 정렬 파라미터를 enum으로 변환한다")
    void getPosts_popularSort_bindsPostSortEnum() throws Exception {
        // Given
        LocalDate topicDate = LocalDate.of(2026, 8, 12);
        PostListResult result = new PostListResult(1, 20, false, null, List.of());
        given(postQueryService.getPosts(
                topicDate,
                PostSort.POPULAR,
                null,
                1,
                20,
                Optional.empty()
        )).willReturn(result);

        // When & Then
        mockMvc.perform(get("/api/v1/posts")
                        .queryParam("topicDate", "2026-08-12")
                        .queryParam("sort", "popular"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.posts").isEmpty());
    }

    @Test
    @DisplayName("랜덤 다음 페이지 조회 조건을 서비스에 전달한다")
    void getPosts_randomNextPage_passesQueryParameters() throws Exception {
        // Given
        LocalDate topicDate = LocalDate.of(2026, 8, 12);
        PostListResult result = new PostListResult(
                2,
                100,
                false,
                "f4c3a091",
                List.of()
        );
        given(postQueryService.getPosts(
                topicDate,
                PostSort.RANDOM,
                "f4c3a091",
                2,
                100,
                Optional.empty()
        )).willReturn(result);

        // When & Then
        mockMvc.perform(get("/api/v1/posts")
                        .queryParam("topicDate", "2026-08-12")
                        .queryParam("sort", "random")
                        .queryParam("randomSeed", "f4c3a091")
                        .queryParam("page", "2")
                        .queryParam("pageSize", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentPage").value(2))
                .andExpect(jsonPath("$.pageSize").value(100))
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.randomSeed").value("f4c3a091"))
                .andExpect(jsonPath("$.posts").isEmpty());
    }

    @ParameterizedTest
    @CsvSource({
            "page, 0",
            "pageSize, 0",
            "pageSize, 101"
    })
    @DisplayName("페이지 조회 조건이 범위를 벗어나면 잘못된 요청을 반환한다")
    void getPosts_invalidPagination_returnsBadRequest(
            String parameterName,
            String parameterValue
    ) throws Exception {
        // When & Then
        mockMvc.perform(get("/api/v1/posts")
                        .queryParam("topicDate", "2026-08-12")
                        .queryParam(parameterName, parameterValue))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_ERROR"));
        then(postQueryService).shouldHaveNoInteractions();
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                    "sort | unknown | sort: 요청 값의 형식이 올바르지 않습니다.",
                    "randomSeed | seed! | 조회 조건이 올바르지 않습니다."
            }
    )
    @DisplayName("목록 조회 파라미터 형식이 올바르지 않으면 잘못된 요청을 반환한다")
    void getPosts_invalidQueryParameter_returnsBadRequest(
            String parameterName,
            String parameterValue,
            String expectedMessage
    ) throws Exception {
        // When & Then
        mockMvc.perform(get("/api/v1/posts")
                        .queryParam("topicDate", "2026-08-12")
                        .queryParam(parameterName, parameterValue))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_ERROR"))
                .andExpect(jsonPath("$.message").value(expectedMessage));
        then(postQueryService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("랜덤 시드가 최대 길이를 초과하면 잘못된 요청을 반환한다")
    void getPosts_tooLongRandomSeed_returnsBadRequest() throws Exception {
        // Given
        String tooLongRandomSeed = "a".repeat(65);

        // When & Then
        mockMvc.perform(get("/api/v1/posts")
                        .queryParam("topicDate", "2026-08-12")
                        .queryParam("sort", "random")
                        .queryParam("randomSeed", tooLongRandomSeed))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_ERROR"))
                .andExpect(jsonPath("$.message")
                        .value("조회 조건이 올바르지 않습니다."));
        then(postQueryService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("주제 공개일 형식이 올바르지 않으면 잘못된 요청을 반환한다")
    void getPosts_invalidTopicDate_returnsBadRequest() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/v1/posts")
                        .queryParam("topicDate", "2026-13-40"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_ERROR"))
                .andExpect(jsonPath("$.message")
                        .value("topicDate: 요청 값의 형식이 올바르지 않습니다."));
        then(postQueryService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("주제 공개일이 없으면 잘못된 요청을 반환한다")
    void getPosts_missingTopicDate_returnsBadRequest() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/v1/posts"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_ERROR"));
        then(postQueryService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("유효한 게시물 ID로 상세 게시물을 조회한다")
    void getPost_validPostId_returnsPostDetail() throws Exception {
        // Given
        PostDetail detail = new PostDetail(
                POST_ID,
                new PostDetail.TopicDetail(
                        TOPIC_ID,
                        "오늘 가장 기억에 남은 순간",
                        LocalDate.of(2026, 8, 12)
                ),
                "https://cdn.example.com/dev/posts/original.jpg",
                "https://cdn.example.com/dev/posts/thumbnail.jpg",
                "https://cdn.example.com/dev/signatures/signature.png",
                "오늘의 순간",
                43L,
                true
        );
        given(postQueryService.getPost(POST_ID, USER_ID)).willReturn(detail);

        // When & Then
        mockMvc.perform(get("/api/v1/posts/{postId}", POST_ID)
                        .header(USER_ID_HEADER, USER_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(POST_ID.toString()))
                .andExpect(jsonPath("$.topic.id").value(TOPIC_ID.toString()))
                .andExpect(jsonPath("$.topic.title").value("오늘 가장 기억에 남은 순간"))
                .andExpect(jsonPath("$.topic.topicDate").value("2026-08-12"))
                .andExpect(jsonPath("$.originalImageUrl")
                        .value("https://cdn.example.com/dev/posts/original.jpg"))
                .andExpect(jsonPath("$.thumbnailImageUrl")
                        .value("https://cdn.example.com/dev/posts/thumbnail.jpg"))
                .andExpect(jsonPath("$.signatureOriginalImageUrl")
                        .value("https://cdn.example.com/dev/signatures/signature.png"))
                .andExpect(jsonPath("$.title").value("오늘의 순간"))
                .andExpect(jsonPath("$.likeCount").value(43L))
                .andExpect(jsonPath("$.isLiked").value(true));
    }

    @Test
    @DisplayName("게시물 상세 조회에 사용자 식별 헤더가 없으면 401을 반환한다")
    void getPost_missingUserIdHeader_returnsUnauthorized() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/v1/posts/{postId}", POST_ID))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("유효하지 않은 인증 정보입니다."));

        then(postQueryService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("ID 형식이 올바르지 않으면 400 응답을 반환한다")
    void getPost_invalidPostId_returnsBadRequest() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/v1/posts/{postId}", "invalid-post-id")
                        .header(USER_ID_HEADER, USER_ID.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_ERROR"))
                .andExpect(jsonPath("$.message").value("ID 형식이 올바르지 않습니다."));
        then(postQueryService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("표준 형식이 아닌 UUID이면 400 응답을 반환한다")
    void getPost_nonCanonicalUuid_returnsBadRequest() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/v1/posts/{postId}", "1-1-1-1-1")
                        .header(USER_ID_HEADER, USER_ID.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_ERROR"))
                .andExpect(jsonPath("$.message").value("ID 형식이 올바르지 않습니다."));
        then(postQueryService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("공개 가능한 게시물이 없으면 404 응답을 반환한다")
    void getPost_invisiblePost_returnsNotFound() throws Exception {
        // Given
        given(postQueryService.getPost(POST_ID, USER_ID)).willThrow(new NotFoundException(
                ErrorCode.BUSINESS_ERROR,
                "게시물을 찾을 수 없습니다."
        ));

        // When & Then
        mockMvc.perform(get("/api/v1/posts/{postId}", POST_ID)
                        .header(USER_ID_HEADER, USER_ID.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_ERROR"))
                .andExpect(jsonPath("$.message").value("게시물을 찾을 수 없습니다."));
    }
}
