package com.chalkak.backend.jev.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "jev_topic_categories")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class JevTopicCategory {

    @Id
    private UUID id;

    @Column(name = "topic_id", nullable = false, updatable = false)
    private UUID topicId;

    @Column(name = "classification_key", nullable = false, updatable = false, length = 64)
    private String classificationKey;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private JevPhase phase;

    @Column(length = 64)
    private String category;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private JevEvaluation evaluation;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static JevTopicCategory create(
            UUID topicId,
            String classificationKey,
            Instant now
    ) {
        JevTopicCategory result = new JevTopicCategory();
        result.id = UUID.randomUUID();
        result.topicId = topicId;
        result.classificationKey = classificationKey;
        result.phase = JevPhase.ready(now);
        result.createdAt = now;
        return result;
    }

    public void updatePhase(JevPhase phase) {
        this.phase = phase;
    }

    public void complete(JevEvaluation evaluation) {
        this.evaluation = evaluation;
        this.category = evaluation.category();
        if (evaluation.error() != null) {
            phase = phase.finish(evaluation.error());
        }
    }
}
