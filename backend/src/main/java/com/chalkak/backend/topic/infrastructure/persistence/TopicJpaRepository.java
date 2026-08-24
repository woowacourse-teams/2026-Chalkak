package com.chalkak.backend.topic.infrastructure.persistence;

import com.chalkak.backend.topic.domain.Topic;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TopicJpaRepository extends JpaRepository<Topic, UUID> {

    @Query("""
            SELECT topic
            FROM Topic topic
            WHERE topic.topicDate = :topicDate
              AND topic.deletedAt IS NULL
            """)
    Optional<Topic> findActiveByTopicDate(@Param("topicDate") LocalDate topicDate);
}
