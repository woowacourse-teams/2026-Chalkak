package com.chalkak.backend.admin.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chalkak.backend.exception.BusinessException;
import java.time.Instant;
import java.util.LinkedHashMap;
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

    @Test
    @DisplayName("배너 삭제 감사는 같은 해시와 null에서 삭제 시각으로의 전이만 기록한다")
    void delete_validTransition_recordsDeletion() {
        // Given
        Map<String, Object> before = deletionState(null);
        Map<String, Object> after = deletionState("2026-10-19T01:00:00Z");

        // When
        AdminAuditLog audit = delete(before, after, "종료", AdminTargetType.BANNER);

        // Then
        assertThat(audit.getBeforeState()).containsExactlyInAnyOrderEntriesOf(before);
        assertThat(audit.getAfterState()).containsExactlyInAnyOrderEntriesOf(after);
    }

    @ParameterizedTest
    @MethodSource("invalidDeletions")
    @DisplayName("배너 삭제 감사는 잘못된 대상·사유·상태 전이를 거절한다")
    void delete_invalidTransition_rejectsAudit(Map<String, Object> before,
            Map<String, Object> after, String reason, AdminTargetType type) {
        // When & Then
        assertThatThrownBy(() -> delete(before, after, reason, type))
                .isInstanceOf(BusinessException.class);
    }

    private static Stream<Arguments> invalidDeletions() {
        Map<String, Object> before = deletionState(null);
        Map<String, Object> after = deletionState("2026-10-19T01:00:00Z");
        Map<String, Object> changed = new LinkedHashMap<>(after);
        changed.put("titleSha256", "c".repeat(64));
        Map<String, Object> invalidHash = new LinkedHashMap<>(before);
        invalidHash.put("subtitleSha256", "invalid");
        Map<String, Object> extra = new LinkedHashMap<>(after);
        extra.put("webViewUrl", "https://example.com");
        return Stream.of(
                Arguments.of(before, after, null, AdminTargetType.BANNER),
                Arguments.of(before, after, "종료", AdminTargetType.POST),
                Arguments.of(after, after, "종료", AdminTargetType.BANNER),
                Arguments.of(before, before, "종료", AdminTargetType.BANNER),
                Arguments.of(before, deletionState("invalid"), "종료", AdminTargetType.BANNER),
                Arguments.of(before, changed, "종료", AdminTargetType.BANNER),
                Arguments.of(invalidHash, after, "종료", AdminTargetType.BANNER),
                Arguments.of(before, extra, "종료", AdminTargetType.BANNER),
                Arguments.of(VALID, after, "종료", AdminTargetType.BANNER));
    }

    private static Map<String, Object> deletionState(String deletedAt) {
        Map<String, Object> state = new LinkedHashMap<>(VALID);
        state.put("deletedAt", deletedAt);
        return state;
    }

    private AdminAuditLog delete(Map<String, Object> before, Map<String, Object> after,
            String reason, AdminTargetType type) {
        return AdminAuditLog.create(UUID.randomUUID(), AdminAction.BANNER_DELETED, type,
                UUID.randomUUID(), reason, AdminAuditSnapshot.from(before),
                AdminAuditSnapshot.from(after), Instant.now(), UUID.randomUUID());
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
