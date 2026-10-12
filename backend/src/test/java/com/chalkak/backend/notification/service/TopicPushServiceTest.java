package com.chalkak.backend.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.chalkak.backend.notification.domain.NotificationSourceType;
import com.chalkak.backend.notification.domain.NotificationType;
import com.chalkak.backend.notification.infrastructure.infra.SqsPushMessagePublisher;
import com.chalkak.backend.notification.repository.TopicPushScheduleRepository;
import com.chalkak.backend.support.DatabaseCleaner;
import com.chalkak.backend.support.IntegrationTestSupport;
import com.chalkak.backend.topic.repository.TopicRepository;
import com.chalkak.backend.user.repository.UserRepository;
import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SqsException;
import tools.jackson.databind.json.JsonMapper;

class TopicPushServiceTest extends IntegrationTestSupport {

    private static final Instant NOW = Instant.parse("2026-10-12T00:00:00Z");
    private static final UUID USER_ID = new UUID(0, 1);
    private static final UUID TOPIC_ID = UUID.randomUUID();
    private static final UUID SCHEDULE_ID = UUID.randomUUID();

    @Autowired
    private TopicPushScheduleRepository schedules;
    @Autowired
    private TopicRepository topics;
    @Autowired
    private UserRepository users;
    @Autowired
    private PlatformTransactionManager manager;
    @Autowired
    private JdbcTemplate jdbc;

    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private PushMessagePublisher publisher;
    private FakeClock clock;
    private TopicPushService service;

    @BeforeEach
    void setUp() {
        new DatabaseCleaner(jdbc).clean();
        insertUser(USER_ID);
        insertTopic(TOPIC_ID, LocalDate.of(2026, 10, 12));
        insertSchedule(SCHEDULE_ID, LocalTime.of(9, 0), NOW.minusSeconds(3600));
        publisher = mock(PushMessagePublisher.class);
        given(publisher.publish(any())).willReturn(PushPublicationResult.accepted("sqs-message"));
        clock = new FakeClock(NOW);
        service = newService();
        appender.list.clear();
        appender.start();
        ((Logger) LoggerFactory.getLogger(TopicPushService.class)).addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        ((Logger) LoggerFactory.getLogger(TopicPushService.class)).detachAppender(appender);
        appender.stop();
        new DatabaseCleaner(jdbc).clean();
    }

    @Test
    @DisplayName("제출 여부·검수 설정과 무관하게 주제 수신 정상 회원에게 사용자별 메시지를 직접 발행한다")
    void publishDueTopics_normalRecipients_publishesWithoutInboxOrTransaction() {
        // Given
        for (int id = 2; id <= 5; id++) {
            insertUser(new UUID(0, id));
        }
        jdbc.update("UPDATE users SET moderation_push_enabled=false WHERE id=?", new UUID(0, 2));
        jdbc.update("UPDATE users SET status='BANNED' WHERE id=?", new UUID(0, 3));
        jdbc.update("UPDATE users SET deleted_at=? WHERE id=?", Timestamp.from(NOW),
                new UUID(0, 4));
        jdbc.update("UPDATE users SET topic_push_enabled=false WHERE id=?", new UUID(0, 5));
        UUID photoId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO photos(id,original_storage_key,thumbnail_storage_key)
                VALUES (?,'topic/original','topic/thumbnail')
                """, photoId);
        jdbc.update("""
                INSERT INTO posts(id,user_id,topic_id,photo_id,moderation_status)
                VALUES (?,?,?,?,'APPROVED')
                """, UUID.randomUUID(), USER_ID, TOPIC_ID, photoId);
        given(publisher.publish(any())).willAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return PushPublicationResult.accepted("sqs-message");
        });
        // When
        service.publishDueTopics();
        // Then
        List<PushMessage> messages = captureMessages(2);
        assertThat(messages).extracting(PushMessage::userId).containsExactly(USER_ID,
                new UUID(0, 2));
        assertThat(messages).extracting(PushMessage::eventId)
                .containsOnly(messages.getFirst().eventId());
        for (PushMessage message : messages) {
            assertThat(message.notificationId()).isNull();
            assertThat(message.type()).isEqualTo(NotificationType.TOPIC);
            assertThat(message.sourceType()).isEqualTo(NotificationSourceType.TOPIC);
            assertThat(message.sourceId()).isEqualTo(TOPIC_ID);
            assertThat(message.occurredAt()).isEqualTo(NOW);
            assertThat(message.expiresAt()).isEqualTo(NOW.plusSeconds(1800));
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notifications", Integer.class))
                .isZero();
        assertThat(summary()).containsEntry("targetCount", 2).containsEntry("acceptedCount", 2);
        assertThat(logsAtStage("topic_publication_result")).isEmpty();
        assertThat(logsAtStage("topic_publication_summary")).hasSize(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "deleted"})
    @DisplayName("오늘 주제가 없거나 삭제되면 해당 회차는 발행을 생략한다")
    void publishDueTopics_missingActiveTopic_skipsOccurrence(String state) {
        // Given
        if (state.equals("missing")) {
            jdbc.update("DELETE FROM topics");
        }
        if (state.equals("deleted")) {
            jdbc.update("UPDATE topics SET deleted_at=?", Timestamp.from(NOW));
        }
        // When
        service.publishDueTopics();
        service.publishDueTopics();
        // Then
        verifyNoInteractions(publisher);
    }

    @Test
    @DisplayName("수신 대상이 없으면 빈 회차를 기록하고 SQS를 호출하지 않는다")
    void publishDueTopics_noRecipients_recordsEmptySummary() {
        // Given
        jdbc.update("UPDATE users SET topic_push_enabled=false");
        // When
        service.publishDueTopics();
        // Then
        verifyNoInteractions(publisher);
        assertThat(summary()).containsEntry("targetCount", 0).containsEntry("acceptedCount", 0);
    }

    @Test
    @DisplayName("여러 시각을 회차별로 한 번 실행하고 다음 날 다시 실행한다")
    void publishDueTopics_multipleTimesAndDays_distinguishesOccurrences() {
        // Given
        insertSchedule(UUID.randomUUID(), LocalTime.of(9, 10), NOW.minusSeconds(3600));
        clock.set(NOW.plusSeconds(600));
        // When
        service.publishDueTopics();
        service.publishDueTopics();
        insertTopic(UUID.randomUUID(), LocalDate.of(2026, 10, 13));
        clock.set(NOW.plusSeconds(86400 + 600));
        service.publishDueTopics();
        // Then
        List<PushMessage> messages = captureMessages(4);
        assertThat(messages).extracting(PushMessage::occurredAt).containsExactly(
                NOW, NOW.plusSeconds(600), NOW.plusSeconds(86400), NOW.plusSeconds(87000));
        assertThat(messages).extracting(PushMessage::eventId).doesNotHaveDuplicates();
        assertThat(messages.getFirst().expiresAt()).isEqualTo(NOW.plusSeconds(1800));
    }

    @ParameterizedTest
    @ValueSource(longs = {1799, 1800, 1801})
    @DisplayName("늦게 실행해도 원래 예정 시각의 기한을 유지하고 30분 정각부터 생략한다")
    void publishDueTopics_delayedExecution_observesOriginalDeadline(long seconds) {
        // Given
        clock.set(NOW.plusSeconds(seconds));
        // When
        service.publishDueTopics();
        // Then
        if (seconds >= 1800) {
            verifyNoInteractions(publisher);
            return;
        }
        assertThat(captureMessages(1).getFirst().expiresAt()).isEqualTo(NOW.plusSeconds(1800));
    }

    @ParameterizedTest
    @ValueSource(strings = {"created", "updated"})
    @DisplayName("이미 지난 시각으로 생성·수정한 설정은 소급 실행하지 않고 다음 날 실행한다")
    void publishDueTopics_pastSettingChange_appliesNextDay(String change) {
        // Given
        if (change.equals("created")) {
            jdbc.update("DELETE FROM topic_push_schedules");
            insertSchedule(SCHEDULE_ID, LocalTime.of(9, 0), NOW.plusSeconds(1));
        }
        if (change.equals("updated")) {
            jdbc.update("UPDATE topic_push_schedules SET updated_at=?",
                    Timestamp.from(NOW.plusSeconds(1)));
        }
        clock.set(NOW.plusSeconds(300));
        // When
        service.publishDueTopics();
        // Then
        verifyNoInteractions(publisher);
        UUID tomorrowTopic = UUID.randomUUID();
        insertTopic(tomorrowTopic, LocalDate.of(2026, 10, 13));
        clock.set(NOW.plusSeconds(86400));
        service.publishDueTopics();
        PushMessage message = captureMessages(1).getFirst();
        assertThat(message.sourceId()).isEqualTo(tomorrowTopic);
        assertThat(message.occurredAt()).isEqualTo(NOW.plusSeconds(86400));
    }

    @Test
    @DisplayName("앞으로 도래하는 시각으로 수정하면 당일 새 시각부터 실행한다")
    void publishDueTopics_futureTimeUpdate_appliesUpcomingTime() {
        // Given
        jdbc.update("UPDATE topic_push_schedules SET send_time=?,updated_at=?",
                Time.valueOf("09:10:00"),
                Timestamp.from(NOW.plusSeconds(300)));
        clock.set(NOW.plusSeconds(300));
        // When & Then
        service.publishDueTopics();
        verifyNoInteractions(publisher);
        clock.set(NOW.plusSeconds(600));
        service.publishDueTopics();
        assertThat(captureMessages(1).getFirst().occurredAt()).isEqualTo(NOW.plusSeconds(600));
    }

    @Test
    @DisplayName("삭제된 설정은 실행하지 않고 이미 발행한 메시지의 기한은 바꾸지 않는다")
    void publishDueTopics_deletedSetting_preservesPublishedMessageOnly() {
        // When
        service.publishDueTopics();
        jdbc.update("DELETE FROM topic_push_schedules");
        service.publishDueTopics();
        // Then
        assertThat(captureMessages(1).getFirst().expiresAt()).isEqualTo(NOW.plusSeconds(1800));
    }

    @ParameterizedTest
    @ValueSource(strings = {"deleted", "updated"})
    @DisplayName("설정 목록을 읽은 뒤에도 회차 실행 전에 삭제·수정된 설정을 다시 확인한다")
    void publishDueTopics_scheduleChangesBeforeExecution_skipsStaleSnapshot(String change) {
        // Given
        UUID laterId = UUID.randomUUID();
        insertSchedule(laterId, LocalTime.of(9, 5), NOW.minusSeconds(3600));
        clock.set(NOW.plusSeconds(300));
        given(publisher.publish(any())).willAnswer(call -> {
            if (change.equals("deleted")) {
                jdbc.update("DELETE FROM topic_push_schedules WHERE id=?", laterId);
            }
            if (change.equals("updated")) {
                jdbc.update("UPDATE topic_push_schedules SET send_time=?,updated_at=? WHERE id=?",
                        Time.valueOf("10:00:00"), Timestamp.from(clock.instant()), laterId);
            }
            return PushPublicationResult.accepted("sqs-message");
        });
        // When
        service.publishDueTopics();
        // Then
        assertThat(captureMessages(1).getFirst().occurredAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("UTC 날짜가 달라도 KST 오늘 주제와 자정 설정을 사용한다")
    void publishDueTopics_kstMidnight_usesKstDate() {
        // Given
        UUID midnightTopic = UUID.randomUUID();
        insertTopic(midnightTopic, LocalDate.of(2026, 10, 13));
        Instant midnight = Instant.parse("2026-10-12T15:00:00Z");
        insertSchedule(UUID.randomUUID(), LocalTime.MIDNIGHT, midnight.minusSeconds(60));
        clock.set(midnight);
        // When
        service.publishDueTopics();
        // Then
        PushMessage message = captureMessages(1).getFirst();
        assertThat(message.sourceId()).isEqualTo(midnightTopic);
        assertThat(message.occurredAt()).isEqualTo(midnight);
    }

    @Test
    @DisplayName("한 회원의 실패가 다른 회원을 중단시키지 않고 실패·결과 불명을 구분하며 재발행하지 않는다")
    void publishDueTopics_mixedResults_continuesAndRecordsWithoutRepublishing() {
        // Given
        for (int id = 2; id <= 4; id++) {
            insertUser(new UUID(0, id));
        }
        given(publisher.publish(any())).willReturn(PushPublicationResult.accepted("sqs-message"),
                PushPublicationResult.retry("RequestThrottled"),
                PushPublicationResult.unknown("SQS_CLIENT_ERROR"))
                .willThrow(new IllegalStateException("private-message-must-not-be-logged"));
        // When
        service.publishDueTopics();
        service.publishDueTopics();
        // Then
        List<PushMessage> messages = captureMessages(4);
        assertThat(messages).hasSize(4);
        assertThat(summary()).containsEntry("targetCount", 4).containsEntry("acceptedCount", 1)
                .containsEntry("failedCount", 1).containsEntry("unknownCount", 2)
                .containsEntry("expiredCount", 0);
        List<Map<String, Object>> failures = logsAtStage("topic_publication_result");
        assertThat(failures).hasSize(2);
        assertThat(failures.getFirst()).containsEntry("userId", new UUID(0, 2))
                .containsEntry("result", "FAILED")
                .containsEntry("errorCode", "RequestThrottled");
        assertThat(failures.get(1)).containsEntry("userId", new UUID(0, 3))
                .containsEntry("result", "UNKNOWN")
                .containsEntry("errorCode", "SQS_CLIENT_ERROR");
        assertThat(failures).allSatisfy(log -> assertThat(log)
                .containsEntry("eventId", messages.getFirst().eventId())
                .containsEntry("sourceId", TOPIC_ID));
        assertThat(logsAtStage("topic_publication_unknown")).singleElement()
                .satisfies(log -> assertThat(log).containsEntry("userId", new UUID(0, 4))
                        .containsEntry("errorCode", "IllegalStateException"));
        assertThat(logsAtStage("topic_publication_summary")).hasSize(1);
        assertThat(appender.list).extracting(ILoggingEvent::getFormattedMessage)
                .noneMatch(message -> message.contains("private-message-must-not-be-logged"));
    }

    @ParameterizedTest
    @CsvSource({"503, InternalError, 1, 0", "429, TooManyRequests, 0, 1",
            "403, AccessDenied, 0, 1"})
    @DisplayName("SQS 서버 오류는 수락 불명으로, 명시적 거절은 실패로 집계하고 재발행하지 않는다")
    void publishDueTopics_sqsError_classifiesOutcomeWithoutRepublishing(
            int status,
            String errorCode,
            int unknownCount,
            int failedCount
    ) {
        // Given
        SqsClient client = mock(SqsClient.class);
        given(client.sendMessage(any(SendMessageRequest.class))).willThrow(SqsException.builder()
                .statusCode(status)
                .awsErrorDetails(AwsErrorDetails.builder().errorCode(errorCode).build()).build());
        publisher = new SqsPushMessagePublisher(client, "test-queue", JsonMapper.builder().build());
        service = newService();

        // When
        service.publishDueTopics();
        service.publishDueTopics();

        // Then
        assertThat(summary()).containsEntry("acceptedCount", 0)
                .containsEntry("unknownCount", unknownCount)
                .containsEntry("failedCount", failedCount);
        verify(client).sendMessage(any(SendMessageRequest.class));
    }

    @Test
    @DisplayName("회차 발행 중 기한에 도달하면 남은 회원은 생략하고 다시 발행하지 않는다")
    void publishDueTopics_deadlineDuringPublication_skipsRemainingRecipients() {
        // Given
        insertUser(new UUID(0, 2));
        insertUser(new UUID(0, 3));
        given(publisher.publish(any())).willAnswer(call -> {
            clock.set(NOW.plusSeconds(1800));
            return PushPublicationResult.accepted("sqs-message");
        });
        // When
        service.publishDueTopics();
        service.publishDueTopics();
        // Then
        assertThat(captureMessages(1)).hasSize(1);
        assertThat(summary()).containsEntry("acceptedCount", 1).containsEntry("expiredCount", 2);
    }

    @Test
    @DisplayName("실행 중단 후 같은 인스턴스는 재발행하지 않고 재시작하면 중복 가능성이 남는다")
    void publishDueTopics_interruptedOccurrence_preservesBestEffortLimit() {
        // Given
        insertUser(new UUID(0, 2));
        insertUser(new UUID(0, 3));
        given(publisher.publish(any())).willReturn(PushPublicationResult.accepted("sqs-message"))
                .willThrow(new AssertionError("simulated process interruption"));
        // When & Then
        assertThatThrownBy(service::publishDueTopics).isInstanceOf(AssertionError.class);
        service.publishDueTopics();
        assertThat(captureMessages(2)).hasSize(2);
        doReturn(PushPublicationResult.accepted("sqs-message")).when(publisher).publish(any());
        newService().publishDueTopics();
        List<PushMessage> messages = captureMessages(5);
        assertThat(messages.get(2).userId()).isEqualTo(messages.getFirst().userId());
        assertThat(messages.get(2).occurredAt()).isEqualTo(messages.getFirst().occurredAt());
        assertThat(messages.get(2).eventId()).isNotEqualTo(messages.getFirst().eventId());
    }

    private TopicPushService newService() {
        return new TopicPushService(schedules, topics, users, publisher, clock, manager);
    }

    private List<PushMessage> captureMessages(int count) {
        ArgumentCaptor<PushMessage> messages = ArgumentCaptor.forClass(PushMessage.class);
        verify(publisher, times(count)).publish(messages.capture());
        return messages.getAllValues();
    }

    private Map<String, Object> summary() {
        List<Map<String, Object>> summaries = logsAtStage("topic_publication_summary");
        if (summaries.isEmpty()) {
            throw new AssertionError("주제 발행 집계 로그가 없습니다.");
        }
        return summaries.getFirst();
    }

    private List<Map<String, Object>> logsAtStage(String stage) {
        List<Map<String, Object>> logs = new ArrayList<>();
        for (ILoggingEvent event : appender.list) {
            Map<String, Object> values = new HashMap<>();
            for (var entry : event.getKeyValuePairs()) {
                values.put(entry.key, entry.value);
            }
            if (stage.equals(values.get("stage"))) {
                logs.add(values);
            }
        }
        return logs;
    }

    private void insertUser(UUID id) {
        jdbc.update("""
                INSERT INTO users(id,email,status,signature_original_storage_key)
                VALUES (?,?,'ACTIVE',?)
                """, id, id + "@topic-test.example", "topic/signature/" + id);
    }

    private void insertTopic(UUID id, LocalDate date) {
        Instant startsAt = date.atStartOfDay(ZoneId.of("Asia/Seoul")).toInstant();
        jdbc.update(
                "INSERT INTO topics(id,title,topic_date,starts_at,ends_at) VALUES (?,'빛과 그림자',?,?,?)",
                id, Date.valueOf(date), Timestamp.from(startsAt),
                Timestamp.from(startsAt.plusSeconds(86400)));
    }

    private void insertSchedule(UUID id, LocalTime sendTime, Instant updatedAt) {
        jdbc.update(
                "INSERT INTO topic_push_schedules(id,send_time,created_at,updated_at) VALUES (?,?,?,?)",
                id, Time.valueOf(sendTime), Timestamp.from(updatedAt), Timestamp.from(updatedAt));
    }

    private static class FakeClock extends Clock {
        private Instant now;

        FakeClock(Instant now) {
            this.now = now;
        }

        void set(Instant now) {
            this.now = now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(now, zone);
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
