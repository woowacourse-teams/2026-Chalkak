package com.chalkak.backend.post.service;

import com.chalkak.backend.photo.service.ImageUrlProvider;
import com.chalkak.backend.post.domain.ModerationStatus;
import com.chalkak.backend.post.domain.Post;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

public record PostCalendarResult(
        int year,
        int month,
        List<PostSummary> posts
) {

    public static PostCalendarResult from(
            YearMonth yearMonth,
            List<Post> posts,
            ImageUrlProvider imageUrlProvider
    ) {
        return new PostCalendarResult(
                yearMonth.getYear(),
                yearMonth.getMonthValue(),
                posts.stream()
                        .map(post -> PostSummary.from(post, imageUrlProvider))
                        .toList()
        );
    }

    public record PostSummary(
            LocalDate topicDate,
            UUID postId,
            String thumbnailImageUrl,
            ModerationStatus status
    ) {

        private static PostSummary from(Post post, ImageUrlProvider imageUrlProvider) {
            return new PostSummary(
                    post.getTopic().getTopicDate(),
                    post.getId(),
                    imageUrlProvider.getUrl(post.getPhoto().getThumbnailStorageKey()),
                    post.getModerationStatus()
            );
        }
    }
}
