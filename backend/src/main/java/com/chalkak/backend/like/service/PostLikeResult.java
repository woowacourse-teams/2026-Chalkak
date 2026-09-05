package com.chalkak.backend.like.service;

import java.util.UUID;

public record PostLikeResult(
        UUID postId,
        long likeCount,
        boolean isLiked
) {
}
