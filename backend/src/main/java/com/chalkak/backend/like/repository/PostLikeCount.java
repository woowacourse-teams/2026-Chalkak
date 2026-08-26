package com.chalkak.backend.like.repository;

import java.util.UUID;

public record PostLikeCount(
        UUID postId,
        long likeCount
) {
}
