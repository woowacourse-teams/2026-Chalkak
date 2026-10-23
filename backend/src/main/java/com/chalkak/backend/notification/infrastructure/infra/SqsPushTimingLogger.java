package com.chalkak.backend.notification.infrastructure.infra;

import com.chalkak.backend.notification.service.PushMessage;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageSystemAttributeName;

@Slf4j
public class SqsPushTimingLogger {

    public void logPolled(Optional<Message> message, long startedAt) {
        log.atInfo().addKeyValue("type", "notification")
                .addKeyValue("stage", "worker_sqs_receive")
                .addKeyValue("received", message.isPresent())
                .addKeyValue("sqsMessageId", message.map(Message::messageId).orElse(null))
                .addKeyValue("receiveCount", message.map(value -> value.attributes().get(
                        MessageSystemAttributeName.APPROXIMATE_RECEIVE_COUNT)).orElse(null))
                .addKeyValue("sqsReceiveDurationMs", elapsedMillis(startedAt))
                .log("SQS 수신 요청 완료");
    }

    public void logPollFailed(RuntimeException exception, long startedAt) {
        log.atError().addKeyValue("type", "notification")
                .addKeyValue("stage", "worker_poll_failed")
                .addKeyValue("sqsReceiveDurationMs", elapsedMillis(startedAt))
                .addKeyValue("errorCode", exception.getClass().getSimpleName())
                .log("푸시 작업 수신 오류");
    }

    public void logAttemptCompleted(
            Message message,
            long receivedAt,
            long startedAt,
            String outcome
    ) {
        log.atInfo().addKeyValue("type", "notification")
                .addKeyValue("stage", "worker_attempt_completed")
                .addKeyValue("outcome", outcome)
                .addKeyValue("queueAgeMs", findQueueAgeMillis(message, receivedAt))
                .addKeyValue("processingDurationMs", elapsedMillis(startedAt))
                .log("푸시 처리 시도 종료");
    }

    public void logDeleted(long startedAt) {
        log.atInfo().addKeyValue("type", "notification")
                .addKeyValue("stage", "worker_message_deleted")
                .addKeyValue("sqsDeleteDurationMs", elapsedMillis(startedAt))
                .log("SQS 푸시 작업 완료");
    }

    public void logDeleteFailed(long startedAt) {
        // 기존 worker_processing_failed만 ERROR로 남겨 오류 지표를 중복 집계하지 않는다.
        log.atInfo().addKeyValue("type", "notification")
                .addKeyValue("stage", "worker_sqs_delete_failed")
                .addKeyValue("sqsDeleteDurationMs", elapsedMillis(startedAt))
                .log("SQS 삭제 요청 실패 시간");
    }

    public void logReceived(PushMessage pushMessage) {
        log.atInfo().addKeyValue("type", "notification").addKeyValue("stage", "worker_received")
                .addKeyValue("eventId", pushMessage.eventId())
                .addKeyValue("notificationId", pushMessage.notificationId())
                .addKeyValue("userId", pushMessage.userId())
                .addKeyValue("notificationType", pushMessage.type())
                .addKeyValue("sourceId", pushMessage.sourceId())
                .log("푸시 작업 수신");
    }

    public void logRetryPending(PushMessage pushMessage, Duration retryAfter) {
        log.atInfo().addKeyValue("type", "notification")
                .addKeyValue("stage", "worker_retry_pending")
                .addKeyValue("eventId", pushMessage.eventId())
                .addKeyValue("userId", pushMessage.userId())
                .addKeyValue("notificationId", pushMessage.notificationId())
                .addKeyValue("retryAfterSeconds", retryAfter.toSeconds())
                .log("푸시 작업 재전달 대기");
    }

    public void logProcessingFailed(RuntimeException exception) {
        logFailure("worker_processing_failed", exception);
    }

    public void logInvalidMessage(RuntimeException exception) {
        logFailure("worker_invalid_message", exception);
    }

    public void logVisibilityFailed(Message message, RuntimeException exception) {
        // 연장 전용 스레드는 처리 스레드의 MDC를 상속하지 않는다.
        log.atError().addKeyValue("type", "notification")
                .addKeyValue("stage", "worker_visibility_failed")
                .addKeyValue("sqsMessageId", message.messageId())
                .addKeyValue("receiveCount", message.attributes().get(
                        MessageSystemAttributeName.APPROXIMATE_RECEIVE_COUNT))
                .addKeyValue("errorCode", exception.getClass().getSimpleName())
                .log("푸시 숨김 시간 연장 실패");
    }

    private Long findQueueAgeMillis(Message message, long receivedAt) {
        String sentTimestamp = message.attributes().get(MessageSystemAttributeName.SENT_TIMESTAMP);
        if (sentTimestamp == null) {
            return null;
        }
        try {
            long sentAt = Long.parseLong(sentTimestamp);
            if (sentAt < 0 || sentAt > receivedAt) {
                return null;
            }
            return receivedAt - sentAt;
        } catch (NumberFormatException exception) {
            // 조사용 메타데이터가 잘못돼도 기존 발송·삭제 동작은 유지한다.
            return null;
        }
    }

    private long elapsedMillis(long startedAt) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
    }

    private void logFailure(String stage, RuntimeException exception) {
        log.atError().addKeyValue("type", "notification").addKeyValue("stage", stage)
                .addKeyValue("errorCode", exception.getClass().getSimpleName())
                .log("푸시 작업 처리 오류");
    }
}
