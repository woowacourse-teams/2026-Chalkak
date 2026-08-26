package com.chalkak.backend.post.service;

import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.exception.NotFoundException;
import com.chalkak.backend.photo.service.ImageUrlProvider;
import com.chalkak.backend.post.domain.Post;
import com.chalkak.backend.post.repository.PostRepository;
import com.chalkak.backend.post.repository.PostSlice;
import com.chalkak.backend.topic.domain.Topic;
import com.chalkak.backend.topic.repository.TopicRepository;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 게시물 단건·목록 조회. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostQueryService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final PostRepository postRepository;
    private final TopicRepository topicRepository;
    private final ImageUrlProvider imageUrlProvider;
    private final RandomSeedGenerator randomSeedGenerator;

    public PostDetail getPost(UUID postId) {
        Post post = postRepository.findVisibleById(postId)
                .orElseThrow(() -> new NotFoundException(
                        ErrorCode.BUSINESS_ERROR,
                        "게시물을 찾을 수 없습니다."
                ));

        return PostDetail.from(post, imageUrlProvider);
    }

    public PostListResult getPosts(
            LocalDate topicDate,
            PostSort sort,
            String randomSeed,
            int page,
            int pageSize
    ) {
        validateRandomSeedCombination(sort, randomSeed, page);
        validateTopicDate(topicDate);

        Topic topic = topicRepository.findActiveByTopicDate(topicDate)
                .orElseThrow(() -> new NotFoundException(
                        ErrorCode.BUSINESS_ERROR,
                        "해당 날짜의 주제를 찾을 수 없습니다."
                ));
        if (sort == PostSort.RANDOM) {
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
            return PostListResult.from(
                    postSlice,
                    page,
                    pageSize,
                    effectiveRandomSeed,
                    imageUrlProvider
            );
        }

        PostSlice postSlice = postRepository.findVisibleRecentByTopicId(
                topic.getId(),
                page - 1,
                pageSize
        );

        return PostListResult.from(
                postSlice,
                page,
                pageSize,
                randomSeed,
                imageUrlProvider
        );
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
