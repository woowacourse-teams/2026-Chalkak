package com.chalkak.backend.notification.infrastructure.infra;

import com.chalkak.backend.common.logging.Elapsed;
import com.chalkak.backend.notification.service.PushMessage;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.spi.LoggingEventBuilder;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageSystemAttributeName;

@Slf4j
public final class SqsPushLog {

    private SqsPushLog() {
    }

    public static void logEmptyPoll(long sqsReceiveDurationMs) {
        log.atDebug().addKeyValue("type", "notification")
                .addKeyValue("stage", "worker_sqs_receive")
                .addKeyValue("received", false)
                .addKeyValue("sqsMessageId", (Object) null)
                .addKeyValue("receiveCount", (Object) null)
                .addKeyValue("sqsReceiveDurationMs", sqsReceiveDurationMs)
                .log("SQS 빈 수신 응답");
    }

    public static void logPollFailed(RuntimeException exception, Elapsed elapsed) {
        log.atError().addKeyValue("type", "notification")
                .addKeyValue("stage", "worker_poll_failed")
                .addKeyValue("phase", "RECEIVE")
                .addKeyValue("sqsReceiveDurationMs", elapsed.toMillis())
                .addKeyValue("errorCode", exception.getClass().getSimpleName())
                .log("푸시 작업 수신 오류");
    }

    public static void logProcessingPollFailed(
            Optional<Message> message,
            RuntimeException exception,
            long sqsReceiveDurationMs
    ) {
        log.atError().addKeyValue("type", "notification")
                .addKeyValue("stage", "worker_poll_failed")
                .addKeyValue("phase", "PROCESSING")
                .addKeyValue("sqsReceiveDurationMs", sqsReceiveDurationMs)
                .addKeyValue("sqsMessageId", message.map(Message::messageId).orElse(null))
                .addKeyValue("receiveCount", message.map(value -> value.attributes().get(
                        MessageSystemAttributeName.APPROXIMATE_RECEIVE_COUNT)).orElse(null))
                .addKeyValue("errorCode", exception.getClass().getSimpleName())
                .log("수신 후 푸시 처리 진입·종료 오류");
    }

    public static void logAttemptCompleted(
            Message message,
            Instant receivedAt,
            Elapsed elapsed,
            String outcome
    ) {
        log.atInfo().addKeyValue("type", "notification")
                .addKeyValue("stage", "worker_attempt_completed")
                .addKeyValue("outcome", outcome)
                .addKeyValue("queueAgeMs", findQueueAgeMillis(message, receivedAt))
                .addKeyValue("processingDurationMs", elapsed.toMillis())
                .log("푸시 처리 시도 종료");
    }

    public static void logDeleted(Elapsed elapsed) {
        log.atInfo().addKeyValue("type", "notification")
                .addKeyValue("stage", "worker_message_deleted")
                .addKeyValue("sqsDeleteDurationMs", elapsed.toMillis())
                .log("SQS 푸시 작업 완료");
    }

    public static void logDeleteFailed(Elapsed elapsed) {
        // 기존 worker_processing_failed만 ERROR로 남겨 오류 지표를 중복 집계하지 않는다.
        log.atInfo().addKeyValue("type", "notification")
                .addKeyValue("stage", "worker_sqs_delete_failed")
                .addKeyValue("sqsDeleteDurationMs", elapsed.toMillis())
                .log("SQS 삭제 요청 실패 시간");
    }

    public static void logReceived(PushMessage pushMessage, Long sqsReceiveDurationMs) {
        var event = log.atInfo().addKeyValue("type", "notification")
                .addKeyValue("stage", "worker_received")
                .addKeyValue("received", true)
                .addKeyValue("messageParsed", pushMessage != null);
        addReceiveTiming(event, sqsReceiveDurationMs);
        if (pushMessage != null) {
            event.addKeyValue("eventId", pushMessage.eventId())
                    .addKeyValue("notificationId", pushMessage.notificationId())
                    .addKeyValue("userId", pushMessage.userId())
                    .addKeyValue("notificationType", pushMessage.type())
                    .addKeyValue("sourceId", pushMessage.sourceId());
        }
        event.log("푸시 작업 수신");
    }

    public static void logRetryPending(PushMessage pushMessage, Duration retryAfter) {
        log.atInfo().addKeyValue("type", "notification")
                .addKeyValue("stage", "worker_retry_pending")
                .addKeyValue("eventId", pushMessage.eventId())
                .addKeyValue("userId", pushMessage.userId())
                .addKeyValue("notificationId", pushMessage.notificationId())
                .addKeyValue("retryAfterSeconds", retryAfter.toSeconds())
                .log("푸시 작업 재전달 대기");
    }

    public static void logProcessingFailed(
            RuntimeException exception,
            Long sqsReceiveDurationMs,
            boolean receivedLogged
    ) {
        var event = log.atError().addKeyValue("type", "notification")
                .addKeyValue("stage", "worker_processing_failed")
                .addKeyValue("errorCode", exception.getClass().getSimpleName());
        if (!receivedLogged) {
            addReceiveTiming(event, sqsReceiveDurationMs);
        }
        event.log("푸시 작업 처리 오류");
    }

    public static void logInvalidMessage(RuntimeException exception) {
        logFailure("worker_invalid_message", exception);
    }

    public static void logVisibilityFailed(Message message, RuntimeException exception) {
        // 연장 전용 스레드는 처리 스레드의 MDC를 상속하지 않는다.
        log.atError().addKeyValue("type", "notification")
                .addKeyValue("stage", "worker_visibility_failed")
                .addKeyValue("sqsMessageId", message.messageId())
                .addKeyValue("receiveCount", message.attributes().get(
                        MessageSystemAttributeName.APPROXIMATE_RECEIVE_COUNT))
                .addKeyValue("errorCode", exception.getClass().getSimpleName())
                .log("푸시 숨김 시간 연장 실패");
    }

    private static void addReceiveTiming(LoggingEventBuilder event, Long sqsReceiveDurationMs) {
        if (sqsReceiveDurationMs != null) {
            event.addKeyValue("sqsReceiveDurationMs", sqsReceiveDurationMs);
        }
    }

    private static Long findQueueAgeMillis(Message message, Instant receivedAt) {
        String sentTimestamp = message.attributes().get(MessageSystemAttributeName.SENT_TIMESTAMP);
        if (sentTimestamp == null) {
            return null;
        }
        try {
            long sentAt = Long.parseLong(sentTimestamp);
            long receivedAtMillis = receivedAt.toEpochMilli();
            if (sentAt < 0 || sentAt > receivedAtMillis) {
                return null;
            }
            return receivedAtMillis - sentAt;
        } catch (NumberFormatException exception) {
            // 조사용 메타데이터가 잘못돼도 기존 발송·삭제 동작은 유지한다.
            return null;
        }
    }

    private static void logFailure(String stage, RuntimeException exception) {
        log.atError().addKeyValue("type", "notification").addKeyValue("stage", stage)
                .addKeyValue("errorCode", exception.getClass().getSimpleName())
                .log("푸시 작업 처리 오류");
    }
}
