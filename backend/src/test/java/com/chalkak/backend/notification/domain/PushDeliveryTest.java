package com.chalkak.backend.notification.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.notification.domain.PushDelivery.DeviceResult;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class PushDeliveryTest {
    private static final Instant NOW = Instant.parse("2026-10-26T00:00:00Z");
    private static final UUID DEVICE_ID = UUID.randomUUID();

    @Test
    @DisplayName("첫 발송 시작을 기록하고 호출자가 원본 맵을 바꿔도 스냅샷은 유지한다")
    void startDevice_firstAttempt_returnsSendingRecordAndKeepsSnapshot() {
        // Given
        Map<UUID, DeviceResult> source = new HashMap<>();
        PushDelivery delivery = snapshot(source);
        source.put(DEVICE_ID, sending(1));

        // When
        DeviceResult started = delivery.startDevice(DEVICE_ID, NOW);

        // Then
        assertThat(started.getStatus()).isEqualTo(PushDeviceDeliveryStatus.SENDING);
        assertThat(started.getAttemptCount()).isEqualTo(1);
        assertThat(delivery.getDeviceResults()).isEmpty();
        assertThatThrownBy(() -> delivery.getDeviceResults().put(DEVICE_ID, started))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @ParameterizedTest
    @EnumSource(value = PushDeviceDeliveryStatus.class, names = {"SENDING", "UNKNOWN", "RETRYABLE"})
    @DisplayName("미확정·일시 실패 기록은 별도 횟수 상한 없이 다음 시도를 기록한다")
    void startDevice_retryableHistory_incrementsAttemptCount(PushDeviceDeliveryStatus status) {
        // Given
        DeviceResult previous = new DeviceResult(
                status,
                10,
                "UNAVAILABLE",
                NOW,
                null
        );
        PushDelivery delivery = snapshot(Map.of(DEVICE_ID, previous));

        // When
        DeviceResult started = delivery.startDevice(DEVICE_ID, NOW.plusSeconds(60));

        // Then
        assertThat(started.getAttemptCount()).isEqualTo(11);
        assertThat(started.getSendStartedAt()).isEqualTo(NOW.plusSeconds(60));
    }

    @ParameterizedTest
    @EnumSource(value = PushDeviceDeliveryStatus.class, names = {"ACCEPTED", "PERMANENT_FAILURE",
            "INVALID_TOKEN", "SKIPPED"})
    @DisplayName("종료된 기기 결과는 다시 발송을 시작하지 않는다")
    void startDevice_terminalHistory_rejectsNewAttempt(PushDeviceDeliveryStatus status) {
        // Given
        DeviceResult result = sending(1).finish(
                status,
                null,
                NOW
        );
        PushDelivery delivery = snapshot(Map.of(DEVICE_ID, result));

        // When & Then
        assertThat(delivery.canSendTo(DEVICE_ID)).isFalse();
        assertThat(delivery.hasAcceptedDevice())
                .isEqualTo(status == PushDeviceDeliveryStatus.ACCEPTED);
        assertThatThrownBy(() -> delivery.startDevice(DEVICE_ID, NOW))
                .isInstanceOf(BusinessException.class);
    }

    @ParameterizedTest
    @EnumSource(value = PushDeviceDeliveryStatus.class, mode = EnumSource.Mode.EXCLUDE, names = "SENDING")
    @DisplayName("발송 중인 시도에 결과를 기록하며 수락 결과에만 수락 시각을 남긴다")
    void finish_sendingAttempt_preservesCountAndSetsAcceptanceTime(
            PushDeviceDeliveryStatus status) {
        // When
        DeviceResult result = sending(2).finish(
                status,
                "RESULT",
                NOW.plusSeconds(1)
        );

        // Then
        assertThat(result.getStatus()).isEqualTo(status);
        assertThat(result.getAttemptCount()).isEqualTo(2);
        assertThat(result.getSendStartedAt()).isEqualTo(NOW);
        if (status == PushDeviceDeliveryStatus.ACCEPTED) {
            assertThat(result.getAcceptedAt()).isEqualTo(NOW.plusSeconds(1));
            return;
        }
        assertThat(result.getAcceptedAt()).isNull();
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0})
    @DisplayName("발송 시작 기록의 횟수는 양수여야 한다")
    void constructor_nonPositiveAttemptCount_rejectsRecord(int count) {
        // When & Then
        assertThatThrownBy(() -> sending(count)).isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("수락 상태·수락 시각이 서로 맞지 않거나 시작 시각이 없으면 거절한다")
    void constructor_inconsistentResult_rejectsRecord() {
        // When & Then
        assertThatThrownBy(() -> new DeviceResult(
                PushDeviceDeliveryStatus.ACCEPTED,
                1,
                null,
                NOW,
                null
        )).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> new DeviceResult(
                PushDeviceDeliveryStatus.RETRYABLE,
                1,
                null,
                NOW,
                NOW
        )).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> new DeviceResult(
                PushDeviceDeliveryStatus.SENDING,
                1,
                null,
                null,
                null
        )).isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("완료 결과를 덮어쓰거나 결과 없이 발송 중 상태로 마치는 것을 거절한다")
    void finish_notSendingOrMissingResult_rejectsTransition() {
        // Given
        DeviceResult accepted = sending(1).finish(
                PushDeviceDeliveryStatus.ACCEPTED,
                null,
                NOW
        );

        // When & Then
        assertThatThrownBy(() -> accepted.finish(
                PushDeviceDeliveryStatus.RETRYABLE,
                "UNAVAILABLE",
                NOW
        )).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> sending(1).finish(
                PushDeviceDeliveryStatus.SENDING,
                null,
                NOW
        )).isInstanceOf(BusinessException.class);
    }

    private DeviceResult sending(int count) {
        return new DeviceResult(
                PushDeviceDeliveryStatus.SENDING,
                count,
                null,
                NOW,
                null
        );
    }

    private PushDelivery snapshot(Map<UUID, DeviceResult> results) {
        return new PushDelivery(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                PushDeliveryStatus.PROCESSING,
                UUID.randomUUID(),
                NOW.plusSeconds(60),
                results,
                null,
                NOW,
                NOW
        );
    }
}
