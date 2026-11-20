package com.chalkak.backend.jev.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.chalkak.backend.notification.service.NotificationDeliveryResult;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JevSlackDeliveryTest {

    private static final Instant NOW = Instant.parse("2026-11-20T01:00:00Z");

    @Test
    @DisplayName("발송권 만료는 응답 유실로 기록하고 재시작 뒤에도 대기 시간을 지킨다")
    void refresh_expiredLease_recordsLossAndSchedulesRetry() {
        var phase = JevSlackDelivery.ready(NOW, true).claim(NOW, Duration.ofSeconds(15));
        assertThat(phase.refresh(NOW.plusSeconds(14))).isSameAs(phase);
        var recovered = phase.refresh(NOW.plusSeconds(15));
        assertThat(recovered.status()).isEqualTo("RETRY_WAIT");
        assertThat(recovered.history().getFirst().error()).isEqualTo("DELIVERY_RESULT_LOST");
        assertThat(recovered.claim(NOW.plusSeconds(74), Duration.ofSeconds(15)))
                .isSameAs(recovered);
        var next = recovered.claim(NOW.plusSeconds(75), Duration.ofSeconds(15));
        assertThat(next.attempts()).isEqualTo(2);
        assertThat(next.token()).isNotEqualTo(phase.token());
    }

    @Test
    @DisplayName("Retry-After가 1분보다 길면 공급자 제한을 따른다")
    void finish_retryAfter_usesLongerDelay() {
        var phase = JevSlackDelivery.ready(NOW, true).claim(NOW, Duration.ofSeconds(15));
        var result = phase.finish(
                phase.token(),
                new NotificationDeliveryResult(
                        false,
                        true,
                        "SLACK_HTTP_ERROR",
                        429,
                        120L
                ),
                NOW
        );
        assertThat(result.dueAt()).isEqualTo(NOW.plusSeconds(120));
    }

    @Test
    @DisplayName("설정 오류나 성공 결과는 추가 전송을 예약하지 않는다")
    void finish_permanentFailureOrSuccess_stops() {
        var phase = JevSlackDelivery.ready(NOW, true).claim(NOW, Duration.ofSeconds(15));
        var failed = phase.finish(
                phase.token(),
                new NotificationDeliveryResult(
                        false,
                        false,
                        "SLACK_HTTP_ERROR",
                        404,
                        null
                ),
                NOW
        );
        assertThat(failed.status()).isEqualTo("FAILED");
        assertThat(failed.dueAt()).isNull();
        assertThat(failed.claim(NOW.plusSeconds(1000), Duration.ofSeconds(15))).isSameAs(failed);
        assertThat(JevSlackDelivery.ready(NOW, false).dueAt()).isNull();
    }
}
