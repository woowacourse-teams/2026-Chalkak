package com.chalkak.backend.like.repository;

import java.util.UUID;

public interface PostLikeRepository {

    int createIfAbsent(UUID postId, UUID userId);

    int deleteByPostIdAndUserId(UUID postId, UUID userId);

    long countByPostId(UUID postId);
}
