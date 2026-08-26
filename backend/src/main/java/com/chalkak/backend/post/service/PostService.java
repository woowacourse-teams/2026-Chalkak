package com.chalkak.backend.post.service;

import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.exception.NotFoundException;
import com.chalkak.backend.exception.UnauthorizedException;
import com.chalkak.backend.like.repository.PostLikeCount;
import com.chalkak.backend.like.repository.PostLikeRepository;
import com.chalkak.backend.photo.service.ImageUrlProvider;
import com.chalkak.backend.post.domain.Post;
import com.chalkak.backend.post.repository.PostRepository;
import com.chalkak.backend.post.repository.PostSlice;
import com.chalkak.backend.topic.domain.Topic;
import com.chalkak.backend.topic.repository.TopicRepository;
import com.chalkak.backend.user.repository.UserRepository;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final PostRepository postRepository;
    private final TopicRepository topicRepository;
    private final PostLikeRepository postLikeRepository;
    private final UserRepository userRepository;
    private final ImageUrlProvider imageUrlProvider;
    private final RandomSeedGenerator randomSeedGenerator;

    public PostDetail getPost(UUID postId, UUID userId) {
        validateUser(userId);
        Post post = getVisiblePost(postId);

        return PostDetail.from(
                post,
                imageUrlProvider,
                postLikeRepository.countByPostId(postId),
                postLikeRepository.existsByPostIdAndUserId(postId, userId)
        );
    }

    public PostListResult getPosts(
            LocalDate topicDate,
            PostSort sort,
            String randomSeed,
            int page,
            int pageSize,
            Optional<UUID> userId
    ) {
        validateRandomSeedCombination(sort, randomSeed, page);
        validateTopicDate(topicDate);
        userId.ifPresent(this::validateUser);

        Topic topic = getActiveTopic(topicDate);
        if (sort == PostSort.RANDOM) {
            return getRandomPosts(topic, randomSeed, page, pageSize, userId);
        }

        return getRecentPosts(topic, page, pageSize, userId);
    }

    private PostListResult getRandomPosts(
            Topic topic,
            String randomSeed,
            int page,
            int pageSize,
            Optional<UUID> userId
    ) {
        String effectiveRandomSeed = Objects.requireNonNullElseGet(
                randomSeed,
                randomSeedGenerator::generateRandomSeed
        );
        PostSlice postSlice = postRepository.findVisibleRandomByTopicId(
                topic.getId(),
                effectiveRandomSeed,
                page - 1,
                pageSize
        );

        return createPostListResult(
                postSlice,
                page,
                pageSize,
                effectiveRandomSeed,
                userId
        );
    }

    private PostListResult getRecentPosts(
            Topic topic,
            int page,
            int pageSize,
            Optional<UUID> userId
    ) {
        PostSlice postSlice = postRepository.findVisibleRecentByTopicId(
                topic.getId(),
                page - 1,
                pageSize
        );

        return createPostListResult(
                postSlice,
                page,
                pageSize,
                null,
                userId
        );
    }

    private PostListResult createPostListResult(
            PostSlice postSlice,
            int page,
            int pageSize,
            String randomSeed,
            Optional<UUID> userId
    ) {
        List<UUID> postIds = postSlice.posts().stream()
                .map(Post::getId)
                .toList();
        if (postIds.isEmpty()) {
            return PostListResult.from(
                    postSlice,
                    page,
                    pageSize,
                    randomSeed,
                    imageUrlProvider,
                    Map.of(),
                    Set.of()
            );
        }

        Map<UUID, Long> likeCounts = postLikeRepository.countByPostIds(postIds).stream()
                .collect(Collectors.toMap(
                        PostLikeCount::postId,
                        PostLikeCount::likeCount
                ));
        Set<UUID> likedPostIds = userId
                .map(id -> postLikeRepository.findLikedPostIds(postIds, id))
                .orElseGet(Set::of);

        return PostListResult.from(
                postSlice,
                page,
                pageSize,
                randomSeed,
                imageUrlProvider,
                likeCounts,
                likedPostIds
        );
    }

    private Post getVisiblePost(UUID postId) {
        return postRepository.findVisibleById(postId)
                .orElseThrow(() -> new NotFoundException(
                        ErrorCode.BUSINESS_ERROR,
                        "게시물을 찾을 수 없습니다."
                ));
    }

    private Topic getActiveTopic(LocalDate topicDate) {
        return topicRepository.findActiveByTopicDate(topicDate)
                .orElseThrow(() -> new NotFoundException(
                        ErrorCode.BUSINESS_ERROR,
                        "해당 날짜의 주제를 찾을 수 없습니다."
                ));
    }

    private void validateUser(UUID userId) {
        userRepository.findActiveById(userId)
                .orElseThrow(() -> new UnauthorizedException(
                        ErrorCode.UNAUTHORIZED,
                        "유효하지 않은 인증 정보입니다."
                ));
    }

    private void validateRandomSeedCombination(
            PostSort sort,
            String randomSeed,
            int page
    ) {
        boolean hasSeedWithRecentSort = sort == PostSort.RECENT && randomSeed != null;
        boolean isSeedMissingAfterFirstRandomPage = sort == PostSort.RANDOM
                && page > 1
                && randomSeed == null;

        if (hasSeedWithRecentSort
                || isSeedMissingAfterFirstRandomPage) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_ERROR,
                    "조회 조건이 올바르지 않습니다."
            );
        }
    }

    private void validateTopicDate(LocalDate topicDate) {
        if (topicDate.isAfter(LocalDate.now(KST))) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_ERROR,
                    "미래 날짜의 게시물은 조회할 수 없습니다."
            );
        }
    }
}
