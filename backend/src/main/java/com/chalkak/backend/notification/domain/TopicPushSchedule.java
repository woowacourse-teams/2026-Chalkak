package com.chalkak.backend.notification.domain;

import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "topic_push_schedules")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TopicPushSchedule {

    @Id
    @Generated
    @ColumnDefault("uuidv7()")
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "send_time", nullable = false)
    private LocalTime sendTime;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public TopicPushSchedule(LocalTime sendTime) {
        validateSendTime(sendTime);
        this.sendTime = sendTime;
    }

    public void updateSendTime(LocalTime sendTime) {
        validateSendTime(sendTime);
        this.sendTime = sendTime;
    }

    private void validateSendTime(LocalTime sendTime) {
        if (sendTime == null || sendTime.getSecond() != 0 || sendTime.getNano() != 0) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    "주제 푸시 발송 시각은 분 단위의 KST 시각이어야 합니다.");
        }
    }
}
