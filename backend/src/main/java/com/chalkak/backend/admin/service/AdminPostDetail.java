package com.chalkak.backend.admin.service;

import com.chalkak.backend.admin.domain.PostMediaDeletionStatus;
import com.chalkak.backend.admin.repository.AdminPostDetailProjection;
import com.chalkak.backend.photo.service.ImageUrlProvider;
import com.chalkak.backend.post.domain.ModerationStatus;
import com.chalkak.backend.post.domain.PostImageUploadStatus;
import com.chalkak.backend.user.domain.UserStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record AdminPostDetail(
        UUID postId,
        String title,
        ModerationStatus moderationStatus,
        AuthorDetail author,
        TopicDetail topic,
        PhotoDetail photo,
        ImageUploadDetail imageUpload,
        MediaDeletionDetail mediaDeletion,
        long likeCount,
        Instant createdAt,
        Instant updatedAt,
        Instant moderatedAt,
        UUID moderatedBy,
        String rejectionReason,
        Instant deletedAt
) {

    private static final List<String> SAFE_IMAGE_METADATA_KEYS = List.of(
            "width",
            "height",
            "byteSize"
    );

    public static AdminPostDetail from(
            AdminPostDetailProjection post,
            ImageUrlProvider imageUrlProvider
    ) {
        return new AdminPostDetail(
                post.postId(),
                post.title(),
                post.moderationStatus(),
                new AuthorDetail(
                        post.authorId(),
                        post.authorEmail(),
                        post.authorStatus(),
                        post.authorDeletedAt()
                ),
                new TopicDetail(
                        post.topicId(),
                        post.topicTitle(),
                        post.topicDate(),
                        post.topicStartsAt(),
                        post.topicEndsAt(),
                        post.topicDeletedAt()
                ),
                new PhotoDetail(
                        post.photoId(),
                        imageUrl(post.originalStorageKey(), post.deletedAt(), imageUrlProvider),
                        imageUrl(post.thumbnailStorageKey(), post.deletedAt(), imageUrlProvider),
                        safeImageMetadata(post.photoMetadata()),
                        post.photoCreatedAt(),
                        post.photoUpdatedAt(),
                        post.photoDeletedAt()
                ),
                ImageUploadDetail.from(post),
                MediaDeletionDetail.from(post),
                post.likeCount(),
                post.createdAt(),
                post.updatedAt(),
                post.moderatedAt(),
                post.moderatedBy(),
                post.rejectionReason(),
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

    private static Map<String, Object> safeImageMetadata(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> safeMetadata = new LinkedHashMap<>();
        for (String key : SAFE_IMAGE_METADATA_KEYS) {
            if (metadata.containsKey(key)) {
                safeMetadata.put(key, metadata.get(key));
            }
        }
        return Collections.unmodifiableMap(safeMetadata);
    }

    public record AuthorDetail(
            UUID userId,
            String email,
            UserStatus status,
            Instant deletedAt
    ) {
    }

    public record TopicDetail(
            UUID topicId,
            String title,
            LocalDate topicDate,
            Instant startsAt,
            Instant endsAt,
            Instant deletedAt
    ) {
    }

    public record PhotoDetail(
            UUID photoId,
            String originalImageUrl,
            String thumbnailImageUrl,
            Map<String, Object> metadata,
            Instant createdAt,
            Instant updatedAt,
            Instant deletedAt
    ) {
    }

    public record ImageUploadDetail(
            UUID uploadId,
            PostImageUploadStatus status,
            String rejectionReason,
            Instant createdAt,
            Instant updatedAt
    ) {

        private static ImageUploadDetail from(AdminPostDetailProjection post) {
            if (post.uploadId() == null) {
                return null;
            }
            return new ImageUploadDetail(
                    post.uploadId(),
                    post.uploadStatus(),
                    post.uploadRejectionReason(),
                    post.uploadCreatedAt(),
                    post.uploadUpdatedAt()
            );
        }
    }

    public record MediaDeletionDetail(
            PostMediaDeletionStatus status,
            int attemptCount,
            String lastErrorCode,
            Instant nextAttemptAt,
            Instant completedAt
    ) {

        private static MediaDeletionDetail from(AdminPostDetailProjection post) {
            if (post.mediaDeletionStatus() == null) {
                return null;
            }
            return new MediaDeletionDetail(
                    post.mediaDeletionStatus(),
                    post.mediaDeletionAttemptCount(),
                    post.mediaDeletionLastErrorCode(),
                    post.mediaDeletionNextAttemptAt(),
                    post.mediaDeletionCompletedAt()
            );
        }
    }
}
