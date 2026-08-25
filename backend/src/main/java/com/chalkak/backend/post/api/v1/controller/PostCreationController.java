package com.chalkak.backend.post.api.v1.controller;

import com.chalkak.backend.auth.api.support.AuthenticatedUser;
import com.chalkak.backend.auth.api.support.LoginUser;
import com.chalkak.backend.post.api.v1.docs.PostCreationApiDocs;
import com.chalkak.backend.post.api.v1.dto.request.PostCreateRequest;
import com.chalkak.backend.post.api.v1.dto.response.PostCreateResponse;
import com.chalkak.backend.post.api.v1.dto.response.PostImageUploadResponse;
import com.chalkak.backend.post.service.PostCreationResult;
import com.chalkak.backend.post.service.PostImageUploadResult;
import com.chalkak.backend.post.service.PostService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/posts")
@Profile("!prod")
public class PostCreationController implements PostCreationApiDocs {

    private final PostService postService;

    @Override
    @PostMapping("/uploads")
    public ResponseEntity<PostImageUploadResponse> createPostImageUpload(
            @LoginUser AuthenticatedUser loginUser
    ) {
        PostImageUploadResult result = postService.createPostImageUpload(loginUser.userId());

        return ResponseEntity.ok(PostImageUploadResponse.from(result));
    }

    @Override
    @PostMapping
    public ResponseEntity<PostCreateResponse> createPost(
            @LoginUser AuthenticatedUser loginUser,
            @Valid @RequestBody PostCreateRequest request
    ) {
        PostCreationResult result = postService.createPost(
                loginUser.userId(),
                request.topicId(),
                request.photoUploadId(),
                request.title()
        );

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(PostCreateResponse.from(result));
    }
}
