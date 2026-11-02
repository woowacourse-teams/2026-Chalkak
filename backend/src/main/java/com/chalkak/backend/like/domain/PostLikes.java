package com.chalkak.backend.like.domain;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;

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
        Long count = counts.get(postId);
        if (count == null) {
            throw new DataIntegrityViolationException("좋아요 집계 행을 찾을 수 없습니다: " + postId);
        }
        return count;
    }
}
