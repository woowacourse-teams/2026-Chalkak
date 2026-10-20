package com.chalkak.backend.post.api.v1.dto.response;

import com.chalkak.backend.post.service.PostDetail;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.UUID;

public record PostDetailResponse(
        UUID id,
        @Schema(description = "사진 작성자의 회원 ID. 조회자의 ID와 구분")
        UUID authorId,
        TopicResponse topic,
        String originalImageUrl,
        @Schema(description = "변환된 게시물 썸네일 URL", nullable = true)
        String thumbnailImageUrl,
        String signatureOriginalImageUrl,
        String title,
        long likeCount,
        boolean isLiked
) {

    public static PostDetailResponse fromPostDetail(PostDetail detail) {
        return new PostDetailResponse(
                detail.id(),
                detail.authorId(),
                TopicResponse.fromPostDetail(detail.topic()),
                detail.originalImageUrl(),
                detail.thumbnailImageUrl(),
                detail.signatureOriginalImageUrl(),
                detail.title(),
                detail.likeCount(),
                detail.isLiked());
    }

    public record TopicResponse(
            UUID id,
            String title,
            LocalDate topicDate
    ) {

        public static TopicResponse fromPostDetail(PostDetail.TopicDetail topic) {
            return new TopicResponse(topic.id(), topic.title(), topic.topicDate());
        }
    }
}
