package com.chalkak.backend.topic.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.exception.NotFoundException;
import com.chalkak.backend.support.IntegrationTestSupport;
import com.chalkak.backend.topic.domain.TopicPhase;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class TopicQueryServiceTest extends IntegrationTestSupport {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final UUID TOPIC_ID = UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6570b2");
    private static final String TITLE = "오늘 가장 기억에 남은 순간";

    @Autowired
    private TopicQueryService topicQueryService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private LocalDate today;

    @BeforeEach
    void setUp() {
        today = LocalDate.now(KST);
        insertTopic(TOPIC_ID, today);
    }

    private void insertTopic(UUID id, LocalDate topicDate) {
        Instant startsAt = topicDate.atStartOfDay(KST).toInstant();
        Instant endsAt = topicDate.plusDays(1).atStartOfDay(KST).toInstant();

        jdbcTemplate.update("""
                INSERT INTO topics (
                    id, title, topic_date, starts_at, ends_at, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                id, TITLE, topicDate, Timestamp.from(startsAt), Timestamp.from(endsAt));
    }

    @Test
    @DisplayName("공개일로 주제를 조회한다")
    void getTopic_openedTopic_returnsTopicDetail() {
        // When
        TopicDetail detail = topicQueryService.getTopic(today);

        // Then
        assertThat(detail.id()).isEqualTo(TOPIC_ID);
        assertThat(detail.title()).isEqualTo(TITLE);
        assertThat(detail.topicDate()).isEqualTo(today);
        assertThat(detail.phase()).isEqualTo(TopicPhase.OPEN);
    }

    @Test
    @DisplayName("오늘보다 미래의 주제는 조회할 수 없다")
    void getTopic_futureDate_throwsBusinessException() {
        // Given
        LocalDate tomorrow = today.plusDays(1);

        // When
        BusinessException exception = catchThrowableOfType(
                BusinessException.class,
                () -> topicQueryService.getTopic(tomorrow)
        );

        // Then
        assertThat(exception).isNotNull();
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BUSINESS_ERROR);
        assertThat(exception.getMessage()).isEqualTo("미래의 주제는 조회할 수 없습니다.");
    }

    @Test
    @DisplayName("주제가 없는 날짜는 조회할 수 없다")
    void getTopic_noTopicOnDate_throwsNotFoundException() {
        // Given
        LocalDate dateWithoutTopic = today.minusDays(1);

        // When
        NotFoundException exception = catchThrowableOfType(
                NotFoundException.class,
                () -> topicQueryService.getTopic(dateWithoutTopic)
        );

        // Then
        assertThat(exception).isNotNull();
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BUSINESS_ERROR);
        assertThat(exception.getMessage()).isEqualTo("주제를 찾을 수 없습니다.");
    }
}
