package com.chalkak.backend.admin.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chalkak.backend.exception.BusinessException;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class BannerAuditTransitionTest {

    private static final Map<String, Object> VALID = Map.of(
            "titleSha256", "a".repeat(64), "subtitleSha256", "b".repeat(64));

    @Test
    @DisplayName("배너 생성은 빈 이전 상태와 제목·부제목 해시만 있는 이후 상태를 허용한다")
    void create_validBannerTransition_recordsHashes() {
        // When
        AdminAuditLog audit = create(Map.of(), VALID, AdminTargetType.BANNER);

        // Then
        assertThat(audit.getBeforeState()).isEmpty();
        assertThat(audit.getAfterState()).containsExactlyInAnyOrderEntriesOf(VALID);
    }

    @ParameterizedTest
    @MethodSource("invalidTransitions")
    @DisplayName("배너 생성은 잘못된 대상·이전 상태·필드·해시를 거절한다")
    void create_invalidBannerTransition_throwsBusinessException(
            Map<String, Object> before, Map<String, Object> after, AdminTargetType targetType) {
        // When & Then
        assertThatThrownBy(() -> create(before, after, targetType))
                .isInstanceOf(BusinessException.class);
    }

    private static Stream<Arguments> invalidTransitions() {
        return Stream.of(
                Arguments.of(VALID, VALID, AdminTargetType.BANNER),
                Arguments.of(Map.of(), VALID, AdminTargetType.POST),
                Arguments.of(Map.of(), Map.of("titleSha256", "a".repeat(64)),
                        AdminTargetType.BANNER),
                Arguments.of(Map.of(), Map.of("titleSha256", "g".repeat(64),
                        "subtitleSha256", "b".repeat(64)), AdminTargetType.BANNER),
                Arguments.of(Map.of(), Map.of("titleSha256", "a".repeat(63),
                        "subtitleSha256", "b".repeat(64)), AdminTargetType.BANNER),
                Arguments.of(Map.of(), Map.of("titleSha256", "a".repeat(64),
                        "subtitleSha256", 1), AdminTargetType.BANNER),
                Arguments.of(Map.of(), Map.of("titleSha256", "a".repeat(64),
                        "subtitleSha256", "b".repeat(64), "extra", true), AdminTargetType.BANNER));
    }

    private AdminAuditLog create(
            Map<String, Object> before,
            Map<String, Object> after,
            AdminTargetType targetType
    ) {
        return AdminAuditLog.create(UUID.randomUUID(), AdminAction.BANNER_CREATED, targetType,
                UUID.randomUUID(), null, AdminAuditSnapshot.from(before),
                AdminAuditSnapshot.from(after),
                Instant.now(), UUID.randomUUID());
    }
}
