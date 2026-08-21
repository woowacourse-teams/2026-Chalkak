package com.chalkak.backend.post.api.v1.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.exception.GlobalExceptionHandler;
import com.chalkak.backend.exception.NotFoundException;
import com.chalkak.backend.post.service.PostDetail;
import com.chalkak.backend.post.service.PostService;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PostController.class)
@Import(GlobalExceptionHandler.class)
class PostControllerTest {

    private static final UUID POST_ID = UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6570d4");
    private static final UUID TOPIC_ID = UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6570b2");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PostService postService;

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
                "오늘의 순간"
        );
        given(postService.getPost(POST_ID)).willReturn(detail);

        // When & Then
        mockMvc.perform(get("/api/v1/posts/{postId}", POST_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(POST_ID.toString()))
                .andExpect(jsonPath("$.topic.id").value(TOPIC_ID.toString()))
                .andExpect(jsonPath("$.topic.title").value("오늘 가장 기억에 남은 순간"))
                .andExpect(jsonPath("$.topic.topic_date").value("2026-08-12"))
                .andExpect(jsonPath("$.original_image_url")
                        .value("https://cdn.example.com/dev/posts/original.jpg"))
                .andExpect(jsonPath("$.thumbnail_image_url")
                        .value("https://cdn.example.com/dev/posts/thumbnail.jpg"))
                .andExpect(jsonPath("$.signature_original_image_url")
                        .value("https://cdn.example.com/dev/signatures/signature.png"))
                .andExpect(jsonPath("$.description").value("오늘의 순간"));
    }

    @Test
    @DisplayName("게시물 ID 형식이 올바르지 않으면 400 응답을 반환한다")
    void getPost_invalidPostId_returnsBadRequest() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/v1/posts/{postId}", "invalid-post-id"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_ERROR"))
                .andExpect(jsonPath("$.message").value("게시물 ID 형식이 올바르지 않습니다."));
    }

    @Test
    @DisplayName("표준 형식이 아닌 UUID이면 400 응답을 반환한다")
    void getPost_nonCanonicalUuid_returnsBadRequest() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/v1/posts/{postId}", "1-1-1-1-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_ERROR"))
                .andExpect(jsonPath("$.message").value("게시물 ID 형식이 올바르지 않습니다."));
        then(postService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("공개 가능한 게시물이 없으면 404 응답을 반환한다")
    void getPost_invisiblePost_returnsNotFound() throws Exception {
        // Given
        given(postService.getPost(POST_ID)).willThrow(new NotFoundException(
                ErrorCode.BUSINESS_ERROR,
                "게시물을 찾을 수 없습니다."
        ));

        // When & Then
        mockMvc.perform(get("/api/v1/posts/{postId}", POST_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_ERROR"))
                .andExpect(jsonPath("$.message").value("게시물을 찾을 수 없습니다."));
    }
}
