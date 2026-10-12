package com.chalkak.backend.notification.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chalkak.backend.exception.BusinessException;
import java.time.LocalTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.NullSource;

class TopicPushScheduleTest {

    @ParameterizedTest
    @ValueSource(strings = {"00:00:00", "12:00:00", "23:59:00"})
    @DisplayName("하루의 시작·중간·마지막 분에 설정을 생성할 수 있다")
    void constructor_validSendTime_createsSchedule(String time) {
        // When
        TopicPushSchedule schedule = new TopicPushSchedule(LocalTime.parse(time));

        // Then
        assertThat(schedule.getSendTime()).isEqualTo(LocalTime.parse(time));
    }

    @ParameterizedTest
    @ValueSource(strings = {"11:59:59", "12:00:01", "23:59:59"})
    @DisplayName("분 경계 전후의 초 단위 시각은 거절한다")
    void constructor_nonZeroSecond_throwsBusinessException(String time) {
        // When & Then
        assertThatThrownBy(() -> new TopicPushSchedule(LocalTime.parse(time)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("주제 푸시 발송 시각은 분 단위의 KST 시각이어야 합니다.");
    }

    @Test
    @DisplayName("발송 시각이 없으면 생성할 수 없다")
    void constructor_nullSendTime_throwsBusinessException() {
        // When & Then
        assertThatThrownBy(() -> new TopicPushSchedule(null))
                .isInstanceOf(BusinessException.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 999999999})
    @DisplayName("초 미만의 시각은 DB에서 반올림해 저장하지 않고 거절한다")
    void constructor_fractionalSecond_throwsBusinessException(int nanos) {
        // Given
        LocalTime time = LocalTime.NOON.withNano(nanos);

        // When & Then
        assertThatThrownBy(() -> new TopicPushSchedule(time))
                .isInstanceOf(BusinessException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"00:00:00", "12:00:00", "23:59:00"})
    @DisplayName("하루의 시작·중간·마지막 분으로 발송 시각을 수정할 수 있다")
    void updateSendTime_validTime_changesTime(String time) {
        // Given
        TopicPushSchedule schedule = new TopicPushSchedule(LocalTime.of(9, 0));

        // When
        schedule.updateSendTime(LocalTime.parse(time));

        // Then
        assertThat(schedule.getSendTime()).isEqualTo(LocalTime.parse(time));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"11:59:59", "12:00:01", "12:00:00.000000001", "12:00:00.999999999"})
    @DisplayName("잘못된 시각으로 수정하면 기존 시각을 유지한다")
    void updateSendTime_invalidTime_preservesTime(String time) {
        // Given
        TopicPushSchedule schedule = new TopicPushSchedule(LocalTime.of(9, 0));
        LocalTime sendTime = null;
        if (time != null) {
            sendTime = LocalTime.parse(time);
        }
        LocalTime invalidTime = sendTime;

        // When & Then
        assertThatThrownBy(() -> schedule.updateSendTime(invalidTime))
                .isInstanceOf(BusinessException.class);
        assertThat(schedule.getSendTime()).isEqualTo(LocalTime.of(9, 0));
    }
}
