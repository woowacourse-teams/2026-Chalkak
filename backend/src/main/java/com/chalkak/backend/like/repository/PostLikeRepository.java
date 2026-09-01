package com.chalkak.backend.like.repository;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface PostLikeRepository {

    int createIfAbsent(UUID postId, UUID userId);

    int deleteByPostIdAndUserId(UUID postId, UUID userId);

    int deleteByPostId(UUID postId);

    long countByPostId(UUID postId);

    List<PostLikeCount> countByPostIds(List<UUID> postIds);

    Set<UUID> findLikedPostIds(List<UUID> postIds, UUID userId);

    boolean existsByPostIdAndUserId(UUID postId, UUID userId);
}
