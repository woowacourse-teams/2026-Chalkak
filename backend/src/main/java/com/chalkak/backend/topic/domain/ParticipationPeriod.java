package com.chalkak.backend.topic.domain;

import java.time.Instant;

public class ParticipationPeriod {

    private Instant startsAt;
    private Instant endsAt;

    public ParticipationPeriod(Instant startsAt, Instant endsAt) {
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
