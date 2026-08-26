package com.chalkak.backend.post.api.v1.controller;

import com.chalkak.backend.auth.api.support.AuthenticatedUser;
import com.chalkak.backend.auth.api.support.OptionalLoginUser;
import com.chalkak.backend.common.util.CanonicalUuidParser;
import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.exception.UnauthorizedException;
import com.chalkak.backend.post.api.v1.docs.PostApiDocs;
import com.chalkak.backend.post.api.v1.dto.request.PostListRequest;
import com.chalkak.backend.post.api.v1.dto.response.PostDetailResponse;
import com.chalkak.backend.post.api.v1.dto.response.PostListResponse;
import com.chalkak.backend.post.service.PostDetail;
import com.chalkak.backend.post.service.PostQueryService;
import jakarta.validation.Valid;
import java.util.Optional;
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
            @Valid @ModelAttribute PostListRequest request,
            @OptionalLoginUser Optional<AuthenticatedUser> loginUser
    ) {
        return ResponseEntity.ok(
                PostListResponse.fromPostListResult(
                        postQueryService.getPosts(
                                request.topicDate(),
                                request.sort(),
                                request.randomSeed(),
                                request.page(),
                                request.pageSize(),
                                loginUser.map(AuthenticatedUser::userId)
                        )
                )
        );
    }

    @Override
    @GetMapping("/{postId}")
    public ResponseEntity<PostDetailResponse> getPost(
            @PathVariable String postId,
            @OptionalLoginUser Optional<AuthenticatedUser> loginUser
    ) {
        UUID userId = loginUser
                .map(AuthenticatedUser::userId)
                .orElseThrow(() -> new UnauthorizedException(
                        ErrorCode.UNAUTHORIZED,
                        "유효하지 않은 인증 정보입니다."
                ));
        UUID parsedPostId = CanonicalUuidParser.parse(postId);
        PostDetail detail = postQueryService.getPost(parsedPostId, userId);

        return ResponseEntity.ok(PostDetailResponse.fromPostDetail(detail));
    }
}
