package com.chalkak.backend.admin.api.v1.dto.response;

import com.chalkak.backend.admin.service.AdminPostListResult;
import com.chalkak.backend.post.domain.ModerationStatus;
import com.chalkak.backend.user.domain.UserStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record AdminPostListResponse(
        int currentPage,
        int pageSize,
        boolean hasNext,
        List<PostResponse> posts
) {

    public static AdminPostListResponse from(AdminPostListResult result) {
        return new AdminPostListResponse(
                result.currentPage(),
                result.pageSize(),
                result.hasNext(),
                result.posts().stream()
                        .map(PostResponse::from)
                        .toList()
        );
    }

    @Schema(name = "AdminPostListItem")
    public record PostResponse(
            UUID postId,
            @Schema(nullable = true)
            String title,
            @Schema(allowableValues = {"PENDING", "APPROVED", "REJECTED"})
            ModerationStatus moderationStatus,
            AuthorResponse author,
            TopicResponse topic,
            PhotoResponse photo,
            long likeCount,
            Instant createdAt,
            @Schema(nullable = true)
            Instant moderatedAt,
            @Schema(nullable = true)
            Instant deletedAt
    ) {

        private static PostResponse from(AdminPostListResult.PostSummary post) {
            return new PostResponse(
                    post.postId(),
                    post.title(),
                    post.moderationStatus(),
                    AuthorResponse.from(post.author()),
                    TopicResponse.from(post.topic()),
                    PhotoResponse.from(post.photo()),
                    post.likeCount(),
                    post.createdAt(),
                    post.moderatedAt(),
                    post.deletedAt()
            );
        }
    }

    @Schema(name = "AdminPostListAuthor")
    public record AuthorResponse(
            UUID userId,
            @Schema(nullable = true)
            String email,
            UserStatus status,
            @Schema(nullable = true)
            Instant deletedAt
    ) {

        private static AuthorResponse from(AdminPostListResult.AuthorSummary author) {
            if (author == null) {
                return null;
            }
            return new AuthorResponse(
                    author.userId(),
                    author.email(),
                    author.status(),
                    author.deletedAt()
            );
        }
    }

    @Schema(name = "AdminPostListTopic")
    public record TopicResponse(
            UUID topicId,
            String title,
            LocalDate topicDate
    ) {

        private static TopicResponse from(AdminPostListResult.TopicSummary topic) {
            if (topic == null) {
                return null;
            }
            return new TopicResponse(topic.topicId(), topic.title(), topic.topicDate());
        }
    }

    @Schema(name = "AdminPostListPhoto")
    public record PhotoResponse(
            UUID photoId,
            String originalImageUrl,
            @Schema(nullable = true)
            String thumbnailImageUrl
    ) {

        private static PhotoResponse from(AdminPostListResult.PhotoSummary photo) {
            if (photo == null) {
                return null;
            }
            return new PhotoResponse(
                    photo.photoId(),
                    photo.originalImageUrl(),
                    photo.thumbnailImageUrl()
            );
        }
    }
}
