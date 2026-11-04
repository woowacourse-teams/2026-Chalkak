package com.chalkak.backend.like.service;

import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.exception.NotFoundException;
import com.chalkak.backend.exception.UnauthorizedException;
import com.chalkak.backend.like.repository.PostLikeCounterRepository;
import com.chalkak.backend.like.repository.PostLikeRepository;
import com.chalkak.backend.notification.domain.LikeMilestonePolicy;
import com.chalkak.backend.notification.service.NotificationService;
import com.chalkak.backend.post.domain.Post;
import com.chalkak.backend.post.repository.PostRepository;
import com.chalkak.backend.user.repository.UserRepository;
import java.time.Clock;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class PostLikeService {

    private final PostLikeRepository postLikeRepository;
    private final PostLikeCounterRepository postLikeCounterRepository;
    private final NotificationService notificationService;
    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final Clock clock;
    private final boolean likeMilestoneEnabled;

    public PostLikeService(
            PostLikeRepository postLikeRepository,
            PostLikeCounterRepository postLikeCounterRepository,
            NotificationService notificationService,
            PostRepository postRepository,
            UserRepository userRepository,
            Clock clock,
            @Value("${chalkak.notification.like-milestone.enabled:false}") boolean likeMilestoneEnabled
    ) {
        this.postLikeRepository = postLikeRepository;
        this.postLikeCounterRepository = postLikeCounterRepository;
        this.notificationService = notificationService;
        this.postRepository = postRepository;
        this.userRepository = userRepository;
        this.clock = clock;
        this.likeMilestoneEnabled = likeMilestoneEnabled;
    }

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
        boolean updated = postLikeCounterRepository
                .updateLastMilestoneCount(post.getId(), candidateCount);
        if (!updated) {
            return;
        }
        // 1차 호환 배포에서는 기준 단계만 갱신하고 새 알림 종류를 DB·SQS에 만들지 않는다.
        if (!likeMilestoneEnabled) {
            return;
        }
        notificationService.createForLikeMilestone(
                post,
                candidateCount,
                clock.instant()
        );
    }
}
