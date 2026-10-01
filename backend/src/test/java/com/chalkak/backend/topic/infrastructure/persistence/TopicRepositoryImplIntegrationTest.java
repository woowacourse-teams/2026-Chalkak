package com.chalkak.backend.topic.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.support.IntegrationTestSupport;
import com.chalkak.backend.topic.domain.ParticipationPeriod;
import com.chalkak.backend.topic.domain.Topic;
import com.chalkak.backend.topic.repository.TopicRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class TopicRepositoryImplIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private TopicRepository topicRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("실제 DB의 참여 기간 겹침 제약 위반을 관리자 안내 오류로 변환한다")
    void saveAndFlush_overlappingPeriodInDatabase_throwsBusinessException() {
        // Given
        jdbcTemplate.update(
                """
                        INSERT INTO topics (id, title, topic_date, starts_at, ends_at, created_at, updated_at)
                        VALUES (?, '기존 주제', DATE '2026-08-30',
                                TIMESTAMPTZ '2026-08-29 15:00:00+00',
                                TIMESTAMPTZ '2026-08-30 15:00:00+00',
                                CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                        """,
                UUID.fromString("0198fd20-0000-7000-8000-000000000011"));
        Topic overlappingTopic = Topic.create(
                "겹치는 주제",
                LocalDate.of(2026, 8, 29),
                new ParticipationPeriod(
                        Instant.parse("2026-08-29T14:00:00Z"),
                        Instant.parse("2026-08-29T16:00:00Z")),
                Instant.parse("2026-08-28T03:00:00Z"));

        // When
        BusinessException exception = catchThrowableOfType(
                BusinessException.class,
                () -> topicRepository.saveAndFlush(overlappingTopic));

        // Then
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BUSINESS_ERROR);
        assertThat(exception.getMessage()).isEqualTo("다른 주제의 참여 기간과 겹칩니다.");
    }
}
