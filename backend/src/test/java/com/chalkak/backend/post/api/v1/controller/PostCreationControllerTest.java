package com.chalkak.backend.post.api.v1.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chalkak.backend.exception.GlobalExceptionHandler;
import com.chalkak.backend.post.domain.ModerationStatus;
import com.chalkak.backend.post.service.PostCreationResult;
import com.chalkak.backend.post.service.PostImageUploadResult;
import com.chalkak.backend.post.service.PostService;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PostCreationController.class)
@Import(GlobalExceptionHandler.class)
class PostCreationControllerTest {

    private static final String USER_ID_HEADER = "X-User-Id";
    private static final UUID USER_ID =
            UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6570a1");
    private static final UUID TOPIC_ID =
            UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6570b2");
    private static final UUID PHOTO_UPLOAD_ID =
            UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6570c3");
    private static final UUID POST_ID =
            UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6570d4");
    private static final UUID UPLOAD_ID =
            UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6570d5");
    private static final String UPLOAD_URL =
            "https://test-bucket.s3.ap-northeast-2.amazonaws.com/presigned";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PostService postService;

    @Test
    @DisplayName("인증된 사용자가 업로드 URL을 발급받으면 200과 발급 정보를 반환한다")
    void createPostImageUpload_authenticatedUser_returnsIssuedUpload() throws Exception {
        // Given
        given(postService.createPostImageUpload(USER_ID)).willReturn(
                new PostImageUploadResult(
                        UPLOAD_ID,
                        UPLOAD_URL,
                        300L,
                        "image/webp",
                        5_242_880L
                )
        );

        // When & Then
        mockMvc.perform(post("/api/v1/posts/uploads")
                        .header(USER_ID_HEADER, USER_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uploadId").value(UPLOAD_ID.toString()))
                .andExpect(jsonPath("$.uploadUrl").value(UPLOAD_URL))
                .andExpect(jsonPath("$.expiresInSeconds").value(300))
                .andExpect(jsonPath("$.contentType").value("image/webp"))
                .andExpect(jsonPath("$.maxBytes").value(5_242_880L));

        then(postService).should().createPostImageUpload(USER_ID);
    }

    @Test
    @DisplayName("인증 헤더가 없으면 업로드 URL을 발급하지 않고 401을 반환한다")
    void createPostImageUpload_missingUserIdHeader_returnsUnauthorized() throws Exception {
        // When & Then
        mockMvc.perform(post("/api/v1/posts/uploads"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));

        then(postService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("인증 헤더가 UUID 형식이 아니면 업로드 URL을 발급하지 않고 401을 반환한다")
    void createPostImageUpload_malformedUserIdHeader_returnsUnauthorized() throws Exception {
        // When & Then
        mockMvc.perform(post("/api/v1/posts/uploads")
                        .header(USER_ID_HEADER, "not-a-uuid"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));

        then(postService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("인증된 사용자가 게시물을 생성하면 201과 생성 정보를 반환한다")
    void createPost_validRequest_returnsCreatedPost() throws Exception {
        // Given
        given(postService.createPost(
                USER_ID,
                TOPIC_ID,
                PHOTO_UPLOAD_ID,
                "오늘의 기록"
        )).willReturn(new PostCreationResult(POST_ID, ModerationStatus.VALIDATING));

        // When & Then
        mockMvc.perform(post("/api/v1/posts")
                        .header(USER_ID_HEADER, USER_ID.toString())
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

        then(postService).should().createPost(
                USER_ID,
                TOPIC_ID,
                PHOTO_UPLOAD_ID,
                "오늘의 기록"
        );
    }

    @Test
    @DisplayName("사용자 식별 헤더가 없으면 401을 반환한다")
    void createPost_missingUserIdHeader_returnsUnauthorized() throws Exception {
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

        then(postService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("주제 ID가 없으면 400을 반환한다")
    void createPost_missingTopicId_returnsBadRequest() throws Exception {
        // When & Then
        mockMvc.perform(post("/api/v1/posts")
                        .header(USER_ID_HEADER, USER_ID.toString())
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

        then(postService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("사진 업로드 ID가 없으면 400을 반환한다")
    void createPost_missingPhotoUploadId_returnsBadRequest() throws Exception {
        // When & Then
        mockMvc.perform(post("/api/v1/posts")
                        .header(USER_ID_HEADER, USER_ID.toString())
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

        then(postService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("제목이 10자를 초과하면 400을 반환한다")
    void createPost_tooLongTitle_returnsBadRequest() throws Exception {
        // When & Then
        mockMvc.perform(post("/api/v1/posts")
                        .header(USER_ID_HEADER, USER_ID.toString())
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

        then(postService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("이모지 제목은 코드 포인트 기준 10자까지 서비스로 전달한다")
    void createPost_tenCodePointEmojiTitle_returnsCreatedPost() throws Exception {
        // Given
        String emojiTitle = "📸".repeat(10);
        given(postService.createPost(
                USER_ID,
                TOPIC_ID,
                PHOTO_UPLOAD_ID,
                emojiTitle
        )).willReturn(new PostCreationResult(POST_ID, ModerationStatus.VALIDATING));

        // When & Then
        mockMvc.perform(post("/api/v1/posts")
                        .header(USER_ID_HEADER, USER_ID.toString())
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

        then(postService).should().createPost(
                USER_ID,
                TOPIC_ID,
                PHOTO_UPLOAD_ID,
                emojiTitle
        );
    }

    @Test
    @DisplayName("긴 유니코드 공백 제목은 제목 없음으로 처리할 수 있도록 서비스에 전달한다")
    void createPost_longBlankTitle_passesRequestValidation() throws Exception {
        // Given
        String blankTitle = "\u3000".repeat(11);
        given(postService.createPost(
                USER_ID,
                TOPIC_ID,
                PHOTO_UPLOAD_ID,
                blankTitle
        )).willReturn(new PostCreationResult(POST_ID, ModerationStatus.VALIDATING));

        // When & Then
        mockMvc.perform(post("/api/v1/posts")
                        .header(USER_ID_HEADER, USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "topicId": "%s",
                                  "photoUploadId": "%s",
                                  "title": "%s"
                                }
                                """.formatted(TOPIC_ID, PHOTO_UPLOAD_ID, blankTitle)))
                .andExpect(status().isCreated());

        then(postService).should().createPost(
                USER_ID,
                TOPIC_ID,
                PHOTO_UPLOAD_ID,
                blankTitle
        );
    }
}
