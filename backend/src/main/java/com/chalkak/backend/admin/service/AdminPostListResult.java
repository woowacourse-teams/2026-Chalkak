package com.chalkak.backend.admin.service;

import com.chalkak.backend.admin.repository.AdminPostQueryPage;
import com.chalkak.backend.admin.repository.AdminPostSummaryProjection;
import com.chalkak.backend.photo.service.ImageUrlProvider;
import com.chalkak.backend.post.domain.ModerationStatus;
import com.chalkak.backend.user.domain.UserStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record AdminPostListResult(
        int currentPage,
        int pageSize,
        boolean hasNext,
        List<PostSummary> posts
) {

    public static AdminPostListResult from(
            AdminPostQueryPage page,
            ImageUrlProvider imageUrlProvider
    ) {
        return new AdminPostListResult(
                page.currentPage(),
                page.pageSize(),
                page.hasNext(),
                page.posts().stream()
                        .map(post -> PostSummary.from(post, imageUrlProvider))
                        .toList()
        );
    }

    public record PostSummary(
            UUID postId,
            String title,
            ModerationStatus moderationStatus,
            AuthorSummary author,
            TopicSummary topic,
            PhotoSummary photo,
            long likeCount,
            Instant createdAt,
            Instant moderatedAt,
            Instant deletedAt
    ) {

        private static PostSummary from(
                AdminPostSummaryProjection post,
                ImageUrlProvider imageUrlProvider
        ) {
            return new PostSummary(
                    post.postId(),
                    post.title(),
                    post.moderationStatus(),
                    new AuthorSummary(
                            post.authorId(),
                            post.authorEmail(),
                            post.authorStatus(),
                            post.authorDeletedAt()
                    ),
                    new TopicSummary(
                            post.topicId(),
                            post.topicTitle(),
                            post.topicDate()
                    ),
                    new PhotoSummary(
                            post.photoId(),
                            imageUrl(
                                    post.originalStorageKey(),
                                    post.deletedAt(),
                                    imageUrlProvider
                            ),
                            imageUrl(
                                    post.thumbnailStorageKey(),
                                    post.deletedAt(),
                                    imageUrlProvider
                            )
                    ),
                    post.likeCount(),
                    post.createdAt(),
                    post.moderatedAt(),
                    post.deletedAt()
            );
        }

        private static String imageUrl(
                String storageKey,
                Instant deletedAt,
                ImageUrlProvider imageUrlProvider
        ) {
            if (deletedAt != null) {
                return null;
            }
            return imageUrlProvider.getUrl(storageKey);
        }
    }

    public record AuthorSummary(
            UUID userId,
            String email,
            UserStatus status,
            Instant deletedAt
    ) {
    }

    public record TopicSummary(
            UUID topicId,
            String title,
            LocalDate topicDate
    ) {
    }

    public record PhotoSummary(
            UUID photoId,
            String originalImageUrl,
            String thumbnailImageUrl
    ) {
    }
}
