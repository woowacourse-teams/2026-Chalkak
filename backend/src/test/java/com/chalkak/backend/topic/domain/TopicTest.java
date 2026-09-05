package com.chalkak.backend.topic.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TopicTest {

    private static final Instant NOW = Instant.parse("2026-08-28T03:00:00Z");
    private static final Instant STARTS_AT = Instant.parse("2026-08-29T15:00:00Z");
    private static final Instant ENDS_AT = Instant.parse("2026-08-30T15:00:00Z");
    private static final LocalDate TOPIC_DATE = LocalDate.of(2026, 8, 30);

    @Test
    @DisplayName("공개 전 제목과 날짜·참여 기간으로 주제를 생성한다")
    void create_validState_createsBeforeOpenTopic() {
        Topic topic = Topic.create(
                "  오늘 가장 기억에 남은 순간  ",
                TOPIC_DATE,
                new ParticipationPeriod(STARTS_AT, ENDS_AT),
                NOW
        );

        assertThat(topic.getTitle()).isEqualTo("오늘 가장 기억에 남은 순간");
        assertThat(topic.getTopicDate()).isEqualTo(TOPIC_DATE);
        assertThat(topic.phaseAt(NOW)).isEqualTo(TopicPhase.BEFORE_OPEN);
    }

    @Test
    @DisplayName("주제 날짜는 참여 시작일의 한국 날짜와 일치해야 한다")
    void create_mismatchedTopicDate_throwsBusinessException() {
        assertThatThrownBy(() -> Topic.create(
                "오늘의 주제",
                TOPIC_DATE.minusDays(1),
                new ParticipationPeriod(STARTS_AT, ENDS_AT),
                NOW
        )).isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.BUSINESS_ERROR);
    }

    @Test
    @DisplayName("이미 공개가 시작된 기간으로 주제를 생성할 수 없다")
    void create_startedPeriod_throwsBusinessException() {
        assertThatThrownBy(() -> Topic.create(
                "오늘의 주제",
                LocalDate.of(2026, 8, 28),
                new ParticipationPeriod(NOW, NOW.plusSeconds(3600)),
                NOW
        )).isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("공개 전 주제의 제목과 날짜·기간을 수정한다")
    void update_beforeOpen_changesBusinessState() {
        Topic topic = topic();
        Instant newStartsAt = STARTS_AT.plusSeconds(86_400);
        Instant newEndsAt = ENDS_AT.plusSeconds(86_400);

        topic.update(
                "수정한 주제",
                TOPIC_DATE.plusDays(1),
                new ParticipationPeriod(newStartsAt, newEndsAt),
                NOW
        );

        assertThat(topic.getTitle()).isEqualTo("수정한 주제");
        assertThat(topic.getTopicDate()).isEqualTo(TOPIC_DATE.plusDays(1));
        assertThat(topic.getParticipationPeriod().getStartsAt()).isEqualTo(newStartsAt);
    }

    @Test
    @DisplayName("공개가 시작된 주제는 수정할 수 없다")
    void update_openTopic_throwsStateChanged() {
        Topic topic = topic();

        assertThatThrownBy(() -> topic.update(
                "수정한 주제",
                TOPIC_DATE,
                new ParticipationPeriod(STARTS_AT, ENDS_AT),
                STARTS_AT
        )).isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue(
                        "errorCode",
                        ErrorCode.RESOURCE_STATE_CHANGED);
    }

    @Test
    @DisplayName("공개 전 주제는 삭제 시각을 기록한다")
    void delete_beforeOpen_recordsDeletedAt() {
        Topic topic = topic();

        topic.delete(NOW);

        assertThat(topic.getDeletedAt()).isEqualTo(NOW);
        assertThat(topic.isDeleted()).isTrue();
    }

    @Test
    @DisplayName("공개가 끝난 주제는 삭제할 수 없다")
    void delete_closedTopic_throwsStateChanged() {
        Topic topic = topic();

        assertThatThrownBy(() -> topic.delete(ENDS_AT))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue(
                        "errorCode",
                        ErrorCode.RESOURCE_STATE_CHANGED);
    }

    private Topic topic() {
        return Topic.create(
                "오늘의 주제",
                TOPIC_DATE,
                new ParticipationPeriod(STARTS_AT, ENDS_AT),
                NOW
        );
    }
}
