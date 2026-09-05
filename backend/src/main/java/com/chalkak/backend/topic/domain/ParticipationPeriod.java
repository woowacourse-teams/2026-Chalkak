package com.chalkak.backend.topic.domain;

import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ParticipationPeriod {

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    public ParticipationPeriod(Instant startsAt, Instant endsAt) {
        if (startsAt == null || endsAt == null || !endsAt.isAfter(startsAt)) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_ERROR,
                    "주제 참여 기간이 올바르지 않습니다."
            );
        }
        this.startsAt = startsAt;
        this.endsAt = endsAt;
    }

    public TopicPhase phaseAt(Instant now) {
        if (now.isBefore(startsAt)) {
            return TopicPhase.BEFORE_OPEN;
        }
        if (now.isBefore(endsAt)) {
            return TopicPhase.OPEN;
        }
        return TopicPhase.CLOSED;
    }
}
