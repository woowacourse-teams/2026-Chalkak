package com.chalkak.backend.notification.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.chalkak.backend.user.domain.UserFixture;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PushDeviceTest {

    @Test
    @DisplayName("FCM 토큰을 갱신해도 최초 기기 등록 시각은 유지한다")
    void updateToken_newToken_preservesFirstRegistrationTime() {
        // given
        Instant registeredAt = Instant.parse("2026-09-30T00:00:00Z");
        PushDevice device = new PushDevice(UserFixture.create(), UUID.randomUUID(),
                new FcmToken("first-token"), registeredAt);
        Instant updatedAt = registeredAt.plusSeconds(60);
        FcmToken replacement = new FcmToken("replacement-token");

        // when
        device.updateToken(replacement, updatedAt);

        // then
        assertThat(device.getRegisteredAt()).isEqualTo(registeredAt);
        assertThat(device.getUpdatedAt()).isEqualTo(updatedAt);
        assertThat(device.getFcmToken()).isEqualTo(replacement.getValue());
        assertThat(device.getFcmTokenHash()).isEqualTo(replacement.getHash());
    }
}
