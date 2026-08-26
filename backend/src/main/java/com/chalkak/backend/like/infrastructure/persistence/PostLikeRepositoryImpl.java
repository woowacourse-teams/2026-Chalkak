package com.chalkak.backend.like.infrastructure.persistence;

import com.chalkak.backend.like.repository.PostLikeCount;
import com.chalkak.backend.like.repository.PostLikeRepository;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class PostLikeRepositoryImpl implements PostLikeRepository {

    private final PostLikeJpaRepository postLikeJpaRepository;

    @Override
    public int createIfAbsent(UUID postId, UUID userId) {
        return postLikeJpaRepository.createIfAbsent(postId, userId);
    }

    @Override
    public int deleteByPostIdAndUserId(UUID postId, UUID userId) {
        return postLikeJpaRepository.deleteByPostIdAndUserId(postId, userId);
    }

    @Override
    public long countByPostId(UUID postId) {
        return postLikeJpaRepository.countByPostId(postId);
    }

    @Override
    public List<PostLikeCount> countByPostIds(List<UUID> postIds) {
        return postLikeJpaRepository.countByPostIds(postIds);
    }

    @Override
    public Set<UUID> findLikedPostIds(List<UUID> postIds, UUID userId) {
        return postLikeJpaRepository.findLikedPostIds(postIds, userId);
    }

    @Override
    public boolean existsByPostIdAndUserId(UUID postId, UUID userId) {
        return postLikeJpaRepository.existsByPostIdAndUserId(postId, userId);
    }
}
