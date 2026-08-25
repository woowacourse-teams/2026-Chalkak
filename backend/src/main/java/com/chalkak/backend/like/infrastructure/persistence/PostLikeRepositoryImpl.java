package com.chalkak.backend.like.infrastructure.persistence;

import com.chalkak.backend.like.repository.PostLikeRepository;
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
}
