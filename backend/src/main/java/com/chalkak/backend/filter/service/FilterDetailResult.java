package com.chalkak.backend.filter.service;

import com.chalkak.backend.filter.domain.Filter;
import com.chalkak.backend.filter.domain.FilterStatus;
import com.chalkak.backend.post.domain.PostImageUploadStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record FilterDetailResult(
        UUID filterId,
        UUID artistId,
        String name,
        String description,
        FilterStatus status,
        Instant createdAt,
        Instant updatedAt,
        List<Photo> photos
) {

    public static FilterDetailResult fromFilter(Filter filter, List<Photo> photos) {
        return new FilterDetailResult(
                filter.getId(),
                filter.getArtist().getId(),
                filter.getName(),
                filter.getDescription(),
                filter.getStatus(),
                filter.getCreatedAt(),
                filter.getUpdatedAt(),
                photos
        );
    }

    public record Photo(
            UUID uploadId,
            PostImageUploadStatus status,
            String originalImageUrl,
            String thumbnailImageUrl
    ) {
    }
}
