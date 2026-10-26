package com.chalkak.backend.like.domain;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

public record PostLikes(Map<UUID, Long> counts, Set<UUID> likedPostIds) {

    public PostLikes {
        counts = Map.copyOf(counts);
        likedPostIds = Set.copyOf(likedPostIds);
    }

    public static PostLikes createEmpty() {
        return new PostLikes(Map.of(), Set.of());
    }

    public boolean isLiked(UUID postId) {
        return likedPostIds.contains(postId);
    }

    public long getLikeCount(UUID postId) {
        return counts.getOrDefault(postId, 0L);
    }
}
