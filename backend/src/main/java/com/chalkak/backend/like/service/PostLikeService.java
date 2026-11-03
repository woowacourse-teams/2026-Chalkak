package com.chalkak.backend.like.service;

import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.exception.NotFoundException;
import com.chalkak.backend.exception.UnauthorizedException;
import com.chalkak.backend.like.domain.LikeMilestonePolicy;
import com.chalkak.backend.like.repository.PostLikeCounterRepository;
import com.chalkak.backend.like.repository.PostLikeRepository;
import com.chalkak.backend.notification.service.NotificationService;
import com.chalkak.backend.post.domain.Post;
import com.chalkak.backend.post.repository.PostRepository;
import com.chalkak.backend.user.repository.UserRepository;
import java.time.Clock;
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
    private final NotificationService notificationService;
    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    public PostLikeResult likePost(UUID postId, UUID userId) {
        validateUser(userId);
        Post post = getVisiblePostForShare(postId);
        int createdCount = postLikeRepository.createIfAbsent(postId, userId);
        if (createdCount == 1) {
            postLikeCounterRepository.increment(postId);
        }

        long likeCount = postLikeCounterRepository.getCountByPostId(postId);
        if (createdCount == 1) {
            createMilestoneIfRequired(post, likeCount);
        }

        return new PostLikeResult(
                postId,
                likeCount,
                true
        );
    }

    public PostLikeResult unlikePost(UUID postId, UUID userId) {
        validateUser(userId);
        getVisiblePostForShare(postId);
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

    private Post getVisiblePostForShare(UUID postId) {
        return postRepository.findVisibleByIdForShare(postId)
                .orElseThrow(() -> new NotFoundException(
                        ErrorCode.BUSINESS_ERROR,
                        "게시물을 찾을 수 없습니다."
                ));
    }

    private void createMilestoneIfRequired(Post post, long likeCount) {
        long candidateCount = LikeMilestonePolicy.calculateCandidate(likeCount);
        if (candidateCount <= 0) {
            return;
        }
        long lastMilestoneCount = postLikeCounterRepository
                .getLastMilestoneCountByPostId(post.getId());
        if (candidateCount <= lastMilestoneCount) {
            return;
        }
        notificationService.createForLikeMilestone(
                post,
                candidateCount,
                clock.instant()
        );
        postLikeCounterRepository.updateLastMilestoneCount(post.getId(), candidateCount);
    }
}
