package com.chalkak.backend.user.service;

import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 비동기 사인 처리 결과를 기다리는 시간을 판단하는 정책.
 */
@Component
public class SignatureProcessingPolicy {

    private final Duration processingTimeout;

    public SignatureProcessingPolicy(
            @Value("${chalkak.user.signature-processing-timeout}") Duration processingTimeout
    ) {
        if (processingTimeout == null || processingTimeout.isNegative()
                || processingTimeout.isZero()) {
            throw new IllegalArgumentException(
                    "chalkak.user.signature-processing-timeout은 0보다 커야 합니다."
            );
        }
        this.processingTimeout = processingTimeout;
    }

    public boolean isProcessingTimedOut(Instant startedAt, Instant currentTime) {
        return startedAt.plus(processingTimeout).isBefore(currentTime);
    }
}
