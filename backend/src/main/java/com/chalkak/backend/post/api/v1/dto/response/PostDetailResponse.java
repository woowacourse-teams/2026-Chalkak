package com.chalkak.backend.post.api.v1.dto.response;

import com.chalkak.backend.post.service.PostDetail;
import java.time.LocalDate;
import java.util.UUID;

public record PostDetailResponse(
        UUID id,
        TopicResponse topic,
        String originalImageUrl,
        String thumbnailImageUrl,
        String signatureOriginalImageUrl,
        String title
) {

    public static PostDetailResponse fromPostDetail(PostDetail detail) {
        return new PostDetailResponse(
                detail.id(),
                TopicResponse.fromPostDetail(detail.topic()),
                detail.originalImageUrl(),
                detail.thumbnailImageUrl(),
                detail.signatureOriginalImageUrl(),
                detail.title()
        );
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
