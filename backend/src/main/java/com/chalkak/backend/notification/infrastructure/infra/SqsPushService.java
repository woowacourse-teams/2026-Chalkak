package com.chalkak.backend.notification.infrastructure.infra;

import com.chalkak.backend.common.logging.Elapsed;
import com.chalkak.backend.notification.service.PushMessage;
import com.chalkak.backend.notification.service.PushProcessingResult;
import com.chalkak.backend.notification.service.PushWorkerService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.slf4j.MDC;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageSystemAttributeName;
import tools.jackson.databind.ObjectMapper;

public class SqsPushService {
    private static final Duration VISIBILITY_EXTENSION_INTERVAL = Duration.ofSeconds(40);

    private final SqsClient sqsClient;
    private final String queueUrl;
    private final ObjectMapper objectMapper;
    private final PushWorkerService pushWorkerService;
    private final Clock clock;
    private final ScheduledExecutorService visibilityExtensionExecutor;

    public SqsPushService(
            SqsClient sqsClient,
            String queueUrl,
            ObjectMapper objectMapper,
            PushWorkerService pushWorkerService,
            Clock clock,
            ScheduledExecutorService visibilityExtensionExecutor
    ) {
        this.sqsClient = sqsClient;
        this.queueUrl = queueUrl;
        this.objectMapper = objectMapper;
        this.pushWorkerService = pushWorkerService;
        this.clock = clock;
        this.visibilityExtensionExecutor = visibilityExtensionExecutor;
    }

    public void poll() {
        Elapsed elapsed = Elapsed.start();
        Optional<Message> message;
        try {
            message = findMessage();
        } catch (RuntimeException exception) {
            SqsPushLog.logPollFailed(exception, elapsed);
            waitAfterPollingFailure();
            return;
        }
        long sqsReceiveDurationMs = elapsed.toMillis();
        try {
            if (message.isEmpty()) {
                SqsPushLog.logEmptyPoll(sqsReceiveDurationMs);
                return;
            }
            process(message.get(), sqsReceiveDurationMs);
        } catch (RuntimeException exception) {
            SqsPushLog.logProcessingPollFailed(
                    message,
                    exception,
                    sqsReceiveDurationMs
            );
            waitAfterPollingFailure();
        }
    }

    public void process(Message message) {
        process(message, null);
    }

    public void process(Message message, Long sqsReceiveDurationMs) {
        Elapsed elapsed = Elapsed.start();
        Instant receivedAt = clock.instant();
        String outcome = "FAILED";
        boolean receivedLogged = false;
        Map<String, String> previousContext = MDC.getCopyOfContextMap();
        MDC.put("sqsMessageId", message.messageId());
        MDC.put("receiveCount", message.attributes().get(
                MessageSystemAttributeName.APPROXIMATE_RECEIVE_COUNT));
        try (VisibilityExtension visibility = new VisibilityExtension(message)) {
            PushMessage pushMessage = readPushMessage(message);
            SqsPushLog.logReceived(pushMessage, sqsReceiveDurationMs);
            receivedLogged = true;
            outcome = processMessage(
                    message,
                    pushMessage,
                    visibility
            );
        } catch (RuntimeException exception) {
            outcome = "FAILED";
            SqsPushLog.logProcessingFailed(
                    exception,
                    sqsReceiveDurationMs,
                    receivedLogged
            );
            // 삭제하지 않는다. SQS가 다시 전달하거나 설정된 최대 수신 횟수 이후 DLQ로 이동한다.
        } finally {
            try {
                SqsPushLog.logAttemptCompleted(
                        message,
                        receivedAt,
                        elapsed,
                        outcome
                );
            } finally {
                MDC.clear();
                if (previousContext != null) {
                    MDC.setContextMap(previousContext);
                }
            }
        }
    }

    private void waitAfterPollingFailure() {
        try {
            TimeUnit.SECONDS.sleep(1);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private Optional<Message> findMessage() {
        List<Message> messages = sqsClient.receiveMessage(request -> request
                .queueUrl(queueUrl)
                .maxNumberOfMessages(1)
                .waitTimeSeconds(20)
                .visibilityTimeout(60)
                .messageSystemAttributeNames(
                        MessageSystemAttributeName.APPROXIMATE_RECEIVE_COUNT,
                        MessageSystemAttributeName.SENT_TIMESTAMP))
                .messages();

        if (messages.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(messages.getFirst());
    }

    private String processMessage(
            Message message,
            PushMessage pushMessage,
            VisibilityExtension visibility
    ) {
        if (pushMessage == null) {
            visibility.finishAttempt(() -> delete(message));
            return "INVALID_MESSAGE_DELETED";
        }
        PushProcessingResult result = pushWorkerService.process(pushMessage);
        visibility.finishAttempt(() -> handleProcessingResult(
                message,
                pushMessage,
                result
        ));
        if (result.retryable()) {
            return "RETRY_PENDING";
        }
        return "DELETED";
    }

    private PushMessage readPushMessage(Message message) {
        try {
            return objectMapper.readValue(message.body(), PushMessage.class);
        } catch (tools.jackson.core.JacksonException exception) {
            SqsPushLog.logInvalidMessage(exception);
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
        postponeRetry(
                message,
                pushMessage,
                result
        );
        SqsPushLog.logRetryPending(pushMessage, result.retryAfter());
    }

    private void extendVisibility(Message message) {
        try {
            sqsClient.changeMessageVisibility(request -> request.queueUrl(queueUrl)
                    .receiptHandle(message.receiptHandle()).visibilityTimeout(60)
                    .overrideConfiguration(config -> config.apiCallTimeout(Duration.ofSeconds(5))
                            .apiCallAttemptTimeout(Duration.ofSeconds(5))));
        } catch (RuntimeException exception) {
            SqsPushLog.logVisibilityFailed(message, exception);
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
                .visibilityTimeout((int) seconds)
                .overrideConfiguration(config -> config.apiCallTimeout(Duration.ofSeconds(5))
                        .apiCallAttemptTimeout(Duration.ofSeconds(5))));
    }

    private void delete(Message message) {
        Elapsed elapsed = Elapsed.start();
        try {
            sqsClient.deleteMessage(request -> request.queueUrl(queueUrl)
                    .receiptHandle(message.receiptHandle())
                    .overrideConfiguration(config -> config.apiCallTimeout(Duration.ofSeconds(5))
                            .apiCallAttemptTimeout(Duration.ofSeconds(5))));
        } catch (RuntimeException exception) {
            SqsPushLog.logDeleteFailed(elapsed);
            throw exception;
        }
        SqsPushLog.logDeleted(elapsed);
    }

    private final class VisibilityExtension implements AutoCloseable {
        private final Message message;
        private final ScheduledFuture<?> task;
        private State state = State.ACTIVE;

        private VisibilityExtension(Message message) {
            this.message = message;
            long intervalSeconds = VISIBILITY_EXTENSION_INTERVAL.getSeconds();
            task = visibilityExtensionExecutor.scheduleAtFixedRate(
                    this::extend,
                    intervalSeconds,
                    intervalSeconds,
                    TimeUnit.SECONDS
            );
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
