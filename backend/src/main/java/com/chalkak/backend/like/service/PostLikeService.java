package com.chalkak.backend.like.service;

import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.exception.NotFoundException;
import com.chalkak.backend.exception.UnauthorizedException;
import com.chalkak.backend.like.repository.PostLikeRepository;
import com.chalkak.backend.like.repository.PostLikeCounterRepository;
import com.chalkak.backend.post.repository.PostRepository;
import com.chalkak.backend.user.repository.UserRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class PostLikeService {

    private final PostLikeRepository postLikeRepository;
    private final PostLikeCounterRepository postLikeCounterRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;

    public PostLikeResult likePost(UUID postId, UUID userId) {
        validateUser(userId);
        validatePost(postId);
        if (postLikeRepository.createIfAbsent(postId, userId) == 1) {
            postLikeCounterRepository.increment(postId);
        }

        return new PostLikeResult(
                postId,
                postLikeCounterRepository.getCountByPostId(postId),
                true
        );
    }

    public PostLikeResult unlikePost(UUID postId, UUID userId) {
        validateUser(userId);
        validatePost(postId);
        if (postLikeRepository.deleteByPostIdAndUserId(postId, userId) == 1) {
            postLikeCounterRepository.decrement(postId);
        }

        return new PostLikeResult(
                postId,
                postLikeCounterRepository.getCountByPostId(postId),
                false
        );
    }

    private void validateUser(UUID userId) {
        userRepository.findActiveById(userId)
                .orElseThrow(() -> new UnauthorizedException(
                        ErrorCode.UNAUTHORIZED,
                        "유효하지 않은 인증 정보입니다."
                ));
    }

    private void validatePost(UUID postId) {
        postRepository.findVisibleByIdForShare(postId)
                .orElseThrow(() -> new NotFoundException(
                        ErrorCode.BUSINESS_ERROR,
                        "게시물을 찾을 수 없습니다."
                ));
    }
}
