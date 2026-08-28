package com.chalkak.backend.topic.infrastructure.persistence;

import com.chalkak.backend.topic.domain.Topic;
import com.chalkak.backend.topic.repository.TopicRepository;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class TopicRepositoryImpl implements TopicRepository {

    private final TopicJpaRepository topicJpaRepository;

    @Override
    public Optional<Topic> findActiveById(UUID topicId) {
        return topicJpaRepository.findByIdAndDeletedAtIsNull(topicId);
    }

    @Override
    public Optional<Topic> findActiveByTopicDate(LocalDate topicDate) {
        return topicJpaRepository.findActiveByTopicDate(topicDate);
    }

    @Override
    public Optional<Topic> findActiveByIdForUpdate(UUID topicId) {
        return topicJpaRepository.findActiveByIdForUpdate(topicId);
    }

    @Override
    public boolean existsActiveByTopicDate(LocalDate topicDate) {
        return topicJpaRepository.existsByTopicDateAndDeletedAtIsNull(topicDate);
    }

    @Override
    public boolean existsActiveByTopicDateExcludingId(LocalDate topicDate, UUID topicId) {
        return topicJpaRepository.existsByTopicDateAndDeletedAtIsNullAndIdNot(
                topicDate,
                topicId
        );
    }

    @Override
    public Topic saveAndFlush(Topic topic) {
        return topicJpaRepository.saveAndFlush(topic);
    }
}
