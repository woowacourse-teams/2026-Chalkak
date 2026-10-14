package com.chalkak.backend.notification.domain;

import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "notices")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notice {

    private static final int MAX_TITLE_LENGTH = 50;
    private static final int MAX_BODY_LENGTH = 500;
    private static final Duration PAST_SCHEDULE_TOLERANCE = Duration.ofMinutes(5);

    @Id
    @Generated
    @ColumnDefault("uuidv7()")
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "title", nullable = false, length = MAX_TITLE_LENGTH)
    private String title;

    @Column(name = "body", nullable = false, length = MAX_BODY_LENGTH)
    private String body;

    @Column(name = "scheduled_at", nullable = false)
    private Instant scheduledAt;

    @Column(name = "distributed_at")
    private Instant distributedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static Notice create(
            String title,
            String body,
            Instant scheduledAt,
            Instant now
    ) {
        Notice notice = new Notice();
        notice.title = normalizeText(title, MAX_TITLE_LENGTH, "공지 제목은 1자 이상 50자 이하여야 합니다.");
        notice.body = normalizeText(body, MAX_BODY_LENGTH, "공지 본문은 1자 이상 500자 이하여야 합니다.");
        validateScheduledAt(scheduledAt, now);
        notice.scheduledAt = scheduledAt;
        return notice;
    }

    public void distribute(Instant distributedAt) {
        if (this.distributedAt != null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "예약 상태의 공지만 배포할 수 있습니다.");
        }
        this.distributedAt = distributedAt;
    }

    // 상태는 배포 시각으로 정해지므로 따로 저장하지 않고 계산한다.
    public NoticeStatus getStatus() {
        if (distributedAt == null) {
            return NoticeStatus.SCHEDULED;
        }
        return NoticeStatus.DISTRIBUTED;
    }

    public static boolean isValidTitle(String title) {
        return title != null && isValidNormalizedText(title.trim(), MAX_TITLE_LENGTH);
    }

    private static String normalizeText(
            String value,
            int maxLength,
            String message
    ) {
        String normalized = value == null ? "" : value.trim();
        if (!isValidNormalizedText(normalized, maxLength)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, message);
        }
        return normalized;
    }

    private static boolean isValidNormalizedText(String normalized, int maxLength) {
        return !normalized.isBlank()
                && normalized.codePointCount(0, normalized.length()) <= maxLength;
    }

    private static void validateScheduledAt(Instant scheduledAt, Instant now) {
        if (scheduledAt == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "공지 예약 시각이 필요합니다.");
        }
        if (scheduledAt.isBefore(now.minus(PAST_SCHEDULE_TOLERANCE))) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    "공지 예약 시각은 현재 시각보다 5분 넘게 과거일 수 없습니다.");
        }
    }
}
