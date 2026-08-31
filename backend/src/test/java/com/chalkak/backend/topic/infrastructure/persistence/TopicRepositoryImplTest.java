package com.chalkak.backend.topic.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.topic.domain.Topic;
import java.sql.SQLException;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class TopicRepositoryImplTest {

    private static final String TOPIC_DATE_UNIQUE_INDEX =
            "ux_topics_topic_date_active";

    private final TopicJpaRepository topicJpaRepository = mock(TopicJpaRepository.class);
    private final TopicRepositoryImpl topicRepository = new TopicRepositoryImpl(topicJpaRepository);

    @Test
    @DisplayName("활성 주제 날짜 유니크 인덱스 위반만 날짜 중복 오류로 변환한다")
    void saveAndFlush_topicDateUniqueViolation_throwsBusinessException() {
        // Given
        Topic topic = mock(Topic.class);
        given(topicJpaRepository.saveAndFlush(topic))
                .willThrow(integrityViolation(TOPIC_DATE_UNIQUE_INDEX));

        // When
        BusinessException exception = catchThrowableOfType(
                BusinessException.class,
                () -> topicRepository.saveAndFlush(topic)
        );

        // Then
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BUSINESS_ERROR);
        assertThat(exception.getMessage()).isEqualTo("해당 날짜의 주제가 이미 존재합니다.");
    }

    @Test
    @DisplayName("알 수 없는 무결성 오류는 날짜 중복 오류로 변환하지 않고 전파한다")
    void saveAndFlush_unknownIntegrityViolation_propagatesOriginalException() {
        // Given
        Topic topic = mock(Topic.class);
        DataIntegrityViolationException databaseFailure =
                integrityViolation("topics_unknown_constraint");
        given(topicJpaRepository.saveAndFlush(topic)).willThrow(databaseFailure);

        // When & Then
        assertThatThrownBy(() -> topicRepository.saveAndFlush(topic))
                .isSameAs(databaseFailure);
    }

    private DataIntegrityViolationException integrityViolation(String constraintName) {
        ConstraintViolationException cause = new ConstraintViolationException(
                "database constraint violation",
                new SQLException(),
                constraintName
        );
        return new DataIntegrityViolationException("data integrity violation", cause);
    }
}
