package com.chalkak.backend.topic.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.chalkak.backend.topic.domain.Topic;
import com.chalkak.backend.topic.domain.TopicPhase;
import com.chalkak.backend.topic.repository.TopicRepository;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TopicRepositoryImpl.class)
class TopicRepositoryTest {

    private static final UUID TOPIC_ID = UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6570b2");
    private static final LocalDate TOPIC_DATE = LocalDate.of(2026, 8, 12);

    @Autowired
    private TopicRepository topicRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("""
                INSERT INTO topics (
                    id, title, topic_date, starts_at, ends_at, created_at, updated_at
                ) VALUES (
                    '0198f6c1-62ba-7d30-8b12-0f733b6570b2',
                    '오늘 가장 기억에 남은 순간',
                    '2026-08-12',
                    '2026-08-11T15:00:00Z',
                    '2026-08-12T15:00:00Z',
                    CURRENT_TIMESTAMP,
                    CURRENT_TIMESTAMP
                )
                """);
    }

    @Test
    @DisplayName("공개일로 주제를 조회한다")
    void findActiveByTopicDate_existingTopic_returnsTopic() {
        // When
        Optional<Topic> result = topicRepository.findActiveByTopicDate(TOPIC_DATE);

        // Then
        assertThat(result).isPresent();
        Topic topic = result.orElseThrow();
        assertThat(topic.getId()).isEqualTo(TOPIC_ID);
        assertThat(topic.getTitle()).isEqualTo("오늘 가장 기억에 남은 순간");
        assertThat(topic.getTopicDate()).isEqualTo(TOPIC_DATE);
        assertThat(topic.getParticipationPeriod().phaseAt(Instant.parse("2026-08-12T03:00:00Z")))
                .isEqualTo(TopicPhase.OPEN);
    }

    @Test
    @DisplayName("삭제된 주제는 조회하지 않는다")
    void findActiveByTopicDate_deletedTopic_returnsEmpty() {
        // Given
        jdbcTemplate.update(
                "UPDATE topics SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?",
                TOPIC_ID
        );
        entityManager.flush();
        entityManager.clear();

        // When
        Optional<Topic> result = topicRepository.findActiveByTopicDate(TOPIC_DATE);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("주제가 없는 날짜는 조회하지 않는다")
    void findActiveByTopicDate_noTopicOnDate_returnsEmpty() {
        // Given
        LocalDate dateWithoutTopic = LocalDate.of(2026, 8, 13);

        // When
        Optional<Topic> result = topicRepository.findActiveByTopicDate(dateWithoutTopic);

        // Then
        assertThat(result).isEmpty();
    }
}
