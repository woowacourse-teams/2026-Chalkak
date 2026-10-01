package com.chalkak.backend.admin.infrastructure.persistence.topic;

import com.chalkak.backend.admin.repository.topic.AdminTopicProjection;
import com.chalkak.backend.admin.repository.topic.AdminTopicQueryCriteria;
import com.chalkak.backend.topic.domain.Topic;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AdminTopicJpaRepository extends JpaRepository<Topic, UUID> {

    @Query("""
            SELECT new com.chalkak.backend.admin.repository.topic.AdminTopicProjection(
                topic.id,
                topic.title,
                topic.topicDate,
                topic.participationPeriod.startsAt,
                topic.participationPeriod.endsAt,
                topic.createdAt,
                topic.updatedAt,
                SUM(CASE WHEN post.moderationStatus = :#{T(com.chalkak.backend.post.domain.ModerationStatus).PENDING} THEN 1 ELSE 0 END),
                SUM(CASE WHEN post.moderationStatus = :#{T(com.chalkak.backend.post.domain.ModerationStatus).APPROVED} THEN 1 ELSE 0 END),
                SUM(CASE WHEN post.moderationStatus = :#{T(com.chalkak.backend.post.domain.ModerationStatus).REJECTED} THEN 1 ELSE 0 END)
            )
            FROM Topic topic
            LEFT JOIN Post post ON post.topic = topic AND post.deletedAt IS NULL
            WHERE topic.deletedAt IS NULL
              AND (:#{#criteria.phase() == null} = true
                   OR (:#{#criteria.phase()?.name()} = 'BEFORE_OPEN'
                       AND topic.participationPeriod.startsAt > :#{#criteria.now()})
                   OR (:#{#criteria.phase()?.name()} = 'OPEN'
                       AND topic.participationPeriod.startsAt <= :#{#criteria.now()}
                       AND topic.participationPeriod.endsAt > :#{#criteria.now()})
                   OR (:#{#criteria.phase()?.name()} = 'CLOSED'
                       AND topic.participationPeriod.endsAt <= :#{#criteria.now()}))
              AND (:#{#criteria.dateFrom() == null} = true OR topic.topicDate >= :#{#criteria.dateFrom()})
              AND (:#{#criteria.dateTo() == null} = true OR topic.topicDate <= :#{#criteria.dateTo()})
            GROUP BY topic.id, topic.title, topic.topicDate,
                     topic.participationPeriod.startsAt,
                     topic.participationPeriod.endsAt,
                     topic.createdAt, topic.updatedAt
            """)
    Slice<AdminTopicProjection> findTopics(
            @Param("criteria") AdminTopicQueryCriteria criteria,
            Pageable pageable
    );

    @Query("""
            SELECT new com.chalkak.backend.admin.repository.topic.AdminTopicProjection(
                topic.id,
                topic.title,
                topic.topicDate,
                topic.participationPeriod.startsAt,
                topic.participationPeriod.endsAt,
                topic.createdAt,
                topic.updatedAt,
                SUM(CASE WHEN post.moderationStatus = :#{T(com.chalkak.backend.post.domain.ModerationStatus).PENDING} THEN 1 ELSE 0 END),
                SUM(CASE WHEN post.moderationStatus = :#{T(com.chalkak.backend.post.domain.ModerationStatus).APPROVED} THEN 1 ELSE 0 END),
                SUM(CASE WHEN post.moderationStatus = :#{T(com.chalkak.backend.post.domain.ModerationStatus).REJECTED} THEN 1 ELSE 0 END)
            )
            FROM Topic topic
            LEFT JOIN Post post ON post.topic = topic AND post.deletedAt IS NULL
            WHERE topic.id = :id AND topic.deletedAt IS NULL
            GROUP BY topic.id, topic.title, topic.topicDate,
                     topic.participationPeriod.startsAt,
                     topic.participationPeriod.endsAt,
                     topic.createdAt, topic.updatedAt
            """)
    List<AdminTopicProjection> findActiveTopicDetail(
            @Param("id") UUID id,
            Pageable pageable
    );
}
