package com.chalkak.backend.post.service;

import com.chalkak.backend.photo.service.ImageUrlProvider;
import com.chalkak.backend.post.domain.Post;
import com.chalkak.backend.topic.domain.Topic;
import java.time.LocalDate;
import java.util.UUID;

public record PostDetail(
        UUID id,
        TopicDetail topic,
        String originalImageUrl,
        String thumbnailImageUrl,
        String signatureOriginalImageUrl,
        String description
) {

    public static PostDetail from(
            Post post,
            ImageUrlProvider imageUrlProvider
    ) {
        return new PostDetail(
                post.getId(),
                TopicDetail.from(post.getTopic()),
                imageUrlProvider.getUrl(post.getPhoto().getOriginalStorageKey()),
                imageUrlProvider.getUrl(post.getPhoto().getThumbnailStorageKey()),
                imageUrlProvider.getUrl(post.getAuthor().getSignatureOriginalStorageKey()),
                post.getTitle()
        );
    }

    public record TopicDetail(
            UUID id,
            String title,
            LocalDate topicDate
    ) {

        public static TopicDetail from(Topic topic) {
            return new TopicDetail(
                    topic.getId(),
                    topic.getTitle(),
                    topic.getTopicDate()
            );
        }
    }
}
