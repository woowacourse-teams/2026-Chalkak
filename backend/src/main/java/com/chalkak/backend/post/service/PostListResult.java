package com.chalkak.backend.post.service;

import com.chalkak.backend.photo.service.ImageUrlProvider;
import com.chalkak.backend.post.domain.Post;
import com.chalkak.backend.post.repository.PostSlice;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public record PostListResult(
        int currentPage,
        int pageSize,
        boolean hasNext,
        String randomSeed,
        List<PostSummary> posts
) {

    public static PostListResult from(
            PostSlice postSlice,
            int currentPage,
            int pageSize,
            String randomSeed,
            ImageUrlProvider imageUrlProvider,
            Map<UUID, Long> likeCounts,
            Set<UUID> likedPostIds
    ) {
        return new PostListResult(
                currentPage,
                pageSize,
                postSlice.hasNext(),
                randomSeed,
                postSlice.posts().stream()
                        .map(post -> PostSummary.fromPost(
                                post,
                                imageUrlProvider,
                                likeCounts.getOrDefault(post.getId(), 0L),
                                likedPostIds.contains(post.getId())
                        ))
                        .toList()
        );
    }

    public record PostSummary(
            UUID id,
            String originalImageUrl,
            String thumbnailImageUrl,
            String signatureOriginalImageUrl,
            String signatureThumbnailImageUrl,
            String title,
            Instant submittedAt,
            long likeCount,
            boolean isLiked
    ) {

        private static PostSummary fromPost(
                Post post,
                ImageUrlProvider imageUrlProvider,
                long likeCount,
                boolean isLiked
        ) {
            return new PostSummary(
                    post.getId(),
                    imageUrlProvider.getUrl(post.getPhoto().getOriginalStorageKey()),
                    imageUrlProvider.getUrl(post.getPhoto().getThumbnailStorageKey()),
                    imageUrlProvider.getUrl(post.getAuthor().getSignatureOriginalStorageKey()),
                    imageUrlProvider.getUrl(post.getAuthor().getSignatureThumbnailStorageKey()),
                    post.getTitle(),
                    post.getCreatedAt(),
                    likeCount,
                    isLiked
            );
        }
    }
}
