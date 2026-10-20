package com.chalkak.backend.aireview.api.v1.controller;

import com.chalkak.backend.aireview.api.v1.docs.PostAiReviewApiDocs;
import com.chalkak.backend.aireview.api.v1.dto.response.PostAiReviewResponse;
import com.chalkak.backend.aireview.service.PostAiReviewService;
import com.chalkak.backend.aireview.service.PostAiReviewService.ReviewResult;
import com.chalkak.backend.auth.api.support.AuthenticatedUser;
import com.chalkak.backend.auth.api.support.LoginUser;
import com.chalkak.backend.auth.api.support.RequiresExistingUser;
import com.chalkak.backend.auth.api.support.RequiresUsableUser;
import com.chalkak.backend.common.util.CanonicalUuidParser;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/posts")
public class PostAiReviewController implements PostAiReviewApiDocs {

    private final PostAiReviewService postAiReviewService;

    @Override
    @RequiresUsableUser
    @PostMapping("/{postId}/ai-review")
    public ResponseEntity<PostAiReviewResponse> requestAiReview(
            @PathVariable String postId,
            @LoginUser AuthenticatedUser loginUser
    ) {
        UUID parsedPostId = CanonicalUuidParser.parse(postId);
        ReviewResult result = postAiReviewService.request(loginUser.userId(), parsedPostId);
        HttpStatus status = result.isInProgress() ? HttpStatus.ACCEPTED : HttpStatus.OK;

        return ResponseEntity.status(status).body(PostAiReviewResponse.from(result));
    }

    @Override
    @RequiresExistingUser
    @GetMapping("/{postId}/ai-review")
    public ResponseEntity<PostAiReviewResponse> getAiReview(
            @PathVariable String postId,
            @LoginUser AuthenticatedUser loginUser
    ) {
        UUID parsedPostId = CanonicalUuidParser.parse(postId);
        ReviewResult result = postAiReviewService.get(loginUser.userId(), parsedPostId);

        return ResponseEntity.ok(PostAiReviewResponse.from(result));
    }
}
