package com.chalkak.backend.post.service;

import com.chalkak.backend.photo.service.ImageUrlProvider;
import com.chalkak.backend.post.domain.Post;
import com.chalkak.backend.post.repository.PostSlice;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record AuthorPostListResult(
        int currentPage,
        int pageSize,
        boolean hasNext,
        List<PostSummary> posts
) {

    public static AuthorPostListResult fromPostSlice(
            PostSlice slice,
            int currentPage,
            int pageSize,
            ImageUrlProvider imageUrlProvider
    ) {
        return new AuthorPostListResult(
                currentPage,
                pageSize,
                slice.hasNext(),
                slice.posts().stream()
                        .map(post -> PostSummary.fromPost(post, imageUrlProvider))
                        .toList()
        );
    }

    public record PostSummary(
            UUID id,
            String originalImageUrl,
            String thumbnailImageUrl,
            TopicSummary topic
    ) {

        private static PostSummary fromPost(Post post, ImageUrlProvider imageUrlProvider) {
            return new PostSummary(
                    post.getId(),
                    imageUrlProvider.getUrl(post.getPhoto().getOriginalStorageKey()),
                    imageUrlProvider.getUrl(post.getPhoto().getThumbnailStorageKey()),
                    new TopicSummary(
                            post.getTopic().getId(),
                            post.getTopic().getTitle(),
                            post.getTopic().getTopicDate()
                    )
            );
        }
    }

    public record TopicSummary(
            UUID id,
            String title,
            LocalDate topicDate
    ) {
    }
}
