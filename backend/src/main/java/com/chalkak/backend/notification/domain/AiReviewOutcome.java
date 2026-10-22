package com.chalkak.backend.notification.domain;

/**
 * AI 리뷰 결과 알림이 알리는 결과. 알림 종류와 고정 문구를 결정한다. 실패는 다시 요청할 수 있는지로만 나눈다.
 */
public enum AiReviewOutcome {

    COMPLETED(
            NotificationType.AI_REVIEW_COMPLETED,
            "AI 피드백이 도착했어요",
            "사진의 구도 점수와 피드백을 확인해 보세요."
    ), FAILED_RETRYABLE(
            NotificationType.AI_REVIEW_FAILED,
            "AI 피드백을 만들지 못했어요",
            "잠시 후 다시 요청해 주세요."
    ), FAILED_FINAL(
            NotificationType.AI_REVIEW_FAILED,
            "AI 피드백을 만들지 못했어요",
            "이 사진은 더 이상 AI 피드백을 요청할 수 없어요."
    );

    private final NotificationType type;
    private final String title;
    private final String body;

    AiReviewOutcome(
            NotificationType type,
            String title,
            String body
    ) {
        this.type = type;
        this.title = title;
        this.body = body;
    }

    public NotificationType getType() {
        return type;
    }

    public String getTitle() {
        return title;
    }

    public String getBody() {
        return body;
    }
}
