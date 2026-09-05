package com.chalkak.backend.admin.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chalkak.backend.exception.BusinessException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AdminAuditSnapshotTest {

    @Test
    @DisplayName(
            "허용한 상태 값은 문자열 형식으로 정규화하고 불변 복사한다"
    )
    void from_safeScalarValues_createsImmutableSnapshot() {
        // Given
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("requestId", UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b657099"));
        source.put("occurredAt", Instant.parse("2026-08-27T11:20:00Z"));

        // When
        AdminAuditSnapshot snapshot = AdminAuditSnapshot.from(source);
        source.put("requestId", "changed");

        // Then
        assertThat(snapshot.values().get("requestId"))
                .isEqualTo("0198f6c1-62ba-7d30-8b12-0f733b657099");
        assertThat(snapshot.values().get("occurredAt")).isEqualTo("2026-08-27T11:20:00Z");
        assertThatThrownBy(() -> snapshot.values().put("status", "changed"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("대소문자와 구분자 형태가 달라도 민감 키를 차단한다")
    void from_obfuscatedSensitiveKey_throwsBusinessException() {
        // When & Then
        assertThatThrownBy(() -> AdminAuditSnapshot.from(
                Map.of("Refresh_Token", "secret-value")
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessage(
                        "민감한 정보는 관리자 감사 로그에 저장할 수 없습니다."
                )
                .hasMessageNotContaining("secret-value");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "accessToken=secret-value",
            "https://hooks.slack.com/services/T000/B000/SECRET",
            "fcmToken=fake-device-registration-token",
            "https://storage.example.com/private/photo.webp?version=1",
            "data:image/png;base64,AAAA"
    })
    @DisplayName("허용 필드의 문자열 값에 숨긴 민감정보도 차단한다")
    void from_sensitiveStringValue_throwsBusinessException(String sensitiveValue) {
        // When & Then
        assertThatThrownBy(() -> AdminAuditSnapshot.from(
                Map.of("status", sensitiveValue)
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessage(
                        "민감한 정보는 관리자 감사 로그에 저장할 수 없습니다."
                )
                .hasMessageNotContaining(sensitiveValue);
    }

    @Test
    @DisplayName("생성 뒤 값이 바뀔 수 있는 숫자 객체는 저장할 수 없다")
    void from_mutableNumber_throwsBusinessException() {
        // Given
        AtomicInteger mutableNumber = new AtomicInteger(1);

        // When & Then
        assertThatThrownBy(() -> AdminAuditSnapshot.from(Map.of("count", mutableNumber)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("관리자 감사 로그 상태가 올바르지 않습니다.");
    }

    @Test
    @DisplayName("불변 숫자 값은 감사 로그 상태로 저장한다")
    void from_immutableNumber_createsSnapshot() {
        // When
        AdminAuditSnapshot snapshot = AdminAuditSnapshot.from(Map.of(
                "count", 1,
                "score", new BigDecimal("1.5")
        ));

        // Then
        assertThat(snapshot.values())
                .containsEntry("count", 1)
                .containsEntry("score", new BigDecimal("1.5"));
    }

    @Test
    @DisplayName("민감값이 없는 일반 문장과 문서 URL은 상태 값으로 저장한다")
    void from_benignStringValue_createsSnapshot() {
        // Given
        String value = "토큰 만료 관련 문의: https://docs.example.com/admin/moderation";

        // When
        AdminAuditSnapshot snapshot = AdminAuditSnapshot.from(Map.of("title", value));

        // Then
        assertThat(snapshot.values()).containsEntry("title", value);
    }

    @Test
    @DisplayName("중첩 객체나 목록은 감사 로그 상태에 저장할 수 없다")
    void from_nestedValue_throwsBusinessException() {
        // When & Then
        assertThatThrownBy(() -> AdminAuditSnapshot.from(
                Map.of("status", Map.of("value", "APPROVED"))
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessage("관리자 감사 로그 상태가 올바르지 않습니다.");
    }

    @Test
    @DisplayName("과도하게 중첩된 상태는 감사 로그에 저장할 수 없다")
    void from_tooDeepState_throwsBusinessException() {
        // Given
        Map<String, Object> state = Map.of("level1", Map.of(
                "level2", Map.of(
                        "level3", Map.of(
                                "level4", Map.of(
                                        "level5", Map.of("level6", "value")
                                )
                        )
                )
        ));

        // When & Then
        assertThatThrownBy(() -> AdminAuditSnapshot.from(state))
                .isInstanceOf(BusinessException.class)
                .hasMessage("관리자 감사 로그 상태가 올바르지 않습니다.");
    }

    @Test
    @DisplayName("문자열 상태 값은 코드 포인트 기준 2000자까지 허용한다")
    void from_maxLengthString_createsSnapshot() {
        // Given
        String value = "📸".repeat(2_000);

        // When
        AdminAuditSnapshot snapshot = AdminAuditSnapshot.from(Map.of("value", value));

        // Then
        assertThat(snapshot.values()).containsEntry("value", value);
    }

    @Test
    @DisplayName("문자열 상태 값이 2000자를 초과하면 저장할 수 없다")
    void from_tooLongString_throwsBusinessException() {
        // When & Then
        assertThatThrownBy(() -> AdminAuditSnapshot.from(
                Map.of("value", "가".repeat(2_001))
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessage("관리자 감사 로그 상태가 올바르지 않습니다.");
    }
}
