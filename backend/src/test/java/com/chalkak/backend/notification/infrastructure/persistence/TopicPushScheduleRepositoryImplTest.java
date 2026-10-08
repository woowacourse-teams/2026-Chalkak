package com.chalkak.backend.notification.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.chalkak.backend.notification.domain.TopicPushSchedule;
import java.time.LocalTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PSQLException;
import org.postgresql.util.PSQLState;
import org.postgresql.util.ServerErrorMessage;
import org.springframework.dao.DataIntegrityViolationException;

class TopicPushScheduleRepositoryImplTest {

    private final TopicPushScheduleJpaRepository jpaRepository = mock(
            TopicPushScheduleJpaRepository.class);
    private final TopicPushScheduleRepositoryImpl repository = new TopicPushScheduleRepositoryImpl(
            jpaRepository);

    @Test
    @DisplayName("다른 제약조건 위반은 시각 중복으로 변환하지 않는다")
    void saveAndFlush_otherConstraintViolation_propagatesOriginalException() {
        // Given
        TopicPushSchedule schedule = new TopicPushSchedule(LocalTime.NOON);
        PSQLException cause = mock(PSQLException.class);
        ServerErrorMessage error = mock(ServerErrorMessage.class);
        given(cause.getServerErrorMessage()).willReturn(error);
        given(error.getConstraint()).willReturn("different_constraint");
        DataIntegrityViolationException failure = new DataIntegrityViolationException(
                "database constraint violation", cause);
        given(jpaRepository.saveAndFlush(schedule)).willThrow(failure);

        // When & Then
        assertThatThrownBy(() -> repository.saveAndFlush(schedule)).isSameAs(failure);
    }

    @Test
    @DisplayName("PostgreSQL 오류의 제약조건 정보가 없으면 원래 예외를 전달한다")
    void saveAndFlush_missingServerErrorMessage_propagatesOriginalException() {
        // Given
        TopicPushSchedule schedule = new TopicPushSchedule(LocalTime.NOON);
        PSQLException cause = new PSQLException("connection failed", PSQLState.CONNECTION_FAILURE);
        DataIntegrityViolationException failure = new DataIntegrityViolationException(
                "database failure", cause);
        given(jpaRepository.saveAndFlush(schedule)).willThrow(failure);

        // When & Then
        assertThatThrownBy(() -> repository.saveAndFlush(schedule)).isSameAs(failure);
    }

    @Test
    @DisplayName("PostgreSQL 원인이 아닌 무결성 오류는 원래 예외를 전달한다")
    void saveAndFlush_unknownIntegrityViolation_propagatesOriginalException() {
        // Given
        TopicPushSchedule schedule = new TopicPushSchedule(LocalTime.NOON);
        DataIntegrityViolationException failure = new DataIntegrityViolationException(
                "unexpected database failure");
        given(jpaRepository.saveAndFlush(schedule)).willThrow(failure);

        // When & Then
        assertThatThrownBy(() -> repository.saveAndFlush(schedule)).isSameAs(failure);
    }
}
