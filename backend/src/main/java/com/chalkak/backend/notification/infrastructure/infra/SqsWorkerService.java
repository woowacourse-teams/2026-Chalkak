package com.chalkak.backend.notification.infrastructure.infra;

import com.chalkak.backend.notification.service.DevicePushSender;
import com.chalkak.backend.notification.service.PushMessage;
import com.chalkak.backend.notification.service.PushProcessingResult;
import com.chalkak.backend.notification.service.PushWorkerService;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageSystemAttributeName;
import tools.jackson.databind.ObjectMapper;

@Slf4j
public class SqsWorkerService {
    private static final Duration VISIBILITY_EXTENSION_INTERVAL = Duration.ofSeconds(40);

    private final SqsClient sqsClient;
    private final String queueUrl;
    private final ObjectMapper objectMapper;
    private final PushWorkerService pushWorkerService;
    private final DevicePushSender sender;
    private final Clock clock;
    private final ScheduledExecutorService visibilityExtensionExecutor;

    public SqsWorkerService(
            SqsClient sqsClient,
            String queueUrl,
            ObjectMapper objectMapper,
            PushWorkerService pushWorkerService,
            DevicePushSender sender,
            Clock clock,
            ScheduledExecutorService visibilityExtensionExecutor
    ) {
        this.sqsClient = sqsClient;
        this.queueUrl = queueUrl;
        this.objectMapper = objectMapper;
        this.pushWorkerService = pushWorkerService;
        this.sender = sender;
        this.clock = clock;
        this.visibilityExtensionExecutor = visibilityExtensionExecutor;
    }

    @Scheduled(fixedDelay = 1000)
    public void poll() {
        try {
            Optional<Message> message = findMessage();
            message.ifPresent(this::process);
        } catch (RuntimeException exception) {
            logFailure("worker_poll_failed", exception);
        }
    }

    public void process(Message message) {
        try (VisibilityExtension visibility = new VisibilityExtension(message)) {
            processMessage(message, visibility);
        } catch (RuntimeException exception) {
            logFailure("worker_processing_failed", exception);
            // 삭제하지 않는다. SQS가 다시 전달하거나 설정된 최대 수신 횟수 이후 DLQ로 이동한다.
        }
    }

    private Optional<Message> findMessage() {
        List<Message> messages = sqsClient.receiveMessage(request -> request
                .queueUrl(queueUrl)
                .maxNumberOfMessages(1)
                .waitTimeSeconds(20)
                .visibilityTimeout(60)
                .messageSystemAttributeNames(
                        MessageSystemAttributeName.APPROXIMATE_RECEIVE_COUNT))
                .messages();

        if (messages.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(messages.getFirst());
    }

    private void processMessage(Message message, VisibilityExtension visibility) {
        PushMessage pushMessage = readPushMessage(message);
        if (pushMessage == null) {
            visibility.finishAttempt(() -> delete(message));
            return;
        }
        logReceived(message, pushMessage);
        PushProcessingResult result = pushWorkerService.process(pushMessage, sender);
        visibility.finishAttempt(() -> handleProcessingResult(message, pushMessage, result));
    }

    private PushMessage readPushMessage(Message message) {
        try {
            return objectMapper.readValue(message.body(), PushMessage.class);
        } catch (tools.jackson.core.JacksonException exception) {
            logFailure("worker_invalid_message", exception);
            return null;
        }
    }

    private void handleProcessingResult(
            Message message,
            PushMessage pushMessage,
            PushProcessingResult result
    ) {
        if (!result.retryable()) {
            delete(message);
            return;
        }
        postponeRetry(message, pushMessage, result);
    }

    private void extendVisibility(Message message) {
        try {
            sqsClient.changeMessageVisibility(request -> request.queueUrl(queueUrl)
                    .receiptHandle(message.receiptHandle()).visibilityTimeout(60)
                    .overrideConfiguration(config -> config.apiCallTimeout(Duration.ofSeconds(5))
                            .apiCallAttemptTimeout(Duration.ofSeconds(5))));
        } catch (RuntimeException exception) {
            logFailure("worker_visibility_failed", exception);
        }
    }

    private void postponeRetry(
            Message message,
            PushMessage pushMessage,
            PushProcessingResult result
    ) {
        if (result.retryAfter().isZero()) {
            return;
        }
        long remaining = Duration.between(clock.instant(), pushMessage.expiresAt()).toSeconds();
        long seconds = Math.max(0, Math.min(remaining, result.retryAfter().toSeconds() + 1));
        sqsClient.changeMessageVisibility(request -> request.queueUrl(queueUrl)
                .receiptHandle(message.receiptHandle())
                .visibilityTimeout((int) Math.min(43200, seconds))
                .overrideConfiguration(config -> config.apiCallTimeout(Duration.ofSeconds(5))
                        .apiCallAttemptTimeout(Duration.ofSeconds(5))));
    }

    private void delete(Message message) {
        sqsClient.deleteMessage(request -> request.queueUrl(queueUrl)
                .receiptHandle(message.receiptHandle())
                .overrideConfiguration(config -> config.apiCallTimeout(Duration.ofSeconds(5))
                        .apiCallAttemptTimeout(Duration.ofSeconds(5))));
        log.atInfo().addKeyValue("type", "notification")
                .addKeyValue("stage", "worker_message_deleted")
                .addKeyValue("messageId", message.messageId()).log("SQS 푸시 작업 완료");
    }

    private void logFailure(String stage, RuntimeException exception) {
        log.atError().addKeyValue("type", "notification").addKeyValue("stage", stage)
                .addKeyValue("errorCode", exception.getClass().getSimpleName())
                .log("푸시 작업 처리 오류");
    }

    private void logReceived(Message message, PushMessage pushMessage) {
        log.atInfo().addKeyValue("type", "notification").addKeyValue("stage", "worker_received")
                .addKeyValue("eventId", pushMessage.eventId())
                .addKeyValue("notificationId", pushMessage.notificationId())
                .addKeyValue("messageId", message.messageId())
                .addKeyValue("receiveCount", message.attributes().get(
                        MessageSystemAttributeName.APPROXIMATE_RECEIVE_COUNT))
                .log("푸시 작업 수신");
    }

    private final class VisibilityExtension implements AutoCloseable {
        private final Message message;
        private final ScheduledFuture<?> task;
        private State state = State.ACTIVE;

        private VisibilityExtension(Message message) {
            this.message = message;
            long intervalSeconds = VISIBILITY_EXTENSION_INTERVAL.getSeconds();
            task = visibilityExtensionExecutor.scheduleAtFixedRate(this::extend, intervalSeconds,
                    intervalSeconds, TimeUnit.SECONDS);
        }

        private synchronized void finishAttempt(Runnable completion) {
            close();
            completion.run();
        }

        @Override
        public synchronized void close() {
            state = State.STOPPED;
            task.cancel(false);
        }

        private synchronized void extend() {
            if (state == State.STOPPED) {
                return;
            }
            extendVisibility(message);
        }

        private enum State {
            ACTIVE, STOPPED
        }
    }
}
