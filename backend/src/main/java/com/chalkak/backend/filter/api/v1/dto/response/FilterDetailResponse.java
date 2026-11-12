package com.chalkak.backend.filter.api.v1.dto.response;

import com.chalkak.backend.filter.domain.FilterStatus;
import com.chalkak.backend.filter.service.FilterDetailResult;
import com.chalkak.backend.post.domain.PostImageUploadStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record FilterDetailResponse(
        UUID filterId,
        UUID artistId,
        @Schema(nullable = true)
        String name,
        @Schema(nullable = true)
        String description,
        FilterStatus status,
        Instant createdAt,
        Instant updatedAt,
        List<FilterPhotoResponse> photos
) {

    public static FilterDetailResponse fromResult(FilterDetailResult result) {
        return new FilterDetailResponse(
                result.filterId(),
                result.artistId(),
                result.name(),
                result.description(),
                result.status(),
                result.createdAt(),
                result.updatedAt(),
                result.photos().stream().map(FilterPhotoResponse::fromPhoto).toList()
        );
    }

    public record FilterPhotoResponse(
            UUID uploadId,
            PostImageUploadStatus status,
            @Schema(nullable = true)
            String originalImageUrl,
            @Schema(nullable = true)
            String thumbnailImageUrl
    ) {

        private static FilterPhotoResponse fromPhoto(FilterDetailResult.Photo photo) {
            return new FilterPhotoResponse(
                    photo.uploadId(),
                    photo.status(),
                    photo.originalImageUrl(),
                    photo.thumbnailImageUrl()
            );
        }
    }
}
