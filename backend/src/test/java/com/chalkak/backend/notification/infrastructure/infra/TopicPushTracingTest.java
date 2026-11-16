package com.chalkak.backend.notification.infrastructure.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.chalkak.backend.notification.domain.NotificationSourceType;
import com.chalkak.backend.notification.domain.NotificationType;
import com.chalkak.backend.notification.domain.PushEventIdPolicy;
import com.chalkak.backend.notification.domain.TopicPushSchedule;
import com.chalkak.backend.notification.repository.NotificationRepository;
import com.chalkak.backend.notification.repository.TopicPushScheduleRepository;
import com.chalkak.backend.notification.service.DevicePushRequest;
import com.chalkak.backend.notification.service.DevicePushTiming;
import com.chalkak.backend.notification.service.DirectPushPublisher;
import com.chalkak.backend.notification.service.PushMessage;
import com.chalkak.backend.notification.service.PushProcessingResult;
import com.chalkak.backend.notification.service.PushWorkerService;
import com.chalkak.backend.notification.service.TopicPushService;
import com.chalkak.backend.notification.service.TopicPushTracing;
import com.chalkak.backend.topic.domain.Topic;
import com.chalkak.backend.topic.repository.TopicRepository;
import com.google.firebase.messaging.FirebaseMessaging;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class TopicPushTracingTest {

    private static final Instant NOW = Instant.parse("2026-11-16T10:00:00Z");
    private static final UUID SCHEDULE_ID = UUID.randomUUID();
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final String QUEUE_URL = "https://sqs.ap-northeast-2.amazonaws.com/000000000000/dev";

    private final ObjectMapper mapper = JsonMapper.builder().findAndAddModules().build();
    private final SqsClient sqs = mock(SqsClient.class);
    private final PushWorkerService worker = mock(PushWorkerService.class);
    private final List<SpanData> spans = new ArrayList<>();
    private final PushMessage push = new PushMessage(
            PushEventIdPolicy.createForOccurrence(
                    NotificationType.TOPIC,
                    SCHEDULE_ID,
                    NOW
            ),
            null,
            UUID.randomUUID(),
            NotificationType.TOPIC,
            NotificationSourceType.TOPIC,
            UUID.randomUUID(),
            NOW,
            NOW.plusSeconds(1800)
    );

    @Test
    @DisplayName("대상 조회부터 SQS 재수신 및 FCM 호출까지 같은 트레이스로 이어진다")
    void process_topicMessageFromPublisher_restoresTraceAcrossAttempts() throws Exception {
        // Given
        try (SdkTracerProvider provider = createProvider()) {
            TopicPushTracing tracing = new OpenTelemetryTopicPushTracing(
                    provider.get("test"));
            SqsPushMessagePublisher publisher = new SqsPushMessagePublisher(
                    sqs,
                    QUEUE_URL,
                    mapper,
                    tracing
            );
            FirebaseMessaging firebase = mock(FirebaseMessaging.class);
            when(firebase.send(any(com.google.firebase.messaging.Message.class)))
                    .thenAnswer(invocation -> {
                        assertThat(MDC.get("trace_id")).hasSize(32);
                        assertThat(MDC.get("span_id")).hasSize(16);
                        return "accepted";
                    });
            FcmDevicePushSender sender = new FcmDevicePushSender(
                    firebase,
                    CLOCK,
                    tracing
            );
            when(worker.process(push)).thenAnswer(invocation -> {
                assertThat(sender.send(new DevicePushRequest(
                        push,
                        "test-token",
                        "test-title",
                        "test-body",
                        push.expiresAt()
                ), new DevicePushTiming()).isAccepted()).isTrue();
                return PushProcessingResult.completed();
            });
            when(sqs.sendMessage(any(SendMessageRequest.class))).thenReturn(
                    SendMessageResponse.builder().messageId("sqs-id").build());

            // When
            createTopicService(publisher, tracing).publishDueTopics();
            ArgumentCaptor<SendMessageRequest> request = ArgumentCaptor
                    .forClass(SendMessageRequest.class);
            verify(sqs).sendMessage(request.capture());
            Message message = Message.builder().messageId("sqs-id").receiptHandle("receipt")
                    .messageAttributes(request.getValue().messageAttributes())
                    .body(request.getValue().messageBody()).build();
            SqsPushService receiver = createReceiver(tracing);
            receiver.process(message);
            receiver.process(message);

            // Then
            SpanData occurrence = spans.stream()
                    .filter(span -> span.getName().equals("Run topic notification"))
                    .findFirst().orElseThrow();
            SpanData lookup = spans.stream()
                    .filter(span -> span.getName().equals("Find topic notification targets"))
                    .findFirst().orElseThrow();
            SpanData publish = spans.stream()
                    .filter(span -> span.getName().equals("Publish topic notification to SQS"))
                    .findFirst().orElseThrow();
            List<SpanData> attempts = spans.stream()
                    .filter(span -> span.getName().equals("Process topic notification from SQS"))
                    .toList();
            List<SpanData> fcm = spans.stream()
                    .filter(span -> span.getName().equals("Send topic notification via FCM"))
                    .toList();
            assertThat(request.getValue().messageAttributes().get("traceparent").stringValue())
                    .isEqualTo("00-" + publish.getTraceId() + "-" + publish.getSpanId() + "-"
                            + publish.getSpanContext().getTraceFlags().asHex());
            assertThat(spans).hasSize(7).allSatisfy(
                    span -> assertThat(span.getTraceId()).isEqualTo(occurrence.getTraceId()));
            assertThat(lookup.getParentSpanId()).isEqualTo(occurrence.getSpanId());
            assertThat(lookup.getEndEpochNanos()).isLessThanOrEqualTo(publish.getStartEpochNanos());
            assertThat(publish.getParentSpanId()).isEqualTo(occurrence.getSpanId());
            assertThat(attempts).hasSize(2)
                    .allSatisfy(span -> assertThat(span.getParentSpanId())
                            .isEqualTo(publish.getSpanId()));
            assertThat(fcm).extracting(SpanData::getParentSpanId)
                    .containsExactlyElementsOf(attempts.stream().map(SpanData::getSpanId).toList());
            assertThat(attempts).extracting(SpanData::getSpanId).doesNotHaveDuplicates();
            assertThat(MDC.get("trace_id")).isNull();
            assertThat(MDC.get("span_id")).isNull();
        }
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = "invalid-traceparent")
    @DisplayName("트레이스 헤더가 없거나 잘못되어도 기존 메시지는 정상적으로 처리한다")
    void process_messageWithoutValidTrace_preservesProcessing(String traceparent) {
        // Given
        try (SdkTracerProvider provider = createProvider()) {
            TopicPushTracing tracing = new OpenTelemetryTopicPushTracing(
                    provider.get("test"));
            when(worker.process(push)).thenReturn(PushProcessingResult.completed());
            Map<String, MessageAttributeValue> attributes = traceparent == null
                    ? Map.of()
                    : Map.of(
                            "traceparent", MessageAttributeValue.builder().dataType("String")
                                    .stringValue(traceparent).build());

            // When
            createReceiver(tracing).process(createMessage(attributes));

            // Then
            verify(worker).process(push);
            assertThat(spans).isEmpty();
        }
    }

    @Test
    @DisplayName("개발 설정에서 완료된 스팬을 별도 스레드가 추적용 로그로 내보낸다")
    void trace_devConfiguration_exportsSpanWithTraceId() {
        // Given
        Logger logger = (Logger) LoggerFactory.getLogger(TopicPushTracingConfiguration.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>() {
            @Override
            protected void append(ILoggingEvent event) {
                event.prepareForDeferredProcessing();
                super.append(event);
            }
        };
        appender.start();
        logger.addAppender(appender);
        try {
            new ApplicationContextRunner()
                    .withInitializer(context -> context.getEnvironment().setActiveProfiles("dev"))
                    .withUserConfiguration(TopicPushTracingConfiguration.class)
                    .run(context -> {
                        // When
                        String traceId;
                        try (var occurrence = context.getBean(TopicPushTracing.class)
                                .startOccurrence(push.eventId())) {
                            traceId = MDC.get("trace_id");
                            occurrence.recordOutcome("FINISHED", false);
                        }
                        CompletableResultCode flush = context.getBean(SdkTracerProvider.class)
                                .forceFlush();
                        flush.join(5, TimeUnit.SECONDS);

                        // Then
                        assertThat(flush.isSuccess()).isTrue();
                        assertThat(appender.list).singleElement().satisfies(log -> {
                            assertThat(log.getThreadName())
                                    .isNotEqualTo(Thread.currentThread().getName());
                            assertThat(log.getKeyValuePairs()).anySatisfy(field -> {
                                assertThat(field.key).isEqualTo("trace_id");
                                assertThat(field.value).isEqualTo(traceId);
                            });
                        });
                    });
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    private TopicPushService createTopicService(
            SqsPushMessagePublisher publisher,
            TopicPushTracing tracing
    ) {
        TopicPushSchedule schedule = mock(TopicPushSchedule.class);
        when(schedule.getId()).thenReturn(SCHEDULE_ID);
        when(schedule.findDueOccurrence(NOW)).thenReturn(NOW);
        TopicPushScheduleRepository schedules = mock(TopicPushScheduleRepository.class);
        when(schedules.findAllByOrderBySendTimeAsc()).thenReturn(List.of(schedule));
        when(schedules.findById(SCHEDULE_ID)).thenReturn(Optional.of(schedule));
        Topic topic = mock(Topic.class);
        when(topic.getId()).thenReturn(push.sourceId());
        TopicRepository topics = mock(TopicRepository.class);
        LocalDate topicDate = LocalDate.ofInstant(NOW, ZoneId.of("Asia/Seoul"));
        when(topics.findActiveByTopicDate(topicDate))
                .thenReturn(Optional.of(topic));
        NotificationRepository notifications = mock(NotificationRepository.class);
        when(notifications.findTopicPushEnabledNonParticipantIds(push.sourceId()))
                .thenReturn(List.of(push.userId()));
        PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
        when(transactions.getTransaction(any(TransactionDefinition.class)))
                .thenAnswer(invocation -> new SimpleTransactionStatus());
        return new TopicPushService(
                schedules,
                topics,
                notifications,
                new DirectPushPublisher(publisher, CLOCK),
                CLOCK,
                transactions,
                tracing
        );
    }

    private SdkTracerProvider createProvider() {
        SpanExporter exporter = mock(SpanExporter.class);
        when(exporter.export(any())).thenAnswer(invocation -> {
            spans.addAll(invocation.getArgument(0));
            return CompletableResultCode.ofSuccess();
        });
        when(exporter.flush()).thenReturn(CompletableResultCode.ofSuccess());
        when(exporter.shutdown()).thenReturn(CompletableResultCode.ofSuccess());
        return SdkTracerProvider.builder().addSpanProcessor(SimpleSpanProcessor.create(exporter))
                .build();
    }

    private Message createMessage(Map<String, MessageAttributeValue> attributes) {
        return Message.builder().messageId("sqs-id").receiptHandle("receipt")
                .messageAttributes(attributes).body(mapper.writeValueAsString(push)).build();
    }

    private SqsPushService createReceiver(TopicPushTracing tracing) {
        ScheduledExecutorService executor = mock(ScheduledExecutorService.class);
        doReturn(mock(ScheduledFuture.class)).when(executor).scheduleAtFixedRate(
                any(Runnable.class),
                anyLong(),
                anyLong(),
                any(TimeUnit.class)
        );
        return new SqsPushService(
                sqs,
                QUEUE_URL,
                mapper,
                worker,
                CLOCK,
                executor,
                tracing
        );
    }
}
