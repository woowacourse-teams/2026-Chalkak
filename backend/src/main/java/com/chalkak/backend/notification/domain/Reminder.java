package com.chalkak.backend.notification.domain;

import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.UpdateTimestamp;

// 관리자가 등록한 리마인더 원본이다. 알림함에 남기지 않고 푸시로만 보낸다.
@Entity
@Table(name = "reminders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Reminder {

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

    @Enumerated(EnumType.STRING)
    @Column(name = "repeat", nullable = false, length = 20)
    private ReminderRepeat repeat;

    @Enumerated(EnumType.STRING)
    @Column(name = "target", nullable = false, length = 30)
    private ReminderTarget target;

    // 일회성은 발송 시각, 매일 반복은 첫 발송 날짜와 매일 보낼 시각이다.
    @Column(name = "scheduled_at", nullable = false)
    private Instant scheduledAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static Reminder create(
            String title,
            String body,
            ReminderRepeat repeat,
            ReminderTarget target,
            Instant scheduledAt,
            Instant now
    ) {
        Reminder reminder = new Reminder();
        reminder.applyContent(
                title,
                body,
                repeat,
                target,
                scheduledAt,
                now
        );
        return reminder;
    }

    public static boolean isValidTitle(String title) {
        return title != null && isValidNormalizedText(title.trim(), MAX_TITLE_LENGTH);
    }

    private void applyContent(
            String title,
            String body,
            ReminderRepeat repeat,
            ReminderTarget target,
            Instant scheduledAt,
            Instant now
    ) {
        String normalizedTitle = normalizeText(
                title,
                MAX_TITLE_LENGTH,
                "리마인더 제목은 1자 이상 50자 이하여야 합니다."
        );
        String normalizedBody = normalizeText(
                body,
                MAX_BODY_LENGTH,
                "리마인더 본문은 1자 이상 500자 이하여야 합니다."
        );
        if (repeat == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "리마인더 반복 방식이 필요합니다.");
        }
        if (target == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "리마인더 발송 대상이 필요합니다.");
        }
        validateScheduledAt(scheduledAt, now);
        this.title = normalizedTitle;
        this.body = normalizedBody;
        this.repeat = repeat;
        this.target = target;
        // DB는 마이크로초까지 저장하므로 저장값·감사 기록·변경 비교가 같은 값을 쓰도록 맞춘다.
        this.scheduledAt = scheduledAt.truncatedTo(ChronoUnit.MICROS);
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
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "리마인더 발송 시각이 필요합니다.");
        }
        if (scheduledAt.isBefore(now.minus(PAST_SCHEDULE_TOLERANCE))) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    "리마인더 발송 시각은 현재 시각보다 5분 넘게 과거일 수 없습니다.");
        }
    }
}
