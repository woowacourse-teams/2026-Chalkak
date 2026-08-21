package com.chalkak.backend.post.api.v1.controller;

import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.post.api.v1.dto.response.PostDetailResponse;
import com.chalkak.backend.post.service.PostDetail;
import com.chalkak.backend.post.service.PostService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/posts")
public class PostController {

    private final PostService postService;

    @GetMapping("/{postId}")
    public ResponseEntity<PostDetailResponse> getPost(@PathVariable String postId) {
        PostDetail detail = postService.getPost(parsePostId(postId));

        return ResponseEntity.ok(PostDetailResponse.fromPostDetail(detail));
    }

    private UUID parsePostId(String postId) {
        try {
            UUID parsedPostId = UUID.fromString(postId);

            if (!parsedPostId.toString().equalsIgnoreCase(postId)) {
                throw new IllegalArgumentException();
            }
            return parsedPostId;
        } catch (IllegalArgumentException e) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_ERROR,
                    "게시물 ID 형식이 올바르지 않습니다."
            );
        }
    }
}
