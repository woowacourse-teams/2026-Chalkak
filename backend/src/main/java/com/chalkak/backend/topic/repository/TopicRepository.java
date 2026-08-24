package com.chalkak.backend.topic.repository;

import com.chalkak.backend.topic.domain.Topic;
import java.time.LocalDate;
import java.util.Optional;

public interface TopicRepository {

    Optional<Topic> findActiveByTopicDate(LocalDate topicDate);
}
