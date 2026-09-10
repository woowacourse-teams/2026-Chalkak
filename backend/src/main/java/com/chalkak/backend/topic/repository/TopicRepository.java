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
     * 그 시각에 참여할 수 있는 주제. 날짜가 아니라 참여 기간으로 고르므로, 아직 열리지 않았거나 이미 닫힌
     * 주제는 애초에 걸리지 않는다.
     *
     * <p>참여 기간이 겹치는 주제를 막는 검사는 없다. 겹치도록 등록하는 것은 운영 실수이므로 여기서 임의로
     * 하나를 고르지 않고 조회 자체를 실패시켜 드러낸다.
     */
    Optional<Topic> findActiveOpenAt(Instant now);

    Optional<Topic> findActiveByIdForUpdate(UUID topicId);

    boolean existsActiveByTopicDate(LocalDate topicDate);

    boolean existsActiveByTopicDateExcludingId(LocalDate topicDate, UUID topicId);

    Topic saveAndFlush(Topic topic);
}
