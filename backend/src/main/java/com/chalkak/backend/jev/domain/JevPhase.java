package com.chalkak.backend.jev.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** 분류와 사진 검사의 독립적인 100초 예산·최대 3회. SQS 전달 횟수로 초기화하지 않는다. */
public record JevPhase(
        Instant deadline,
        Instant availableAt,
        UUID callId,
        Instant claimedAt,
        Instant leaseUntil,
        List<JevCallResult> calls,
        String failure,
        boolean completed
) {

    public JevPhase {
        calls = List.copyOf(calls);
    }

    public static JevPhase ready(Instant now) {
        return new JevPhase(
                null,
                now,
                null,
                null,
                null,
                List.of(),
                null,
                false
        );
    }

    public JevPhase refresh(Instant now) {
        if (completed) {
            return this;
        }
        if (callId != null && !now.isBefore(leaseUntil)) {
            return receive(
                    new JevCallResult(
                            callId,
                            claimedAt,
                            now,
                            null,
                            "RESPONSE_LOST",
                            null,
                            null,
                            null
                    ),
                    now,
                    0
            );
        }
        if (deadline != null && !now.isBefore(deadline)) {
            return finish("DEADLINE_EXCEEDED");
        }
        return this;
    }

    public boolean canClaim(Instant now) {
        return !completed && callId == null && !now.isBefore(availableAt)
                && (deadline == null || now.isBefore(deadline));
    }

    public JevPhase claim(Instant now) {
        if (!canClaim(now)) {
            throw new IllegalStateException("JEV_CALL_NOT_AVAILABLE");
        }
        Instant end = deadline == null ? now.plusSeconds(100) : deadline;
        Instant lease = now.plusSeconds(45).isBefore(end) ? now.plusSeconds(45) : end;
        return new JevPhase(
                end,
                availableAt,
                UUID.randomUUID(),
                now,
                lease,
                calls,
                null,
                false
        );
    }

    public JevPhase receive(
            JevCallResult result,
            Instant now,
            long jitterMillis
    ) {
        if (completed || callId == null || !callId.equals(result.callId())) {
            return retainLateResult(result);
        }
        List<JevCallResult> history = new ArrayList<>(calls);
        history.add(result);
        if (!now.isBefore(deadline)) {
            return updated(
                    history,
                    now,
                    "DEADLINE_EXCEEDED",
                    true
            );
        }
        if (result.errorCode() == null) {
            return updated(
                    history,
                    now,
                    null,
                    true
            );
        }
        long backoff = history.size() == 1 ? 2000 : 4000;
        if (result.retryAfterSeconds() != null) {
            backoff = Math.max(backoff, result.retryAfterSeconds().longValue() * 1000);
        }
        Instant next = now.plusMillis(backoff + Math.clamp(
                jitterMillis,
                0,
                1000
        ));
        if (!result.isRetryable() || history.size() >= 3 || !next.isBefore(deadline)) {
            return updated(
                    history,
                    now,
                    result.errorCode(),
                    true
            );
        }
        return updated(
                history,
                next,
                null,
                false
        );
    }

    public JevPhase finish(String error) {
        return updated(
                calls,
                availableAt,
                error,
                true
        );
    }

    /** 응답 유실로 닫힌 호출의 사용량은 복구하되, 이미 시작한 다음 호출의 결과로 오인하지 않는다. */
    private JevPhase retainLateResult(JevCallResult result) {
        List<JevCallResult> history = new ArrayList<>(calls);
        for (int index = 0; index < history.size(); index++) {
            JevCallResult existing = history.get(index);
            if (existing.callId().equals(result.callId())
                    && "RESPONSE_LOST".equals(existing.errorCode())) {
                history.set(index, new JevCallResult(
                        result.callId(),
                        existing.startedAt(),
                        result.receivedAt(),
                        result.rawResponse(),
                        "LATE_RESULT_IGNORED",
                        result.httpStatus(),
                        result.latencyMs(),
                        null,
                        result.inputTokens(),
                        result.outputTokens()
                ));
                return new JevPhase(
                        deadline,
                        availableAt,
                        callId,
                        claimedAt,
                        leaseUntil,
                        history,
                        failure,
                        completed
                );
            }
        }
        return this;
    }

    private JevPhase updated(
            List<JevCallResult> history,
            Instant next,
            String error,
            boolean done
    ) {
        return new JevPhase(
                deadline,
                next,
                null,
                null,
                null,
                history,
                error,
                done
        );
    }
}
