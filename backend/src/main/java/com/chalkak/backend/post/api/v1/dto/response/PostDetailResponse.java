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
        @Schema(description = "작품 제목. 제목이 없으면 null", nullable = true)
        String title,
        @Schema(description = "작품 설명. 줄바꿈을 포함할 수 있으며 설명이 없으면 null", nullable = true)
        String description,
        long likeCount,
        boolean isLiked,
        @Schema(
                description = "인스타그램 게시 동의 정보. 조회자가 작성자 본인일 때만 내려가며 다른 사용자에게는 null",
                nullable = true)
        InstagramConsentResponse instagramConsent
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
                detail.description(),
                detail.likeCount(),
                detail.isLiked(),
                InstagramConsentResponse.from(detail.instagramConsent())
        );
    }

    public record InstagramConsentResponse(
            @Schema(description = "공식 인스타그램 게시 동의 여부")
            boolean agreed,
            @Schema(description = "태그용 인스타그램 ID. 없으면 null", nullable = true)
            String instagramId
    ) {

        private static InstagramConsentResponse from(PostDetail.InstagramConsentDetail consent) {
            if (consent == null) {
                return null;
            }
            return new InstagramConsentResponse(
                    consent.agreed(),
                    consent.instagramId()
            );
        }
    }

    public record TopicResponse(
            UUID id,
            String title,
            LocalDate topicDate
    ) {

        public static TopicResponse fromPostDetail(PostDetail.TopicDetail topic) {
            return new TopicResponse(
                    topic.id(),
                    topic.title(),
                    topic.topicDate()
            );
        }
    }
}
