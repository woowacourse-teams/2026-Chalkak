package com.chalkak.backend.post.api.v1.dto.response;

import com.chalkak.backend.post.service.PostListResult;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

public record PostListResponse(
        int currentPage,
        int pageSize,
        boolean hasNext,
        String randomSeed,
        List<PostResponse> posts
) {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    public static PostListResponse fromPostListResult(PostListResult result) {
        return new PostListResponse(
                result.currentPage(),
                result.pageSize(),
                result.hasNext(),
                result.randomSeed(),
                result.posts().stream()
                        .map(PostResponse::fromPostListResult)
                        .toList()
        );
    }

    public record PostResponse(
            UUID id,
            String originalImageUrl,
            String thumbnailImageUrl,
            String signatureOriginalImageUrl,
            String signatureThumbnailImageUrl,
            String title,
            OffsetDateTime submittedAt
    ) {

        private static PostResponse fromPostListResult(PostListResult.PostSummary post) {
            return new PostResponse(
                    post.id(),
                    post.originalImageUrl(),
                    post.thumbnailImageUrl(),
                    post.signatureOriginalImageUrl(),
                    post.signatureThumbnailImageUrl(),
                    post.title(),
                    post.submittedAt().atZone(KST).toOffsetDateTime()
            );
        }
    }
}
