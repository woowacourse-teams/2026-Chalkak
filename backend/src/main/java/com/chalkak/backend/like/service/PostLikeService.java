package com.chalkak.backend.like.service;

import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.exception.NotFoundException;
import com.chalkak.backend.exception.UnauthorizedException;
import com.chalkak.backend.like.repository.PostLikeRepository;
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
    private final PostRepository postRepository;
    private final UserRepository userRepository;

    public PostLikeResult likePost(UUID postId, UUID userId) {
        validateUser(userId);
        validatePost(postId);
        postLikeRepository.createIfAbsent(postId, userId);

        return new PostLikeResult(
                postId,
                postLikeRepository.countByPostId(postId),
                true
        );
    }

    public PostLikeResult unlikePost(UUID postId, UUID userId) {
        validateUser(userId);
        validatePost(postId);
        postLikeRepository.deleteByPostIdAndUserId(postId, userId);

        return new PostLikeResult(
                postId,
                postLikeRepository.countByPostId(postId),
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
        postRepository.findVisibleById(postId)
                .orElseThrow(() -> new NotFoundException(
                        ErrorCode.BUSINESS_ERROR,
                        "게시물을 찾을 수 없습니다."
                ));
    }
}
