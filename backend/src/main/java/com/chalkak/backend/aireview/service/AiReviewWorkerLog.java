package com.chalkak.backend.aireview.service;

import com.chalkak.backend.aireview.domain.AiReview;
import com.chalkak.backend.aireview.domain.AiReview.Invocation;
import com.chalkak.backend.aireview.domain.AiReview.Status;
import com.chalkak.backend.aireview.service.AiReviewWorkerService.Claim;
import com.chalkak.backend.common.logging.LogFields;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.spi.LoggingEventBuilder;

/**
 * AI 리뷰 처리 워커가 남기는 로그를 한곳에서 조립한다. 이벤트 이름과 필드 키는 모니터링 문서와 맞춰 쓴다.
 */
public final class AiReviewWorkerLog {

    private static final Logger AI_REVIEW_LOG = LoggerFactory.getLogger("chalkak.aireview");

    private AiReviewWorkerLog() {
    }

    public static void finished(AiReview review, Claim claim) {
        boolean completed = review.getStatus() == Status.COMPLETED;
        LoggingEventBuilder event = event(
                completed ? AI_REVIEW_LOG.atInfo() : AI_REVIEW_LOG.atWarn(),
                completed ? "review_completed" : "review_failed",
                claim
        )
                .addKeyValue("promptVersion", claim.version().getVersion())
                .addKeyValue("model", claim.version().getModel())
                .addKeyValue("queueWaitMs",
                        Duration.between(review.getLastRequestedAt(), review.getStartedAt())
                                .toMillis())
                .addKeyValue("totalMs",
                        Duration.between(review.getLastRequestedAt(), review.getCompletedAt())
                                .toMillis());
        withOptionalFields(event, review).log(completed ? "AI 리뷰 완료" : "AI 리뷰 실패");
    }

    /**
     * 자동 재시도를 예약했다. DB에는 마지막 시도의 값만 남으므로 재시도 비용을 추적하려고 시도마다 사용량을 남긴다.
     */
    public static void retryScheduled(
            AiReview review,
            Claim claim,
            Duration retryDelay,
            boolean retryAfterUsed
    ) {
        LoggingEventBuilder event = event(
                AI_REVIEW_LOG.atWarn(),
                "review_retry_scheduled",
                claim
        )
                .addKeyValue("roundAttempt", review.getRoundAttempt())
                .addKeyValue("requestCount", review.getRequestCount())
                .addKeyValue("promptVersion", claim.version().getVersion())
                .addKeyValue("model", claim.version().getModel())
                .addKeyValue("retryInMs", retryDelay.toMillis())
                .addKeyValue("retryAfterUsed", retryAfterUsed);
        event = withOptionalFields(event, review);
        String openaiResponseId = review.getInvocation().openaiResponseId();
        if (openaiResponseId != null) {
            event = event.addKeyValue("openaiResponseId", openaiResponseId);
        }
        event.log("AI 리뷰 자동 재시도 예약");
    }

    public static void staleUpdate(Claim claim) {
        event(
                AI_REVIEW_LOG.atWarn(),
                "review_stale_update",
                claim
        ).log("AI 리뷰 늦은 반영 무시");
    }

    public static void abandoned(
            Claim claim,
            AbandonReason reason,
            RuntimeException exception
    ) {
        LoggingEventBuilder event = event(
                AI_REVIEW_LOG.atWarn(),
                "review_abandoned",
                claim
        ).addKeyValue("reason", reason.name());
        if (exception != null) {
            event = event.addKeyValue(LogFields.ERROR_CODE, exception.getClass().getSimpleName());
        }
        event.log("AI 리뷰 처리 중단, 임대 만료 뒤 다시 가져감");
    }

    public static void pollFailed(RuntimeException exception) {
        AI_REVIEW_LOG.atError()
                .addKeyValue(LogFields.TYPE, LogFields.TYPE_AI_REVIEW)
                .addKeyValue(LogFields.EVENT, "worker_poll_failed")
                .addKeyValue(LogFields.ERROR_CODE, exception.getClass().getSimpleName())
                .log("AI 리뷰 워커 폴링 실패");
    }

    private static LoggingEventBuilder withOptionalFields(LoggingEventBuilder builder,
            AiReview review) {
        LoggingEventBuilder event = builder;
        for (Map.Entry<String, Object> field : optionalFields(review).entrySet()) {
            if (field.getValue() != null) {
                event = event.addKeyValue(field.getKey(), field.getValue());
            }
        }
        return event;
    }

    /** 값이 없는 필드는 남기지 않으므로 호출 쪽에서 null을 건너뛴다. 순서가 곧 로그 필드 순서다. */
    private static Map<String, Object> optionalFields(AiReview review) {
        Invocation invocation = review.getInvocation();
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("latencyMs", invocation.latencyMs());
        fields.put("inputTokens", invocation.inputTokens());
        fields.put("cachedInputTokens", invocation.cachedInputTokens());
        fields.put("outputTokens", invocation.outputTokens());
        fields.put("reasoningTokens", invocation.reasoningTokens());
        fields.put(LogFields.ERROR_CODE, review.getErrorCode());
        return fields;
    }

    /** 모든 이벤트가 공통으로 남기는 식별 필드를 붙인다. */
    private static LoggingEventBuilder event(
            LoggingEventBuilder builder,
            String name,
            Claim claim
    ) {
        return builder
                .addKeyValue(LogFields.TYPE, LogFields.TYPE_AI_REVIEW)
                .addKeyValue(LogFields.EVENT, name)
                .addKeyValue("aiReviewId", claim.reviewId())
                .addKeyValue(LogFields.POST_ID, claim.postId())
                .addKeyValue(LogFields.USER_ID, claim.userId())
                .addKeyValue("attempt", claim.attempt());
    }
}
