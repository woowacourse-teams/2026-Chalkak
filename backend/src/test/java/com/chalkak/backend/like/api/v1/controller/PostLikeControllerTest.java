package com.chalkak.backend.like.api.v1.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.exception.GlobalExceptionHandler;
import com.chalkak.backend.exception.NotFoundException;
import com.chalkak.backend.like.service.PostLikeResult;
import com.chalkak.backend.like.service.PostLikeService;
import com.chalkak.backend.support.WithMockLoginUser;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PostLikeController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class PostLikeControllerTest {

    private static final String USER_ID_VALUE = "0198f6c1-62ba-7d30-8b12-0f733b6570a1";
    private static final UUID USER_ID = UUID.fromString(USER_ID_VALUE);
    private static final UUID POST_ID = UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6570d4");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PostLikeService postLikeService;

    @Test
    @WithMockLoginUser(USER_ID_VALUE)
    @DisplayName("게시물 좋아요를 등록하고 현재 상태를 반환한다")
    void likePost_validRequest_returnsLikedResponse() throws Exception {
        // Given
        given(postLikeService.likePost(POST_ID, USER_ID))
                .willReturn(new PostLikeResult(POST_ID, 43L, true));

        // When & Then
        mockMvc.perform(put("/api/v1/posts/{postId}/likes", POST_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.postId").value(POST_ID.toString()))
                .andExpect(jsonPath("$.likeCount").value(43L))
                .andExpect(jsonPath("$.isLiked").value(true));

        verify(postLikeService).likePost(POST_ID, USER_ID);
    }

    @Test
    @WithMockLoginUser(USER_ID_VALUE)
    @DisplayName("게시물 좋아요를 취소하고 현재 상태를 반환한다")
    void unlikePost_validRequest_returnsUnlikedResponse() throws Exception {
        // Given
        given(postLikeService.unlikePost(POST_ID, USER_ID))
                .willReturn(new PostLikeResult(POST_ID, 42L, false));

        // When & Then
        mockMvc.perform(delete("/api/v1/posts/{postId}/likes", POST_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.postId").value(POST_ID.toString()))
                .andExpect(jsonPath("$.likeCount").value(42L))
                .andExpect(jsonPath("$.isLiked").value(false));

        verify(postLikeService).unlikePost(POST_ID, USER_ID);
    }

    @Test
    @WithMockLoginUser(USER_ID_VALUE)
    @DisplayName("게시물 ID 형식이 올바르지 않으면 400을 반환한다")
    void likePost_invalidPostId_returnsBadRequest() throws Exception {
        // When & Then
        mockMvc.perform(put("/api/v1/posts/{postId}/likes", "invalid-post-id"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_ERROR"))
                .andExpect(jsonPath("$.message").value("ID 형식이 올바르지 않습니다."));

        verify(postLikeService, never()).likePost(any(), any());
    }

    @Test
    @DisplayName("인증 정보가 없으면 401을 반환한다")
    void likePost_unauthenticated_returnsUnauthorized() throws Exception {
        // When & Then
        mockMvc.perform(put("/api/v1/posts/{postId}/likes", POST_ID))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("유효하지 않은 인증 정보입니다."));

        verify(postLikeService, never()).likePost(any(), any());
    }

    @Test
    @WithMockLoginUser(USER_ID_VALUE)
    @DisplayName("공개 가능한 게시물이 아니면 404를 반환한다")
    void likePost_invisiblePost_returnsNotFound() throws Exception {
        // Given
        given(postLikeService.likePost(POST_ID, USER_ID))
                .willThrow(new NotFoundException(
                        ErrorCode.BUSINESS_ERROR,
                        "게시물을 찾을 수 없습니다."
                ));

        // When & Then
        mockMvc.perform(put("/api/v1/posts/{postId}/likes", POST_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_ERROR"))
                .andExpect(jsonPath("$.message").value("게시물을 찾을 수 없습니다."));
    }

}
