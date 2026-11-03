package com.chalkak.backend.post.service;

import com.chalkak.backend.photo.service.ImageUrlProvider;
import com.chalkak.backend.post.domain.Post;
import com.chalkak.backend.topic.domain.Topic;
import java.time.LocalDate;
import java.util.UUID;

public record PostDetail(
        UUID id,
        UUID authorId,
        TopicDetail topic,
        String originalImageUrl,
        String thumbnailImageUrl,
        String signatureOriginalImageUrl,
        String title,
        String description,
        long likeCount,
        boolean isLiked,
        InstagramConsentDetail instagramConsent
) {

    public static PostDetail from(
            Post post,
            ImageUrlProvider imageUrlProvider,
            long likeCount,
            boolean isLiked,
            UUID viewerId
    ) {
        return new PostDetail(
                post.getId(),
                post.getAuthor().getId(),
                TopicDetail.from(post.getTopic()),
                imageUrlProvider.getUrl(post.getPhoto().getOriginalStorageKey()),
                imageUrlProvider.getUrl(post.getPhoto().getThumbnailStorageKey()),
                imageUrlProvider.getUrl(post.getAuthor().getSignatureOriginalStorageKey()),
                post.getTitle(),
                post.getDescription(),
                likeCount,
                isLiked,
                InstagramConsentDetail.forViewer(
                        post,
                        viewerId
                )
        );
    }

    public record InstagramConsentDetail(
            boolean agreed,
            String instagramId
    ) {

        /**
         * 작성자 본인에게만 동의 정보를 내려주고 다른 사용자에게는 {@code null}을 돌려준다.
         */
        private static InstagramConsentDetail forViewer(
                Post post,
                UUID viewerId
        ) {
            if (!post.isAuthoredBy(viewerId)) {
                return null;
            }
            return new InstagramConsentDetail(
                    post.getInstagramConsent().agreed(),
                    post.getInstagramConsent().instagramId()
            );
        }
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
