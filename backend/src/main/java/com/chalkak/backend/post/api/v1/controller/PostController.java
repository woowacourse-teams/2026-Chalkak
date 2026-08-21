package com.chalkak.backend.post.api.v1.controller;

import com.chalkak.backend.common.util.CanonicalUuidParser;
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
        UUID parsedPostId = CanonicalUuidParser.parse(postId);
        PostDetail detail = postService.getPost(parsedPostId);

        return ResponseEntity.ok(PostDetailResponse.fromPostDetail(detail));
    }
}
