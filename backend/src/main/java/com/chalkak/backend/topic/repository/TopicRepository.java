package com.chalkak.backend.topic.repository;

import com.chalkak.backend.topic.domain.Topic;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface TopicRepository {

    Optional<Topic> findActiveById(UUID topicId);

    Optional<Topic> findActiveByTopicDate(LocalDate topicDate);

    /**
     * 그 시각에 참여할 수 있는 주제. 날짜가 아니라 참여 기간으로 고르므로, 아직 열리지 않았거나 이미 닫힌 주제는 애초에 걸리지 않는다.
     *
     * <p>
     * 삭제되지 않은 주제의 참여 기간은 서로 겹치지 않도록 저장 시 검증한다.
     */
    Optional<Topic> findActiveOpenAt(Instant now);

    Optional<Topic> findActiveByIdForUpdate(UUID topicId);

    boolean existsActiveByTopicDate(LocalDate topicDate);

    boolean existsActiveByTopicDateExcludingId(LocalDate topicDate, UUID topicId);

    boolean existsActiveOverlappingPeriod(Instant startsAt, Instant endsAt);

    boolean existsActiveOverlappingPeriodExcludingId(
            Instant startsAt,
            Instant endsAt,
            UUID topicId
    );

    Topic saveAndFlush(Topic topic);
}
