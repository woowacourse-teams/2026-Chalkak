package com.chalkak.backend.topic.repository;

import com.chalkak.backend.topic.domain.Topic;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface TopicRepository {

    Optional<Topic> findActiveById(UUID topicId);

    Optional<Topic> findActiveByTopicDate(LocalDate topicDate);

    Optional<Topic> findActiveByIdForUpdate(UUID topicId);

    boolean existsActiveByTopicDate(LocalDate topicDate);

    boolean existsActiveByTopicDateExcludingId(LocalDate topicDate, UUID topicId);

    Topic saveAndFlush(Topic topic);
}
