package com.chalkak.backend.admin.api.v1.dto.response;

import com.chalkak.backend.admin.service.AdminPostDetail;
import com.chalkak.backend.post.domain.ModerationStatus;
import com.chalkak.backend.post.domain.PostImageUploadStatus;
import com.chalkak.backend.user.domain.UserStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

public record AdminPostDetailResponse(
        UUID postId,
        @Schema(nullable = true)
        String title,
        @Schema(allowableValues = {"PENDING", "APPROVED", "REJECTED"})
        ModerationStatus moderationStatus,
        AuthorResponse author,
        TopicResponse topic,
        PhotoResponse photo,
        ImageUploadResponse imageUpload,
        long likeCount,
        Instant createdAt,
        Instant updatedAt,
        @Schema(nullable = true)
        Instant moderatedAt,
        @Schema(nullable = true)
        UUID moderatedBy,
        @Schema(nullable = true)
        String rejectionReason,
        @Schema(nullable = true)
        Instant deletedAt
) {

    public static AdminPostDetailResponse from(AdminPostDetail detail) {
        return new AdminPostDetailResponse(
                detail.postId(),
                detail.title(),
                detail.moderationStatus(),
                AuthorResponse.from(detail.author()),
                TopicResponse.from(detail.topic()),
                PhotoResponse.from(detail.photo()),
                ImageUploadResponse.from(detail.imageUpload()),
                detail.likeCount(),
                detail.createdAt(),
                detail.updatedAt(),
                detail.moderatedAt(),
                detail.moderatedBy(),
                detail.rejectionReason(),
                detail.deletedAt()
        );
    }

    @Schema(name = "AdminPostDetailAuthor")
    public record AuthorResponse(
            UUID userId,
            @Schema(nullable = true)
            String email,
            UserStatus status,
            @Schema(nullable = true)
            Instant deletedAt
    ) {

        private static AuthorResponse from(AdminPostDetail.AuthorDetail author) {
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

    @Schema(name = "AdminPostDetailTopic")
    public record TopicResponse(
            UUID topicId,
            String title,
            LocalDate topicDate,
            Instant startsAt,
            Instant endsAt,
            @Schema(nullable = true)
            Instant deletedAt
    ) {

        private static TopicResponse from(AdminPostDetail.TopicDetail topic) {
            if (topic == null) {
                return null;
            }
            return new TopicResponse(
                    topic.topicId(),
                    topic.title(),
                    topic.topicDate(),
                    topic.startsAt(),
                    topic.endsAt(),
                    topic.deletedAt()
            );
        }
    }

    @Schema(name = "AdminPostDetailPhoto")
    public record PhotoResponse(
            UUID photoId,
            String originalImageUrl,
            @Schema(nullable = true)
            String thumbnailImageUrl,
            PhotoMetadataResponse metadata,
            Instant createdAt,
            Instant updatedAt,
            @Schema(nullable = true)
            Instant deletedAt
    ) {

        private static PhotoResponse from(AdminPostDetail.PhotoDetail photo) {
            if (photo == null) {
                return null;
            }
            return new PhotoResponse(
                    photo.photoId(),
                    photo.originalImageUrl(),
                    photo.thumbnailImageUrl(),
                    PhotoMetadataResponse.from(photo.metadata()),
                    photo.createdAt(),
                    photo.updatedAt(),
                    photo.deletedAt()
            );
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(name = "AdminPostDetailPhotoMetadata")
    public record PhotoMetadataResponse(
            @Schema(description = "이미지 가로 픽셀 수", nullable = true)
            Integer width,
            @Schema(description = "이미지 세로 픽셀 수", nullable = true)
            Integer height,
            @Schema(description = "이미지 바이트 크기", nullable = true)
            Long byteSize
    ) {

        private static PhotoMetadataResponse from(Map<String, Object> metadata) {
            return new PhotoMetadataResponse(
                    toInteger(metadata.get("width")),
                    toInteger(metadata.get("height")),
                    toLong(metadata.get("byteSize"))
            );
        }

        private static Integer toInteger(Object value) {
            return value instanceof Number number ? number.intValue() : null;
        }

        private static Long toLong(Object value) {
            return value instanceof Number number ? number.longValue() : null;
        }
    }

    @Schema(name = "AdminPostDetailImageUpload")
    public record ImageUploadResponse(
            UUID uploadId,
            PostImageUploadStatus status,
            @Schema(nullable = true)
            String rejectionReason,
            Instant createdAt,
            Instant updatedAt
    ) {

        private static ImageUploadResponse from(AdminPostDetail.ImageUploadDetail imageUpload) {
            if (imageUpload == null) {
                return null;
            }
            return new ImageUploadResponse(
                    imageUpload.uploadId(),
                    imageUpload.status(),
                    imageUpload.rejectionReason(),
                    imageUpload.createdAt(),
                    imageUpload.updatedAt()
            );
        }
    }
}
