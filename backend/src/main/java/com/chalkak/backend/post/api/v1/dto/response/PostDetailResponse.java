package com.chalkak.backend.post.api.v1.dto.response;

import com.chalkak.backend.post.service.PostDetail;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;
import java.util.UUID;

public record PostDetailResponse(
        UUID id,
        TopicResponse topic,
        @JsonProperty("original_image_url") String originalImageUrl,
        @JsonProperty("thumbnail_image_url") String thumbnailImageUrl,
        @JsonProperty("signature_original_image_url") String signatureOriginalImageUrl,
        String description
) {

    public static PostDetailResponse fromPostDetail(PostDetail detail) {
        return new PostDetailResponse(
                detail.id(),
                TopicResponse.fromPostDetail(detail.topic()),
                detail.originalImageUrl(),
                detail.thumbnailImageUrl(),
                detail.signatureOriginalImageUrl(),
                detail.description()
        );
    }

    public record TopicResponse(
            UUID id,
            String title,
            @JsonProperty("topic_date") LocalDate topicDate
    ) {

        public static TopicResponse fromPostDetail(PostDetail.TopicDetail topic) {
            return new TopicResponse(topic.id(), topic.title(), topic.topicDate());
        }
    }
}
