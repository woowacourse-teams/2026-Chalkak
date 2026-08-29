package com.chalkak.backend.post.api.v1.controller;

import com.chalkak.backend.auth.api.support.AuthenticatedUser;
import com.chalkak.backend.auth.api.support.OptionalLoginUser;
import com.chalkak.backend.common.util.CanonicalUuidParser;
import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.exception.UnauthorizedException;
import com.chalkak.backend.post.api.v1.docs.PostApiDocs;
import com.chalkak.backend.post.api.v1.dto.request.PostCalendarRequest;
import com.chalkak.backend.post.api.v1.dto.request.PostCreateRequest;
import com.chalkak.backend.post.api.v1.dto.request.PostListRequest;
import com.chalkak.backend.post.api.v1.dto.response.PostCalendarResponse;
import com.chalkak.backend.post.api.v1.dto.response.PostCreateResponse;
import com.chalkak.backend.post.api.v1.dto.response.PostDetailResponse;
import com.chalkak.backend.post.api.v1.dto.response.PostImageUploadResponse;
import com.chalkak.backend.post.api.v1.dto.response.PostListResponse;
import com.chalkak.backend.post.service.PostCalendarResult;
import com.chalkak.backend.post.service.PostCommandService;
import com.chalkak.backend.post.service.PostCreationResult;
import com.chalkak.backend.post.service.PostDetail;
import com.chalkak.backend.post.service.PostImageUploadResult;
import com.chalkak.backend.post.service.PostQueryService;
import jakarta.validation.Valid;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/posts")
public class PostController implements PostApiDocs {

    private final PostCommandService postCommandService;
    private final PostQueryService postQueryService;

    @Override
    @PostMapping("/uploads")
    public ResponseEntity<PostImageUploadResponse> createPostImageUpload(
            @OptionalLoginUser Optional<AuthenticatedUser> loginUser
    ) {
        UUID userId = requireUserId(loginUser);
        PostImageUploadResult result = postCommandService.createPostImageUpload(userId);

        return ResponseEntity.ok(PostImageUploadResponse.from(result));
    }

    @Override
    @PostMapping
    public ResponseEntity<PostCreateResponse> createPost(
            @OptionalLoginUser Optional<AuthenticatedUser> loginUser,
            @Valid @RequestBody PostCreateRequest request
    ) {
        UUID userId = requireUserId(loginUser);
        PostCreationResult result = postCommandService.createPost(
                userId,
                request.topicId(),
                request.photoUploadId(),
                request.title()
        );

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(PostCreateResponse.from(result));
    }

    @DeleteMapping("/{postId}")
    public ResponseEntity<Void> deletePost(
            @PathVariable String postId,
            @OptionalLoginUser Optional<AuthenticatedUser> loginUser
    ) {
        UUID userId = requireUserId(loginUser);
        UUID parsedPostId = CanonicalUuidParser.parse(postId);
        postCommandService.deletePost(userId, parsedPostId);

        return ResponseEntity.noContent().build();
    }

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
    @GetMapping("/calendar")
    public ResponseEntity<PostCalendarResponse> getMyPostCalendar(
            @Valid @ModelAttribute PostCalendarRequest request,
            @OptionalLoginUser Optional<AuthenticatedUser> loginUser
    ) {
        UUID userId = requireUserId(loginUser);
        PostCalendarResult result = postQueryService.getMyPostCalendar(
                userId,
                request.toYearMonth()
        );

        return ResponseEntity.ok(PostCalendarResponse.from(result));
    }

    @Override
    @GetMapping("/{postId}")
    public ResponseEntity<PostDetailResponse> getPost(
            @PathVariable String postId,
            @OptionalLoginUser Optional<AuthenticatedUser> loginUser
    ) {
        UUID userId = requireUserId(loginUser);
        UUID parsedPostId = CanonicalUuidParser.parse(postId);
        PostDetail detail = postQueryService.getPost(parsedPostId, userId);

        return ResponseEntity.ok(PostDetailResponse.fromPostDetail(detail));
    }

    private UUID requireUserId(Optional<AuthenticatedUser> loginUser) {
        return loginUser
                .map(AuthenticatedUser::userId)
                .orElseThrow(() -> new UnauthorizedException(
                        ErrorCode.UNAUTHORIZED,
                        "유효하지 않은 인증 정보입니다."
                ));
    }
}
