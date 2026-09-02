package com.chalkak.backend.post.api.v1.controller;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.exception.GlobalExceptionHandler;
import com.chalkak.backend.exception.NotFoundException;
import com.chalkak.backend.post.domain.ModerationStatus;
import com.chalkak.backend.post.service.PostCalendarResult;
import com.chalkak.backend.post.service.PostCommandService;
import com.chalkak.backend.post.service.PostCreationResult;
import com.chalkak.backend.post.service.PostDetail;
import com.chalkak.backend.post.service.PostImageUploadResult;
import com.chalkak.backend.post.service.PostListResult;
import com.chalkak.backend.post.service.PostQueryService;
import com.chalkak.backend.post.service.PostSort;
import com.chalkak.backend.post.service.PostUpdateResult;
import com.chalkak.backend.support.WithMockLoginUser;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PostController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class PostControllerTest {

    private static final String USER_ID_VALUE =
            "0198f6c1-62ba-7d30-8b12-0f733b6570a1";
    private static final UUID USER_ID = UUID.fromString(USER_ID_VALUE);
    private static final UUID POST_ID = UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6570d4");
    private static final UUID TOPIC_ID = UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6570b2");
    private static final UUID PHOTO_UPLOAD_ID =
            UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6570c3");
    private static final UUID UPLOAD_ID =
            UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6570d5");
    private static final String UPLOAD_URL =
            "https://test-bucket.s3.ap-northeast-2.amazonaws.com/presigned";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PostCommandService postCommandService;

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
    @WithMockLoginUser(USER_ID_VALUE)
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
                        .queryParam("topicDate", "2026-08-12"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.posts[0].likeCount").value(43L))
                .andExpect(jsonPath("$.posts[0].isLiked").value(true));
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
    @WithMockLoginUser(USER_ID_VALUE)
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
        mockMvc.perform(get("/api/v1/posts/{postId}", POST_ID))
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
    @DisplayName("게시물 상세 조회에 인증 정보가 없으면 401을 반환한다")
    void getPost_unauthenticated_returnsUnauthorized() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/v1/posts/{postId}", POST_ID))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("유효하지 않은 인증 정보입니다."));

        then(postQueryService).shouldHaveNoInteractions();
    }

    @Test
    @WithMockLoginUser(USER_ID_VALUE)
    @DisplayName("ID 형식이 올바르지 않으면 400 응답을 반환한다")
    void getPost_invalidPostId_returnsBadRequest() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/v1/posts/{postId}", "invalid-post-id"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_ERROR"))
                .andExpect(jsonPath("$.message").value("ID 형식이 올바르지 않습니다."));
        then(postQueryService).shouldHaveNoInteractions();
    }

    @Test
    @WithMockLoginUser(USER_ID_VALUE)
    @DisplayName("표준 형식이 아닌 UUID이면 400 응답을 반환한다")
    void getPost_nonCanonicalUuid_returnsBadRequest() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/v1/posts/{postId}", "1-1-1-1-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_ERROR"))
                .andExpect(jsonPath("$.message").value("ID 형식이 올바르지 않습니다."));
        then(postQueryService).shouldHaveNoInteractions();
    }

    @Test
    @WithMockLoginUser(USER_ID_VALUE)
    @DisplayName("공개 가능한 게시물이 없으면 404 응답을 반환한다")
    void getPost_invisiblePost_returnsNotFound() throws Exception {
        // Given
        given(postQueryService.getPost(POST_ID, USER_ID)).willThrow(new NotFoundException(
                ErrorCode.BUSINESS_ERROR,
                "게시물을 찾을 수 없습니다."
        ));

        // When & Then
        mockMvc.perform(get("/api/v1/posts/{postId}", POST_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_ERROR"))
                .andExpect(jsonPath("$.message").value("게시물을 찾을 수 없습니다."));
    }

    @Test
    @WithMockLoginUser(USER_ID_VALUE)
    @DisplayName("인증된 사용자가 업로드 URL을 발급받으면 200과 발급 정보를 반환한다")
    void createPostImageUpload_authenticatedUser_returnsIssuedUpload() throws Exception {
        // Given
        given(postCommandService.createPostImageUpload(USER_ID)).willReturn(
                new PostImageUploadResult(
                        UPLOAD_ID,
                        UPLOAD_URL,
                        300L,
                        "image/webp",
                        5_242_880L
                )
        );

        // When & Then
        mockMvc.perform(post("/api/v1/posts/uploads"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uploadId").value(UPLOAD_ID.toString()))
                .andExpect(jsonPath("$.uploadUrl").value(UPLOAD_URL))
                .andExpect(jsonPath("$.expiresInSeconds").value(300))
                .andExpect(jsonPath("$.contentType").value("image/webp"))
                .andExpect(jsonPath("$.maxBytes").value(5_242_880L));

        then(postCommandService).should().createPostImageUpload(USER_ID);
    }

    @Test
    @DisplayName("인증 정보가 없으면 업로드 URL을 발급하지 않고 401을 반환한다")
    void createPostImageUpload_unauthenticated_returnsUnauthorized() throws Exception {
        // When & Then
        mockMvc.perform(post("/api/v1/posts/uploads"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));

        then(postCommandService).shouldHaveNoInteractions();
    }

    @Test
    @WithMockLoginUser(USER_ID_VALUE)
    @DisplayName("인증된 사용자가 게시물을 생성하면 201과 생성 정보를 반환한다")
    void createPost_validRequest_returnsCreatedPost() throws Exception {
        // Given
        given(postCommandService.createPost(
                USER_ID,
                TOPIC_ID,
                PHOTO_UPLOAD_ID,
                "오늘의 기록"
        )).willReturn(new PostCreationResult(POST_ID, ModerationStatus.VALIDATING));

        // When & Then
        mockMvc.perform(post("/api/v1/posts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "topicId": "%s",
                                  "photoUploadId": "%s",
                                  "title": "오늘의 기록"
                                }
                                """.formatted(TOPIC_ID, PHOTO_UPLOAD_ID)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.postId").value(POST_ID.toString()))
                .andExpect(jsonPath("$.moderationStatus").value("VALIDATING"));

        then(postCommandService).should().createPost(
                USER_ID,
                TOPIC_ID,
                PHOTO_UPLOAD_ID,
                "오늘의 기록"
        );
    }

    @Test
    @WithMockLoginUser(USER_ID_VALUE)
    @DisplayName("인증된 사용자가 게시물 제목을 수정하면 200과 수정 정보를 반환한다")
    void updatePost_validRequest_returnsUpdatedPost() throws Exception {
        // Given
        given(postCommandService.updatePost(USER_ID, POST_ID, "수정한 제목"))
                .willReturn(new PostUpdateResult(
                        POST_ID,
                        "수정한 제목"
                ));

        // When & Then
        mockMvc.perform(put("/api/v1/posts/{postId}", POST_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "수정한 제목"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.postId").value(POST_ID.toString()))
                .andExpect(jsonPath("$.title").value("수정한 제목"))
                .andExpect(jsonPath("$.moderationStatus").doesNotExist());

        then(postCommandService).should().updatePost(USER_ID, POST_ID, "수정한 제목");
    }

    @Test
    @WithMockLoginUser(USER_ID_VALUE)
    @DisplayName("제목에 명시적인 null을 보내면 제목 삭제 결과를 반환한다")
    void updatePost_nullTitle_returnsDeletedTitle() throws Exception {
        // Given
        given(postCommandService.updatePost(USER_ID, POST_ID, null))
                .willReturn(new PostUpdateResult(
                        POST_ID,
                        null
                ));

        // When & Then
        mockMvc.perform(put("/api/v1/posts/{postId}", POST_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": null
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.postId").value(POST_ID.toString()))
                .andExpect(jsonPath("$.title").value(nullValue()))
                .andExpect(jsonPath("$.moderationStatus").doesNotExist());

        then(postCommandService).should().updatePost(USER_ID, POST_ID, null);
    }

    @Test
    @WithMockLoginUser(USER_ID_VALUE)
    @DisplayName("제목 필드가 누락되면 400을 반환한다")
    void updatePost_missingTitle_returnsBadRequest() throws Exception {
        // When & Then
        mockMvc.perform(put("/api/v1/posts/{postId}", POST_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_ERROR"))
                .andExpect(jsonPath("$.message")
                        .value("JSON 형식이 올바르지 않거나 요청 본문이 비어 있습니다."));

        then(postCommandService).shouldHaveNoInteractions();
    }

    @Test
    @WithMockLoginUser(USER_ID_VALUE)
    @DisplayName("앞뒤 공백을 제거한 제목이 10자를 초과하면 400을 반환한다")
    void updatePost_tooLongNormalizedTitle_returnsBadRequest() throws Exception {
        // When & Then
        mockMvc.perform(put("/api/v1/posts/{postId}", POST_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "  12345678901  "
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_ERROR"))
                .andExpect(jsonPath("$.message").value("제목은 10자 이하여야 합니다."));

        then(postCommandService).shouldHaveNoInteractions();
    }

    @Test
    @WithMockLoginUser(USER_ID_VALUE)
    @DisplayName("수정할 게시물 ID 형식이 올바르지 않으면 400을 반환한다")
    void updatePost_invalidPostId_returnsBadRequest() throws Exception {
        // When & Then
        mockMvc.perform(put("/api/v1/posts/{postId}", "invalid-post-id")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "수정한 제목"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_ERROR"))
                .andExpect(jsonPath("$.message").value("ID 형식이 올바르지 않습니다."));

        then(postCommandService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("인증 정보 없이 게시물 제목을 수정하면 401을 반환한다")
    void updatePost_unauthenticated_returnsUnauthorized() throws Exception {
        // When & Then
        mockMvc.perform(put("/api/v1/posts/{postId}", POST_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "수정한 제목"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("유효하지 않은 인증 정보입니다."));

        then(postCommandService).shouldHaveNoInteractions();
    }

    @Test
    @WithMockLoginUser(USER_ID_VALUE)
    @DisplayName("인증된 사용자가 본인 게시물을 삭제하면 204를 반환한다")
    void deletePost_validRequest_returnsNoContent() throws Exception {
        // When & Then
        mockMvc.perform(delete("/api/v1/posts/{postId}", POST_ID))
                .andExpect(status().isNoContent());

        then(postCommandService).should().deletePost(USER_ID, POST_ID);
    }

    @Test
    @DisplayName("인증 정보 없이 게시물을 삭제하면 401을 반환한다")
    void deletePost_unauthenticated_returnsUnauthorized() throws Exception {
        // When & Then
        mockMvc.perform(delete("/api/v1/posts/{postId}", POST_ID))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));

        then(postCommandService).shouldHaveNoInteractions();
    }

    @Test
    @WithMockLoginUser(USER_ID_VALUE)
    @DisplayName("삭제할 게시물 ID 형식이 올바르지 않으면 400을 반환한다")
    void deletePost_invalidPostId_returnsBadRequest() throws Exception {
        // When & Then
        mockMvc.perform(delete("/api/v1/posts/{postId}", "invalid-post-id"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_ERROR"))
                .andExpect(jsonPath("$.message").value("ID 형식이 올바르지 않습니다."));

        then(postCommandService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("인증 정보가 없으면 401을 반환한다")
    void createPost_unauthenticated_returnsUnauthorized() throws Exception {
        // When & Then
        mockMvc.perform(post("/api/v1/posts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "topicId": "%s",
                                  "photoUploadId": "%s"
                                }
                                """.formatted(TOPIC_ID, PHOTO_UPLOAD_ID)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));

        then(postCommandService).shouldHaveNoInteractions();
    }

    @Test
    @WithMockLoginUser(USER_ID_VALUE)
    @DisplayName("주제 ID가 없으면 400을 반환한다")
    void createPost_missingTopicId_returnsBadRequest() throws Exception {
        // When & Then
        mockMvc.perform(post("/api/v1/posts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "photoUploadId": "%s"
                                }
                                """.formatted(PHOTO_UPLOAD_ID)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_ERROR"))
                .andExpect(jsonPath("$.message")
                        .value("주제 정보가 올바르지 않습니다."));

        then(postCommandService).shouldHaveNoInteractions();
    }

    @Test
    @WithMockLoginUser(USER_ID_VALUE)
    @DisplayName("사진 업로드 ID가 없으면 400을 반환한다")
    void createPost_missingPhotoUploadId_returnsBadRequest() throws Exception {
        // When & Then
        mockMvc.perform(post("/api/v1/posts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "topicId": "%s"
                                }
                                """.formatted(TOPIC_ID)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_ERROR"))
                .andExpect(jsonPath("$.message")
                        .value("사진 업로드 정보가 올바르지 않습니다."));

        then(postCommandService).shouldHaveNoInteractions();
    }

    @Test
    @WithMockLoginUser(USER_ID_VALUE)
    @DisplayName("제목이 10자를 초과하면 400을 반환한다")
    void createPost_tooLongTitle_returnsBadRequest() throws Exception {
        // When & Then
        mockMvc.perform(post("/api/v1/posts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "topicId": "%s",
                                  "photoUploadId": "%s",
                                  "title": "12345678901"
                                }
                                """.formatted(TOPIC_ID, PHOTO_UPLOAD_ID)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_ERROR"))
                .andExpect(jsonPath("$.message")
                        .value("제목은 10자 이하여야 합니다."));

        then(postCommandService).shouldHaveNoInteractions();
    }

    @Test
    @WithMockLoginUser(USER_ID_VALUE)
    @DisplayName("이모지 제목은 코드 포인트 기준 10자까지 서비스로 전달한다")
    void createPost_tenCodePointEmojiTitle_returnsCreatedPost() throws Exception {
        // Given
        String emojiTitle = "📸".repeat(10);
        given(postCommandService.createPost(
                USER_ID,
                TOPIC_ID,
                PHOTO_UPLOAD_ID,
                emojiTitle
        )).willReturn(new PostCreationResult(POST_ID, ModerationStatus.VALIDATING));

        // When & Then
        mockMvc.perform(post("/api/v1/posts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .content("""
                                {
                                  "topicId": "%s",
                                  "photoUploadId": "%s",
                                  "title": "%s"
                                }
                                """.formatted(TOPIC_ID, PHOTO_UPLOAD_ID, emojiTitle)))
                .andExpect(status().isCreated());

        then(postCommandService).should().createPost(
                USER_ID,
                TOPIC_ID,
                PHOTO_UPLOAD_ID,
                emojiTitle
        );
    }

    @Test
    @WithMockLoginUser(USER_ID_VALUE)
    @DisplayName("긴 유니코드 공백 제목은 제목 없음으로 처리할 수 있도록 서비스에 전달한다")
    void createPost_longBlankTitle_passesRequestValidation() throws Exception {
        // Given
        String blankTitle = "\u3000".repeat(11);
        given(postCommandService.createPost(
                USER_ID,
                TOPIC_ID,
                PHOTO_UPLOAD_ID,
                blankTitle
        )).willReturn(new PostCreationResult(POST_ID, ModerationStatus.VALIDATING));

        // When & Then
        mockMvc.perform(post("/api/v1/posts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "topicId": "%s",
                                  "photoUploadId": "%s",
                                  "title": "%s"
                                }
                                """.formatted(TOPIC_ID, PHOTO_UPLOAD_ID, blankTitle)))
                .andExpect(status().isCreated());

        then(postCommandService).should().createPost(
                USER_ID,
                TOPIC_ID,
                PHOTO_UPLOAD_ID,
                blankTitle
        );
    }

    @Test
    @WithMockLoginUser(USER_ID_VALUE)
    @DisplayName("내 게시물 캘린더 조회에 성공하면 월별 결과를 반환한다")
    void getMyPostCalendar_validRequest_returnsCalendar() throws Exception {
        // Given
        given(postQueryService.getMyPostCalendar(USER_ID, YearMonth.of(2026, 8)))
                .willReturn(new PostCalendarResult(
                        2026,
                        8,
                        List.of(
                                new PostCalendarResult.PostSummary(
                                        LocalDate.of(2026, 8, 12),
                                        POST_ID,
                                        "https://cdn.example.com/posts/approved.webp",
                                        ModerationStatus.APPROVED
                                )
                        )
                ));

        // When & Then
        mockMvc.perform(get("/api/v1/posts/calendar")
                        .param("year", "2026")
                        .param("month", "8"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.year").value(2026))
                .andExpect(jsonPath("$.month").value(8))
                .andExpect(jsonPath("$.results").doesNotExist())
                .andExpect(jsonPath("$.posts[0].topicDate").value("2026-08-12"))
                .andExpect(jsonPath("$.posts[0].postId").value(POST_ID.toString()))
                .andExpect(jsonPath("$.posts[0].thumbnailImageUrl")
                        .value("https://cdn.example.com/posts/approved.webp"))
                .andExpect(jsonPath("$.posts[0].status").value("APPROVED"))
                .andExpect(jsonPath("$.posts.length()").value(1));
    }

    @Test
    @WithMockLoginUser(USER_ID_VALUE)
    @DisplayName("게시물이 없으면 빈 캘린더 결과를 반환한다")
    void getMyPostCalendar_noPosts_returnsEmptyResults() throws Exception {
        // Given
        given(postQueryService.getMyPostCalendar(USER_ID, YearMonth.of(2026, 8)))
                .willReturn(new PostCalendarResult(2026, 8, List.of()));

        // When & Then
        mockMvc.perform(get("/api/v1/posts/calendar")
                        .param("year", "2026")
                        .param("month", "8"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.posts").isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "13"})
    @DisplayName("조회 월이 1부터 12 사이가 아니면 400을 반환한다")
    void getMyPostCalendar_invalidMonth_returnsBadRequest(String month) throws Exception {
        // When & Then
        mockMvc.perform(get("/api/v1/posts/calendar")
                        .param("year", "2026")
                        .param("month", month))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_ERROR"))
                .andExpect(jsonPath("$.message").value("조회 연월이 올바르지 않습니다."));

        verify(postQueryService, never()).getMyPostCalendar(any(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "10000"})
    @DisplayName("조회 연도가 지원 범위를 벗어나면 400을 반환한다")
    void getMyPostCalendar_invalidYear_returnsBadRequest(String year) throws Exception {
        // When & Then
        mockMvc.perform(get("/api/v1/posts/calendar")
                        .param("year", year)
                        .param("month", "8"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_ERROR"))
                .andExpect(jsonPath("$.message").value("조회 연월이 올바르지 않습니다."));

        verify(postQueryService, never()).getMyPostCalendar(any(), any());
    }

    @Test
    @WithMockLoginUser(USER_ID_VALUE)
    @DisplayName("조회 연월이 누락되면 400을 반환한다")
    void getMyPostCalendar_missingYearMonth_returnsBadRequest() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/v1/posts/calendar"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_ERROR"))
                .andExpect(jsonPath("$.message").value("조회 연월이 올바르지 않습니다."));

        verify(postQueryService, never()).getMyPostCalendar(any(), any());
    }

    @Test
    @DisplayName("인증 정보가 없으면 401을 반환한다")
    void getMyPostCalendar_unauthenticated_returnsUnauthorized() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/v1/posts/calendar")
                        .param("year", "2026")
                        .param("month", "8"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("유효하지 않은 인증 정보입니다."));

        verify(postQueryService, never()).getMyPostCalendar(any(), any());
    }
}
