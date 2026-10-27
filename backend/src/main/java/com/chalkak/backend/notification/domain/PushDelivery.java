package com.chalkak.backend.notification.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

// DB 갱신은 저장소의 부분 UPDATE로 수행하고, 저장 성공 후 메모리의 기기 결과도 갱신한다.
@Entity
@Table(name = "push_deliveries")
@Immutable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PushDelivery {
    @Id
    private UUID id;

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PushDeliveryStatus status;

    @Getter(AccessLevel.NONE)
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "device_results", nullable = false, columnDefinition = "jsonb")
    private Map<UUID, DeviceResult> deviceResults;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public PushDelivery(
            UUID id,
            UUID eventId,
            UUID userId,
            PushDeliveryStatus status,
            Map<UUID, DeviceResult> deviceResults,
            Instant completedAt,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = id;
        this.eventId = eventId;
        this.userId = userId;
        this.status = status;
        this.deviceResults = Map.copyOf(deviceResults);
        this.completedAt = completedAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public boolean isCompleted() {
        return status == PushDeliveryStatus.COMPLETED;
    }

    public boolean hasAcceptedDevice() {
        return deviceResults.values().stream().anyMatch(DeviceResult::isAccepted);
    }

    public boolean canSendTo(UUID deviceId) {
        DeviceResult previous = deviceResults.get(deviceId);
        return previous == null || previous.canRetry();
    }

    public DeviceResult startDevice(UUID deviceId, Instant startedAt) {
        if (!canSendTo(deviceId)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "처리 완료된 기기는 다시 발송할 수 없습니다.");
        }
        int count = 1;
        DeviceResult previous = deviceResults.get(deviceId);
        if (previous != null) {
            count = Math.addExact(previous.getAttemptCount(), 1);
        }
        return new DeviceResult(
                PushDeviceDeliveryStatus.SENDING,
                count,
                null,
                startedAt,
                null
        );
    }

    public void recordDeviceResult(UUID deviceId, DeviceResult result) {
        Map<UUID, DeviceResult> updated = new HashMap<>(deviceResults);
        updated.put(deviceId, result);
        deviceResults = Map.copyOf(updated);
    }

    public Map<UUID, DeviceResult> getDeviceResults() {
        return Map.copyOf(deviceResults);
    }

    @Getter
    @EqualsAndHashCode
    public static final class DeviceResult {
        private final PushDeviceDeliveryStatus status;
        private final int attemptCount;
        private final String lastErrorCode;
        private final Instant sendStartedAt;
        private final Instant acceptedAt;

        @JsonCreator
        public DeviceResult(
                @JsonProperty("status") PushDeviceDeliveryStatus status,
                @JsonProperty("attemptCount") int attemptCount,
                @JsonProperty("lastErrorCode") String lastErrorCode,
                @JsonProperty("sendStartedAt") Instant sendStartedAt,
                @JsonProperty("acceptedAt") Instant acceptedAt
        ) {
            if (status == null || attemptCount < 1 || sendStartedAt == null
                    || (status == PushDeviceDeliveryStatus.ACCEPTED) != (acceptedAt != null)) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, "유효하지 않은 기기별 발송 기록입니다.");
            }
            this.status = status;
            this.attemptCount = attemptCount;
            this.lastErrorCode = lastErrorCode;
            this.sendStartedAt = sendStartedAt;
            this.acceptedAt = acceptedAt;
        }

        public DeviceResult finish(
                PushDeviceDeliveryStatus result,
                String errorCode,
                Instant finishedAt
        ) {
            if (status != PushDeviceDeliveryStatus.SENDING
                    || result == PushDeviceDeliveryStatus.SENDING) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, "발송 중인 시도만 결과를 기록할 수 있습니다.");
            }
            Instant accepted = null;
            if (result == PushDeviceDeliveryStatus.ACCEPTED) {
                accepted = finishedAt;
            }
            return new DeviceResult(
                    result,
                    attemptCount,
                    errorCode,
                    sendStartedAt,
                    accepted
            );
        }

        @JsonIgnore
        public boolean isAccepted() {
            return status == PushDeviceDeliveryStatus.ACCEPTED;
        }

        public boolean canRetry() {
            return status == PushDeviceDeliveryStatus.SENDING
                    || status == PushDeviceDeliveryStatus.RETRYABLE
                    || status == PushDeviceDeliveryStatus.UNKNOWN;
        }
    }
}
