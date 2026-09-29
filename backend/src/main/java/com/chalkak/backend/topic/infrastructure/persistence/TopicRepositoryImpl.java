package com.chalkak.backend.topic.infrastructure.persistence;

import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.topic.domain.Topic;
import com.chalkak.backend.topic.repository.TopicRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class TopicRepositoryImpl implements TopicRepository {

    private static final String TOPIC_DATE_UNIQUE_INDEX = "ux_topics_topic_date_active";
    private static final String TOPIC_PERIOD_EXCLUSION_CONSTRAINT = "ex_topics_active_participation_period";

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
    public Optional<Topic> findActiveOpenAt(Instant now) {
        return topicJpaRepository.findActiveOpenAt(now);
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
                topicId);
    }

    @Override
    public boolean existsActiveOverlappingPeriod(Instant startsAt, Instant endsAt) {
        return topicJpaRepository.existsActiveOverlappingPeriod(startsAt, endsAt);
    }

    @Override
    public boolean existsActiveOverlappingPeriodExcludingId(
            Instant startsAt,
            Instant endsAt,
            UUID topicId
    ) {
        return topicJpaRepository.existsActiveOverlappingPeriodExcludingId(startsAt, endsAt,
                topicId);
    }

    @Override
    public Topic saveAndFlush(Topic topic) {
        try {
            return topicJpaRepository.saveAndFlush(topic);
        } catch (DataIntegrityViolationException exception) {
            if (hasConstraintName(exception, TOPIC_DATE_UNIQUE_INDEX)) {
                throw new BusinessException(
                        ErrorCode.BUSINESS_ERROR,
                        "해당 날짜의 주제가 이미 존재합니다.");
            }
            if (hasConstraintName(exception, TOPIC_PERIOD_EXCLUSION_CONSTRAINT)) {
                throw new BusinessException(
                        ErrorCode.BUSINESS_ERROR,
                        "다른 주제의 참여 기간과 겹칩니다.");
            }
            throw exception;
        }
    }

    private boolean hasConstraintName(Throwable exception, String constraintName) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof ConstraintViolationException constraintViolationException
                    && constraintName.equals(
                            constraintViolationException.getConstraintName())) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }
}
