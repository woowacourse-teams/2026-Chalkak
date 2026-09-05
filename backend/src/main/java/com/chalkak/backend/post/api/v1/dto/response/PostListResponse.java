package com.chalkak.backend.post.api.v1.dto.response;

import com.chalkak.backend.post.service.PostListResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PostListResponse(
        int currentPage,
        int pageSize,
        boolean hasNext,
        @Schema(
                description = "랜덤 정렬 결과를 유지하는 값으로 다음 페이지 요청에도 동일하게 전달",
                nullable = true
        )
        String randomSeed,
        List<PostResponse> posts
) {

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
            @Schema(description = "변환된 게시물 썸네일 URL", nullable = true)
            String thumbnailImageUrl,
            String signatureOriginalImageUrl,
            @Schema(description = "변환된 사인 이미지 썸네일 URL", nullable = true)
            String signatureThumbnailImageUrl,
            String title,
            Instant submittedAt,
            long likeCount,
            boolean isLiked,
            @Schema(description = "조회자가 작성한 게시물인지 여부로, 인증 정보가 없으면 항상 false")
            boolean isMine
    ) {

        private static PostResponse fromPostListResult(PostListResult.PostSummary post) {
            return new PostResponse(
                    post.id(),
                    post.originalImageUrl(),
                    post.thumbnailImageUrl(),
                    post.signatureOriginalImageUrl(),
                    post.signatureThumbnailImageUrl(),
                    post.title(),
                    post.submittedAt(),
                    post.likeCount(),
                    post.isLiked(),
                    post.isMine()
            );
        }
    }
}
