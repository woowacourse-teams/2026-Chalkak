package com.chalkak.backend.like.api.v1.controller;

import com.chalkak.backend.auth.api.support.AuthenticatedUser;
import com.chalkak.backend.auth.api.support.LoginUser;
import com.chalkak.backend.common.util.CanonicalUuidParser;
import com.chalkak.backend.like.api.v1.docs.PostLikeApiDocs;
import com.chalkak.backend.like.api.v1.dto.response.PostLikeResponse;
import com.chalkak.backend.like.service.PostLikeResult;
import com.chalkak.backend.like.service.PostLikeService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/posts")
@Profile("!prod")
public class PostLikeController implements PostLikeApiDocs {

    private final PostLikeService postLikeService;

    @Override
    @PutMapping("/{postId}/likes")
    public ResponseEntity<PostLikeResponse> likePost(
            @PathVariable String postId,
            @LoginUser AuthenticatedUser loginUser
    ) {
        UUID parsedPostId = CanonicalUuidParser.parse(postId);
        PostLikeResult result = postLikeService.likePost(parsedPostId, loginUser.userId());

        return ResponseEntity.ok(PostLikeResponse.from(result));
    }

    @Override
    @DeleteMapping("/{postId}/likes")
    public ResponseEntity<PostLikeResponse> unlikePost(
            @PathVariable String postId,
            @LoginUser AuthenticatedUser loginUser
    ) {
        UUID parsedPostId = CanonicalUuidParser.parse(postId);
        PostLikeResult result = postLikeService.unlikePost(parsedPostId, loginUser.userId());

        return ResponseEntity.ok(PostLikeResponse.from(result));
    }
}
