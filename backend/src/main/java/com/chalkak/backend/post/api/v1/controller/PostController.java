package com.chalkak.backend.post.api.v1.controller;

import com.chalkak.backend.common.util.CanonicalUuidParser;
import com.chalkak.backend.post.api.v1.docs.PostApiDocs;
import com.chalkak.backend.post.api.v1.dto.request.PostListRequest;
import com.chalkak.backend.post.api.v1.dto.response.PostDetailResponse;
import com.chalkak.backend.post.api.v1.dto.response.PostListResponse;
import com.chalkak.backend.post.service.PostDetail;
import com.chalkak.backend.post.service.PostQueryService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/posts")
public class PostController implements PostApiDocs {

    private final PostQueryService postQueryService;

    @Override
    @GetMapping
    public ResponseEntity<PostListResponse> getPosts(
            @Valid @ModelAttribute PostListRequest request
    ) {
        return ResponseEntity.ok(
                PostListResponse.fromPostListResult(
                        postQueryService.getPosts(
                                request.topicDate(),
                                request.sort(),
                                request.randomSeed(),
                                request.page(),
                                request.pageSize()
                        )
                )
        );
    }

    @Override
    @GetMapping("/{postId}")
    public ResponseEntity<PostDetailResponse> getPost(@PathVariable String postId) {
        UUID parsedPostId = CanonicalUuidParser.parse(postId);
        PostDetail detail = postQueryService.getPost(parsedPostId);

        return ResponseEntity.ok(PostDetailResponse.fromPostDetail(detail));
    }
}
