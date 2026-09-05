package com.chalkak.backend.topic.domain;

import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "topics")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Topic {

    private static final int MAX_TITLE_LENGTH = 255;
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Id
    @Generated
    @ColumnDefault("uuidv7()")
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "topic_date", nullable = false)
    private LocalDate topicDate;

    @Embedded
    private ParticipationPeriod participationPeriod;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public static Topic create(
            String title,
            LocalDate topicDate,
            ParticipationPeriod participationPeriod,
            Instant now
    ) {
        Topic topic = new Topic();
        topic.validateBusinessState(title, topicDate, participationPeriod, now);
        topic.title = title.trim();
        topic.topicDate = topicDate;
        topic.participationPeriod = participationPeriod;
        return topic;
    }

    public void update(
            String title,
            LocalDate topicDate,
            ParticipationPeriod participationPeriod,
            Instant now
    ) {
        validateEditable(now);
        validateBusinessState(title, topicDate, participationPeriod, now);
        String normalizedTitle = title.trim();
        if (Objects.equals(this.title, normalizedTitle)
                && Objects.equals(this.topicDate, topicDate)
                && samePeriod(participationPeriod)) {
            throw new BusinessException(
                    ErrorCode.RESOURCE_STATE_CHANGED,
                    "변경된 주제 정보가 없습니다."
            );
        }
        this.title = normalizedTitle;
        this.topicDate = topicDate;
        this.participationPeriod = participationPeriod;
    }

    public void delete(Instant now) {
        validateEditable(now);
        this.deletedAt = now;
    }

    public TopicPhase phaseAt(Instant now) {
        return participationPeriod.phaseAt(now);
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    private void validateEditable(Instant now) {
        if (isDeleted() || phaseAt(now) != TopicPhase.BEFORE_OPEN) {
            throw new BusinessException(
                    ErrorCode.RESOURCE_STATE_CHANGED,
                    "공개 전 주제만 변경할 수 있습니다."
            );
        }
    }

    private void validateBusinessState(
            String title,
            LocalDate topicDate,
            ParticipationPeriod participationPeriod,
            Instant now
    ) {
        if (title == null
                || title.isBlank()
                || title.trim().codePointCount(0, title.trim().length()) > MAX_TITLE_LENGTH
                || topicDate == null
                || participationPeriod == null
                || now == null) {
            throw invalidTopicException();
        }
        LocalDate participationDate = LocalDate.ofInstant(
                participationPeriod.getStartsAt(),
                KST
        );
        if (!topicDate.equals(participationDate)
                || participationPeriod.phaseAt(now) != TopicPhase.BEFORE_OPEN) {
            throw invalidTopicException();
        }
    }

    private boolean samePeriod(ParticipationPeriod other) {
        return Objects.equals(
                participationPeriod.getStartsAt(),
                other.getStartsAt()
        ) && Objects.equals(
                participationPeriod.getEndsAt(),
                other.getEndsAt()
        );
    }

    private BusinessException invalidTopicException() {
        return new BusinessException(
                ErrorCode.BUSINESS_ERROR,
                "주제 정보가 올바르지 않습니다."
        );
    }
}
