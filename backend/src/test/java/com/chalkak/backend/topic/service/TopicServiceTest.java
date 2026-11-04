package com.chalkak.backend.topic.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.exception.NotFoundException;
import com.chalkak.backend.topic.domain.ParticipationPeriod;
import com.chalkak.backend.topic.domain.Topic;
import com.chalkak.backend.topic.domain.TopicPhase;
import com.chalkak.backend.topic.repository.TopicRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class TopicServiceTest {

    private static final UUID TOPIC_ID = UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6570b2");
    private static final LocalDate TOPIC_DATE = LocalDate.of(
            2020,
            1,
            1
    );
    private static final Instant STARTS_AT = Instant.parse("2019-12-31T15:00:00Z");
    private static final Instant ENDS_AT = Instant.parse("2020-01-01T15:00:00Z");

    @Mock
    private TopicRepository repository;

    @InjectMocks
    private TopicService service;

    @Test
    @DisplayName("조회된 과거 주제의 정보와 종료 상태를 반환한다")
    void getTopic_existingPastTopic_returnsTopicDetail() {
        // Given
        Topic topic = Topic.create(
                "오늘 가장 기억에 남은 순간",
                TOPIC_DATE,
                new ParticipationPeriod(STARTS_AT, ENDS_AT),
                STARTS_AT.minusSeconds(1)
        );
        ReflectionTestUtils.setField(
                topic,
                "id",
                TOPIC_ID
        );
        given(repository.findActiveByTopicDate(TOPIC_DATE)).willReturn(Optional.of(topic));

        // When
        TopicDetail detail = service.getTopic(TOPIC_DATE);

        // Then
        assertThat(detail).isEqualTo(new TopicDetail(
                TOPIC_ID,
                "오늘 가장 기억에 남은 순간",
                TOPIC_DATE,
                STARTS_AT,
                ENDS_AT,
                TopicPhase.CLOSED
        ));
    }

    @Test
    @DisplayName("미래의 날짜는 Repository를 조회하지 않고 거절한다")
    void getTopic_futureDate_throwsBusinessException() {
        // When & Then
        assertThatThrownBy(() -> service.getTopic(LocalDate.MAX))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BUSINESS_ERROR);
                    assertThat(exception.getMessage()).isEqualTo("미래의 주제는 조회할 수 없습니다.");
                });
        then(repository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("조회 결과가 없으면 주제를 찾을 수 없다는 예외를 반환한다")
    void getTopic_noTopicOnDate_throwsNotFoundException() {
        // Given
        given(repository.findActiveByTopicDate(TOPIC_DATE)).willReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> service.getTopic(TOPIC_DATE))
                .isInstanceOfSatisfying(NotFoundException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BUSINESS_ERROR);
                    assertThat(exception.getMessage()).isEqualTo("주제를 찾을 수 없습니다.");
                });
    }
}
