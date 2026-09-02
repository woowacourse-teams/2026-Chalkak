package com.chalkak.backend.topic.infrastructure.persistence;

import com.chalkak.backend.topic.domain.Topic;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;

public interface TopicJpaRepository extends JpaRepository<Topic, UUID> {

    Optional<Topic> findByIdAndDeletedAtIsNull(UUID topicId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT topic FROM Topic topic WHERE topic.id = :topicId AND topic.deletedAt IS NULL")
    Optional<Topic> findActiveByIdForUpdate(@Param("topicId") UUID topicId);

    boolean existsByTopicDateAndDeletedAtIsNull(LocalDate topicDate);

    boolean existsByTopicDateAndDeletedAtIsNullAndIdNot(LocalDate topicDate, UUID topicId);

    @Query("""
            SELECT topic
            FROM Topic topic
            WHERE topic.topicDate = :topicDate
              AND topic.deletedAt IS NULL
            """)
    Optional<Topic> findActiveByTopicDate(@Param("topicDate") LocalDate topicDate);
}
