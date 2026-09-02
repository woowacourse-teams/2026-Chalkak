package com.chalkak.backend.like.api.v1.dto.response;

import com.chalkak.backend.like.service.PostLikeResult;
import java.util.UUID;

public record PostLikeResponse(
        UUID postId,
        long likeCount,
        boolean isLiked
) {

    public static PostLikeResponse from(PostLikeResult result) {
        return new PostLikeResponse(
                result.postId(),
                result.likeCount(),
                result.isLiked()
        );
    }
}
