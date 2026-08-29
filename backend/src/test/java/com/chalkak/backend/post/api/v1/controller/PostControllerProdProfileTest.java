package com.chalkak.backend.post.api.v1.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chalkak.backend.exception.GlobalExceptionHandler;
import com.chalkak.backend.post.service.PostCommandService;
import com.chalkak.backend.post.service.PostListResult;
import com.chalkak.backend.post.service.PostQueryService;
import com.chalkak.backend.post.service.PostSort;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PostController.class)
@Import(GlobalExceptionHandler.class)
@ActiveProfiles("prod")
class PostControllerProdProfileTest {

    private static final UUID POST_ID =
            UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6570d4");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PostCommandService postCommandService;

    @MockitoBean
    private PostQueryService postQueryService;

    @Test
    @DisplayName("운영 환경에서도 게시물 목록은 비로그인으로 조회할 수 있다")
    void getPosts_prodProfile_ignoresTemporaryUserHeader() throws Exception {
        // Given
        LocalDate topicDate = LocalDate.of(2026, 8, 12);
        PostListResult result = new PostListResult(1, 20, false, null, List.of());
        given(postQueryService.getPosts(
                topicDate,
                PostSort.RECENT,
                null,
                1,
                20,
                Optional.empty()
        )).willReturn(result);

        // When & Then
        mockMvc.perform(get("/api/v1/posts")
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .queryParam("topicDate", "2026-08-12"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.posts").isEmpty());
    }

    @Test
    @DisplayName("운영 환경에서는 임시 인증 헤더로 게시물 상세를 조회할 수 없다")
    void getPost_prodProfile_rejectsTemporaryUserHeader() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/v1/posts/{postId}", POST_ID)
                        .header("X-User-Id", UUID.randomUUID().toString()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("유효하지 않은 인증 정보입니다."));

        verify(postQueryService, never()).getPost(any(), any());
    }

    @Test
    @DisplayName("운영 환경에서는 임시 인증 헤더로 게시물을 삭제할 수 없다")
    void deletePost_prodProfile_rejectsTemporaryUserHeader() throws Exception {
        // When & Then
        mockMvc.perform(delete("/api/v1/posts/{postId}", POST_ID)
                        .header("X-User-Id", UUID.randomUUID().toString()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("유효하지 않은 인증 정보입니다."));

        verify(postCommandService, never()).deletePost(any(), any());
    }

    @Test
    @DisplayName("운영 환경에서는 임시 인증 헤더로 내 게시물 캘린더를 조회할 수 없다")
    void getMyPostCalendar_prodProfile_rejectsTemporaryUserHeader() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/v1/posts/calendar")
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .queryParam("year", "2026")
                        .queryParam("month", "8"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("유효하지 않은 인증 정보입니다."));

        verify(postQueryService, never()).getMyPostCalendar(any(), any());
    }

    @Test
    @DisplayName("운영 환경에서는 임시 인증 헤더로 업로드 URL을 발급할 수 없다")
    void createPostImageUpload_prodProfile_rejectsTemporaryUserHeader() throws Exception {
        // When & Then
        mockMvc.perform(post("/api/v1/posts/uploads")
                        .header("X-User-Id", UUID.randomUUID().toString()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("유효하지 않은 인증 정보입니다."));

        verify(postCommandService, never()).createPostImageUpload(any());
    }

    @Test
    @DisplayName("운영 환경에서는 임시 인증 헤더로 게시물을 생성할 수 없다")
    void createPost_prodProfile_rejectsTemporaryUserHeader() throws Exception {
        // When & Then
        mockMvc.perform(post("/api/v1/posts")
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "topicId": "0198f6c1-62ba-7d30-8b12-0f733b6570b2",
                                  "photoUploadId": "0198f6c1-62ba-7d30-8b12-0f733b6570c3"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("유효하지 않은 인증 정보입니다."));

        verify(postCommandService, never()).createPost(any(), any(), any(), any());
    }
}
