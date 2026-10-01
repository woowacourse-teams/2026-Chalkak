package com.chalkak.backend.admin.infrastructure.persistence.topic;

import static org.assertj.core.api.Assertions.assertThat;

import com.chalkak.backend.admin.repository.AdminPage;
import com.chalkak.backend.admin.repository.topic.AdminTopicProjection;
import com.chalkak.backend.admin.repository.topic.AdminTopicQueryCriteria;
import com.chalkak.backend.admin.repository.topic.AdminTopicQueryRepository;
import com.chalkak.backend.admin.repository.topic.AdminTopicSort;
import com.chalkak.backend.topic.domain.TopicPhase;
import jakarta.persistence.EntityManager;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(AdminTopicQueryRepositoryImpl.class)
class AdminTopicQueryRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-08-20T00:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 8, 20);
    private static final UUID CLOSED_ID = new UUID(0, 1);
    private static final UUID OPEN_ID = new UUID(0, 2);
    private static final UUID FUTURE_ID = new UUID(0, 3);
    private static final UUID DELETED_ID = new UUID(0, 4);

    @Autowired
    private AdminTopicQueryRepository repository;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private EntityManager entityManager;

    @BeforeEach
    void setUp() {
        insertTopic(CLOSED_ID, -1, NOW.minusSeconds(3600), null);
        insertTopic(OPEN_ID, 0, NOW.minusSeconds(3600), null);
        insertTopic(FUTURE_ID, 1, NOW.minusSeconds(7200), null);
        insertTopic(DELETED_ID, 2, NOW, NOW);
        entityManager.flush();
        entityManager.clear();
    }

    @ParameterizedTest
    @MethodSource("sortCases")
    @DisplayName("네 정렬은 ID 보조 정렬과 다음 페이지 경계를 유지한다")
    void findTopics_eachSort_returnsStablePages(AdminTopicSort sort, List<UUID> expected) {
        // Given
        AdminTopicQueryCriteria criteria = new AdminTopicQueryCriteria(null, null, null, sort, NOW);

        // When
        AdminPage<AdminTopicProjection> first = repository.findTopics(criteria, 1, 2);
        AdminPage<AdminTopicProjection> second = repository.findTopics(criteria, 2, 2);

        // Then
        assertThat(first.items()).extracting(AdminTopicProjection::topicId)
                .containsExactlyElementsOf(expected.subList(0, 2));
        assertThat(second.items()).extracting(AdminTopicProjection::topicId)
                .containsExactly(expected.get(2));
        assertThat(first.hasNext()).isTrue();
        assertThat(second.hasNext()).isFalse();
        assertThat(second.currentPage()).isEqualTo(2);
        assertThat(second.pageSize()).isEqualTo(2);
    }

    @ParameterizedTest
    @MethodSource("phaseCases")
    @DisplayName("시작 시각은 공개 중이고 종료 시각은 종료로 조회한다")
    void findTopics_phaseAtBoundary_returnsMatchingTopic(TopicPhase phase, UUID expected) {
        // Given
        AdminTopicQueryCriteria criteria = new AdminTopicQueryCriteria(
                phase, null, null, AdminTopicSort.TOPIC_DATE_ASC, NOW);

        // When
        AdminPage<AdminTopicProjection> result = repository.findTopics(criteria, 1, 20);

        // Then
        assertThat(result.items()).extracting(AdminTopicProjection::topicId)
                .containsExactly(expected);
    }

    @Test
    @DisplayName("날짜 양 끝 경계를 포함하고 각각의 단독 조건도 적용한다")
    void findTopics_dateBounds_returnsInclusiveRange() {
        // Given
        AdminTopicQueryCriteria fromOnly = new AdminTopicQueryCriteria(
                null, TODAY, null, AdminTopicSort.TOPIC_DATE_ASC, NOW);
        AdminTopicQueryCriteria toOnly = new AdminTopicQueryCriteria(
                null, null, TODAY, AdminTopicSort.TOPIC_DATE_ASC, NOW);
        AdminTopicQueryCriteria combined = new AdminTopicQueryCriteria(
                TopicPhase.OPEN, TODAY, TODAY, AdminTopicSort.TOPIC_DATE_ASC, NOW);

        // When & Then
        assertThat(repository.findTopics(fromOnly, 1, 20).items())
                .extracting(AdminTopicProjection::topicId).containsExactly(OPEN_ID, FUTURE_ID);
        assertThat(repository.findTopics(toOnly, 1, 20).items())
                .extracting(AdminTopicProjection::topicId).containsExactly(CLOSED_ID, OPEN_ID);
        assertThat(repository.findTopics(combined, 1, 20).items())
                .extracting(AdminTopicProjection::topicId).containsExactly(OPEN_ID);
    }

    @Test
    @DisplayName("상세는 삭제된 주제를 제외하고 게시글이 없으면 집계가 0이다")
    void findActiveTopicById_activeOrDeleted_preservesVisibilityAndCounts() {
        // When
        AdminTopicProjection topic = repository.findActiveTopicById(OPEN_ID).orElseThrow();

        // Then
        assertThat(topic.pendingPostCount()).isZero();
        assertThat(topic.approvedPostCount()).isZero();
        assertThat(topic.rejectedPostCount()).isZero();
        assertThat(repository.findActiveTopicById(DELETED_ID)).isEmpty();
        assertThat(repository.findActiveTopicById(new UUID(0, 99))).isEmpty();
    }

    private static Stream<Arguments> sortCases() {
        return Stream.of(
                Arguments.of(AdminTopicSort.TOPIC_DATE_ASC, List.of(CLOSED_ID, OPEN_ID, FUTURE_ID)),
                Arguments.of(AdminTopicSort.TOPIC_DATE_DESC,
                        List.of(FUTURE_ID, OPEN_ID, CLOSED_ID)),
                Arguments.of(AdminTopicSort.CREATED_AT_ASC, List.of(FUTURE_ID, CLOSED_ID, OPEN_ID)),
                Arguments.of(AdminTopicSort.CREATED_AT_DESC,
                        List.of(OPEN_ID, CLOSED_ID, FUTURE_ID)));
    }

    private static Stream<Arguments> phaseCases() {
        return Stream.of(
                Arguments.of(TopicPhase.BEFORE_OPEN, FUTURE_ID),
                Arguments.of(TopicPhase.OPEN, OPEN_ID),
                Arguments.of(TopicPhase.CLOSED, CLOSED_ID));
    }

    private void insertTopic(UUID id, int dayOffset, Instant createdAt, Instant deletedAt) {
        jdbcTemplate.update(
                """
                        INSERT INTO topics (id, title, topic_date, starts_at, ends_at, created_at, updated_at, deleted_at)
                        VALUES (?, '검색 검증', ?, ?, ?, ?, ?, ?)
                        """,
                id, TODAY.plusDays(dayOffset), Timestamp.from(NOW.plusSeconds(dayOffset * 86400L)),
                Timestamp.from(NOW.plusSeconds((dayOffset + 1) * 86400L)),
                Timestamp.from(createdAt),
                Timestamp.from(createdAt), deletedAt == null ? null : Timestamp.from(deletedAt));
    }
}
