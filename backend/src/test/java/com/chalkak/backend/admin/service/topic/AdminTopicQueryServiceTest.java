package com.chalkak.backend.admin.service.topic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.chalkak.backend.admin.repository.AdminPage;
import com.chalkak.backend.admin.repository.topic.AdminTopicProjection;
import com.chalkak.backend.admin.repository.topic.AdminTopicQueryCriteria;
import com.chalkak.backend.admin.repository.topic.AdminTopicQueryRepository;
import com.chalkak.backend.admin.repository.topic.AdminTopicSort;
import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.exception.NotFoundException;
import com.chalkak.backend.topic.domain.TopicPhase;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminTopicQueryServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-28T03:00:00Z");
    private static final Instant STARTS_AT = Instant.parse("2026-08-27T15:00:00Z");
    private static final Instant ENDS_AT = Instant.parse("2026-08-28T15:00:00Z");
    private static final UUID TOPIC_ID = UUID.fromString("0198fd20-0000-7000-8000-000000000012");

    @Mock
    private AdminTopicQueryRepository repository;

    private AdminTopicQueryService service;

    @BeforeEach
    void setUp() {
        service = new AdminTopicQueryService(repository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("상태와 날짜 필터에 고정 Clock 시각을 함께 전달하고 조회 결과를 반환한다")
    void getTopics_phaseAndDateFilters_returnsMatchingActiveTopics() {
        // Given
        LocalDate from = LocalDate.of(
                2026,
                8,
                27
        );
        LocalDate to = LocalDate.of(
                2026,
                8,
                29
        );
        given(repository.findTopics(
                new AdminTopicQueryCriteria(
                        TopicPhase.OPEN,
                        from,
                        to,
                        AdminTopicSort.TOPIC_DATE_DESC,
                        NOW
                ),
                1,
                20
        )).willReturn(new AdminPage<>(
                List.of(topic()),
                1,
                20,
                false
        ));

        // When
        AdminTopicListResult result = service.getTopics(
                TopicPhase.OPEN,
                from,
                to,
                AdminTopicSort.TOPIC_DATE_DESC,
                1,
                20
        );

        // Then
        assertThat(result.topics()).singleElement().satisfies(item -> {
            assertThat(item.topicId()).isEqualTo(TOPIC_ID);
            assertThat(item.phase()).isEqualTo(TopicPhase.OPEN);
        });
        assertThat(result.hasNext()).isFalse();
    }

    @ParameterizedTest
    @CsvSource({"2026-08-27T15:00:00Z, OPEN", "2026-08-28T15:00:00Z, CLOSED"})
    @DisplayName("상세는 Clock의 시작 시각을 OPEN, 종료 시각을 CLOSED로 반환한다")
    void getTopic_phaseBoundaries_derivesFromClock(Instant now, TopicPhase expected) {
        // Given
        service = new AdminTopicQueryService(repository, Clock.fixed(now, ZoneOffset.UTC));
        given(repository.findActiveTopicById(TOPIC_ID)).willReturn(Optional.of(topic()));

        // When
        AdminTopicDetail result = service.getTopic(TOPIC_ID);

        // Then
        assertThat(result.phase()).isEqualTo(expected);
    }

    @Test
    @DisplayName("조회된 주제 상세에 게시물 검수 상태별 통계를 담는다")
    void getTopic_existingPosts_returnsModerationCounts() {
        // Given
        given(repository.findActiveTopicById(TOPIC_ID)).willReturn(Optional.of(topic()));

        // When
        AdminTopicDetail result = service.getTopic(TOPIC_ID);

        // Then
        assertThat(result.postCounts()).isEqualTo(new AdminTopicDetail.PostCounts(
                1,
                0,
                0
        ));
    }

    @Test
    @DisplayName("삭제 등으로 조회할 수 없는 주제는 찾을 수 없음 예외를 반환한다")
    void getTopic_deletedTopic_throwsNotFoundException() {
        // Given
        given(repository.findActiveTopicById(TOPIC_ID)).willReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> service.getTopic(TOPIC_ID))
                .isInstanceOfSatisfying(NotFoundException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.BUSINESS_ERROR));
    }

    @ParameterizedTest
    @CsvSource({"21474836, 100", "21474837, 100"})
    @DisplayName("허용되는 최대 페이지와 그 직전 페이지는 조회 결과를 반환한다")
    void getTopics_offsetWithinIntegerRange_returnsEmptyPage(int page, int pageSize) {
        // Given
        given(repository.findTopics(
                new AdminTopicQueryCriteria(
                        null,
                        null,
                        null,
                        AdminTopicSort.TOPIC_DATE_DESC,
                        NOW
                ),
                page,
                pageSize
        )).willReturn(new AdminPage<>(
                List.of(),
                page,
                pageSize,
                false
        ));

        // When
        AdminTopicListResult result = service.getTopics(
                null,
                null,
                null,
                AdminTopicSort.TOPIC_DATE_DESC,
                page,
                pageSize
        );

        // Then
        assertThat(result.currentPage()).isEqualTo(page);
        assertThat(result.topics()).isEmpty();
        assertThat(result.hasNext()).isFalse();
    }

    @ParameterizedTest
    @CsvSource({"21474838, 100", "2147483647, 100", "42949674, 100"})
    @DisplayName("페이지 offset이 정수 범위를 넘으면 조회하지 않고 거절한다")
    void getTopics_offsetExceedsIntegerRange_throwsBusinessException(int page, int pageSize) {
        // When & Then
        assertThatThrownBy(() -> service.getTopics(
                null,
                null,
                null,
                AdminTopicSort.TOPIC_DATE_DESC,
                page,
                pageSize
        )).isInstanceOfSatisfying(BusinessException.class, exception -> {
            assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BUSINESS_ERROR);
            assertThat(exception.getMessage()).isEqualTo("관리자 주제 요청이 올바르지 않습니다.");
        });
        then(repository).shouldHaveNoInteractions();
    }

    private AdminTopicProjection topic() {
        return new AdminTopicProjection(
                TOPIC_ID,
                "참여 중 주제",
                LocalDate.of(
                        2026,
                        8,
                        28
                ),
                STARTS_AT,
                ENDS_AT,
                STARTS_AT,
                STARTS_AT,
                1,
                0,
                0
        );
    }
}
