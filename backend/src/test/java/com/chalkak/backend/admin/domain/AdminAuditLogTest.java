package com.chalkak.backend.admin.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chalkak.backend.exception.BusinessException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class AdminAuditLogTest {

    private static final UUID ACTOR_ADMIN_ID = UUID.randomUUID();
    private static final UUID TARGET_ID = UUID.randomUUID();
    private static final UUID REQUEST_ID = UUID.randomUUID();
    private static final Instant OCCURRED_AT = Instant.parse("2026-08-27T11:20:00Z");

    @Test
    @DisplayName(
            "관리자 작업의 대상과 변경 전후 상태를 감사 로그로 생성한다"
    )
    void create_validAuditInformation_createsAuditLog() {
        // Given
        Map<String, Object> beforeState = new LinkedHashMap<>();
        beforeState.put("moderationStatus", "PENDING");
        beforeState.put("moderatedAt", null);
        Map<String, Object> afterState = Map.of(
                "moderationStatus", "APPROVED",
                "moderatedAt", OCCURRED_AT,
                "moderatedBy", ACTOR_ADMIN_ID
        );

        // When
        AdminAuditLog auditLog = AdminAuditLog.create(
                ACTOR_ADMIN_ID,
                AdminAction.POST_APPROVED,
                AdminTargetType.POST,
                TARGET_ID,
                "  검수 기준 충족  ",
                AdminAuditSnapshot.from(beforeState),
                AdminAuditSnapshot.from(afterState),
                OCCURRED_AT,
                REQUEST_ID
        );
        beforeState.put("moderationStatus", "REJECTED");

        // Then
        assertThat(auditLog.getActorAdminId()).isEqualTo(ACTOR_ADMIN_ID);
        assertThat(auditLog.getAction()).isEqualTo(AdminAction.POST_APPROVED);
        assertThat(auditLog.getTargetType()).isEqualTo(AdminTargetType.POST);
        assertThat(auditLog.getTargetId()).isEqualTo(TARGET_ID);
        assertThat(auditLog.getReason()).isEqualTo("검수 기준 충족");
        assertThat(auditLog.getBeforeState())
                .containsEntry("moderationStatus", "PENDING")
                .containsEntry("moderatedAt", null);
        assertThat(auditLog.getAfterState()).containsEntry("moderatedAt", OCCURRED_AT.toString());
        assertThat(auditLog.getOccurredAt()).isEqualTo(OCCURRED_AT);
        assertThat(auditLog.getRequestId()).isEqualTo(REQUEST_ID);
        assertThatThrownBy(() -> auditLog.getBeforeState().put("status", "changed"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("선택 사유가 비어 있으면 null로 정규화한다")
    void create_blankReason_normalizesToNull() {
        // When
        AdminAuditLog auditLog = createAuditLog(
                "   ",
                pendingPostState(),
                approvedPostState(ACTOR_ADMIN_ID)
        );

        // Then
        assertThat(auditLog.getReason()).isNull();
    }

    @Test
    @DisplayName(
            "동작과 대상 유형이 맞지 않으면 감사 로그를 생성할 수 없다"
    )
    void create_mismatchedActionAndTarget_throwsBusinessException() {
        // When & Then
        assertThatThrownBy(() -> AdminAuditLog.create(
                ACTOR_ADMIN_ID,
                AdminAction.USER_BANNED,
                AdminTargetType.POST,
                TARGET_ID,
                null,
                AdminAuditSnapshot.from(Map.of("moderationStatus", "PENDING")),
                AdminAuditSnapshot.from(Map.of("moderationStatus", "APPROVED")),
                OCCURRED_AT,
                REQUEST_ID
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessage("관리자 감사 로그 정보가 올바르지 않습니다.");
    }

    @Test
    @DisplayName("승인 작업에 거절 상태 전이를 기록할 수 없다")
    void create_approvedActionWithRejectedTransition_throwsBusinessException() {
        // When & Then
        assertThatThrownBy(() -> createAuditLog(
                null,
                pendingPostState(),
                Map.of(
                        "moderationStatus", "REJECTED",
                        "moderatedAt", OCCURRED_AT,
                        "moderatedBy", ACTOR_ADMIN_ID
                )
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessage("관리자 감사 로그 정보가 올바르지 않습니다.");
    }

    @Test
    @DisplayName("승인 작업에는 검수 시각과 작업자가 모두 필요하다")
    void create_approvedActionWithoutModerationMetadata_throwsBusinessException() {
        // When & Then
        assertThatThrownBy(() -> createAuditLog(
                null,
                pendingPostState(),
                Map.of("moderationStatus", "APPROVED")
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessage("관리자 감사 로그 정보가 올바르지 않습니다.");
    }

    @Test
    @DisplayName("검수 작업자는 감사 로그 작업자와 같아야 한다")
    void create_mismatchedModerator_throwsBusinessException() {
        // When & Then
        assertThatThrownBy(() -> createAuditLog(
                null,
                pendingPostState(),
                Map.of(
                        "moderationStatus", "APPROVED",
                        "moderatedAt", OCCURRED_AT,
                        "moderatedBy", UUID.randomUUID()
                )
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessage("관리자 감사 로그 정보가 올바르지 않습니다.");
    }

    @Test
    @DisplayName("회원 정지 작업에 같은 상태를 기록할 수 없다")
    void create_userBanWithoutTransition_throwsBusinessException() {
        // When & Then
        assertThatThrownBy(() -> AdminAuditLog.create(
                ACTOR_ADMIN_ID,
                AdminAction.USER_BANNED,
                AdminTargetType.USER,
                TARGET_ID,
                "운영 정책 위반",
                AdminAuditSnapshot.from(Map.of("status", "ACTIVE")),
                AdminAuditSnapshot.from(Map.of("status", "ACTIVE")),
                OCCURRED_AT,
                REQUEST_ID
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessage("관리자 감사 로그 정보가 올바르지 않습니다.");
    }

    @Test
    @DisplayName("주제 수정 작업에는 수정 전후의 전체 업무 상태가 필요하다")
    void create_partialTopicUpdate_throwsBusinessException() {
        // When & Then
        assertThatThrownBy(() -> AdminAuditLog.create(
                ACTOR_ADMIN_ID,
                AdminAction.TOPIC_UPDATED,
                AdminTargetType.TOPIC,
                TARGET_ID,
                null,
                AdminAuditSnapshot.from(Map.of("title", "이전 주제")),
                AdminAuditSnapshot.from(Map.of("title", "새 주제")),
                OCCURRED_AT,
                REQUEST_ID
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessage("관리자 감사 로그 정보가 올바르지 않습니다.");
    }

    @ParameterizedTest
    @MethodSource("validActionStateChanges")
    @DisplayName("작업별 올바른 상태 전이를 감사 로그로 생성한다")
    void create_validActionStateChange_createsAuditLog(
            AdminAction action,
            AdminTargetType targetType,
            String reason,
            Map<String, Object> beforeState,
            Map<String, Object> afterState
    ) {
        // When
        AdminAuditLog auditLog = AdminAuditLog.create(
                ACTOR_ADMIN_ID,
                action,
                targetType,
                TARGET_ID,
                reason,
                AdminAuditSnapshot.from(beforeState),
                AdminAuditSnapshot.from(afterState),
                OCCURRED_AT,
                REQUEST_ID
        );

        // Then
        assertThat(auditLog.getAction()).isEqualTo(action);
        assertThat(auditLog.getTargetType()).isEqualTo(targetType);
    }

    @Test
    @DisplayName("필수 식별자가 없으면 감사 로그를 생성할 수 없다")
    void create_missingRequiredIdentifier_throwsBusinessException() {
        // When & Then
        assertThatThrownBy(() -> AdminAuditLog.create(
                null,
                AdminAction.POST_APPROVED,
                AdminTargetType.POST,
                TARGET_ID,
                null,
                AdminAuditSnapshot.from(Map.of("moderationStatus", "PENDING")),
                AdminAuditSnapshot.from(Map.of("moderationStatus", "APPROVED")),
                OCCURRED_AT,
                REQUEST_ID
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessage("관리자 감사 로그 정보가 올바르지 않습니다.");
    }

    @Test
    @DisplayName("사유가 500자를 초과하면 감사 로그를 생성할 수 없다")
    void create_tooLongReason_throwsBusinessException() {
        // When & Then
        assertThatThrownBy(() -> createAuditLog(
                "가".repeat(501),
                pendingPostState(),
                approvedPostState(ACTOR_ADMIN_ID)
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessage("관리자 감사 로그 정보가 올바르지 않습니다.");
    }

    @Test
    @DisplayName("사유는 코드 포인트 기준 500자까지 저장한다")
    void create_maxLengthReason_createsAuditLog() {
        // When
        AdminAuditLog auditLog = createAuditLog(
                "📸".repeat(500),
                pendingPostState(),
                approvedPostState(ACTOR_ADMIN_ID)
        );

        // Then
        assertThat(auditLog.getReason()).hasSize(1_000);
        assertThat(auditLog.getReason().codePointCount(0, auditLog.getReason().length()))
                .isEqualTo(500);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "처리 사유 accessToken=secret-value",
            "https://hooks.slack.com/services/T000/B000/SECRET",
            "fcmToken=fake-device-registration-token",
            "https://storage.example.com/private/photo.webp?version=1",
            "data:image/png;base64,AAAA"
    })
    @DisplayName(
            "사유에 명백한 민감정보 값이 있으면 감사 로그를 생성할 수 없다"
    )
    void create_reasonContainingSensitiveValue_throwsBusinessException(String reason) {
        // When & Then
        assertThatThrownBy(() -> createAuditLog(
                reason,
                pendingPostState(),
                approvedPostState(ACTOR_ADMIN_ID)
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessage(
                        "민감한 정보는 관리자 감사 로그에 저장할 수 없습니다."
                )
                .hasMessageNotContaining(reason);
    }

    @Test
    @DisplayName("민감값이 없는 일반 문장과 문서 URL은 사유로 저장한다")
    void create_benignReason_createsAuditLog() {
        // Given
        String reason = "토큰 만료 관련 문의: https://docs.example.com/admin/moderation";

        // When
        AdminAuditLog auditLog = createAuditLog(
                reason,
                pendingPostState(),
                approvedPostState(ACTOR_ADMIN_ID)
        );

        // Then
        assertThat(auditLog.getReason()).isEqualTo(reason);
    }

    @Test
    @DisplayName("거절 작업에 사유가 없으면 감사 로그를 생성할 수 없다")
    void create_rejectionWithoutReason_throwsBusinessException() {
        // When & Then
        assertThatThrownBy(() -> AdminAuditLog.create(
                ACTOR_ADMIN_ID,
                AdminAction.POST_REJECTED,
                AdminTargetType.POST,
                TARGET_ID,
                "   ",
                AdminAuditSnapshot.from(pendingPostState()),
                AdminAuditSnapshot.from(rejectedPostState(ACTOR_ADMIN_ID)),
                OCCURRED_AT,
                REQUEST_ID
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessage("관리자 감사 로그 정보가 올바르지 않습니다.");
    }

    @Test
    @DisplayName(
            "변경 전후 상태가 모두 비어 있으면 감사 로그를 생성할 수 없다"
    )
    void create_withoutChangedState_throwsBusinessException() {
        // When & Then
        assertThatThrownBy(() -> createAuditLog(null, Map.of(), Map.of()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("관리자 감사 로그 정보가 올바르지 않습니다.");
    }

    @Test
    @DisplayName(
            "작업별 허용목록 밖의 상태 필드는 감사 로그에 저장할 수 없다"
    )
    void create_stateFieldOutsideActionAllowlist_throwsBusinessException() {
        // When & Then
        assertThatThrownBy(() -> createAuditLog(
                null,
                Map.of("email", "user@example.com"),
                Map.of("moderationStatus", "APPROVED")
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessage("관리자 감사 로그 정보가 올바르지 않습니다.");
    }

    @Test
    @DisplayName(
            "중첩 상태에 비밀번호나 토큰이 있으면 "
                    + "감사 로그를 생성할 수 없다"
    )
    void create_stateContainingCredential_throwsBusinessException() {
        // Given
        Map<String, Object> beforeState = Map.of(
                "account",
                Map.of("passwordHash", "hash", "accessToken", "token")
        );

        // When & Then
        assertThatThrownBy(() -> createAuditLog(null, beforeState, Map.of()))
                .isInstanceOf(BusinessException.class)
                .hasMessage(
                        "민감한 정보는 관리자 감사 로그에 저장할 수 없습니다."
                );
    }

    @Test
    @DisplayName(
            "이미지 URL이나 스토리지 키가 있으면 "
                    + "감사 로그를 생성할 수 없다"
    )
    void create_stateContainingImageReference_throwsBusinessException() {
        // Given
        Map<String, Object> afterState = Map.of(
                "changes",
                List.of(Map.of("originalStorageKey", "chalkak/private/image.webp"))
        );

        // When & Then
        assertThatThrownBy(() -> createAuditLog(null, Map.of(), afterState))
                .isInstanceOf(BusinessException.class)
                .hasMessage(
                        "민감한 정보는 관리자 감사 로그에 저장할 수 없습니다."
                );
    }

    @Test
    @DisplayName("바이너리 객체는 감사 로그 상태에 저장할 수 없다")
    void create_stateContainingBinaryValue_throwsBusinessException() {
        // When & Then
        assertThatThrownBy(() -> createAuditLog(
                null,
                Map.of("payload", new byte[]{1, 2, 3}),
                Map.of()
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessage("관리자 감사 로그 상태가 올바르지 않습니다.");
    }

    private AdminAuditLog createAuditLog(
            String reason,
            Map<String, Object> beforeState,
            Map<String, Object> afterState
    ) {
        return AdminAuditLog.create(
                ACTOR_ADMIN_ID,
                AdminAction.POST_APPROVED,
                AdminTargetType.POST,
                TARGET_ID,
                reason,
                AdminAuditSnapshot.from(beforeState),
                AdminAuditSnapshot.from(afterState),
                OCCURRED_AT,
                REQUEST_ID
        );
    }

    private static Stream<Arguments> validActionStateChanges() {
        Map<String, Object> topicBeforeState = topicState("이전 주제");
        Map<String, Object> topicAfterState = topicState("새 주제");
        return Stream.of(
                Arguments.of(
                        AdminAction.POST_APPROVED,
                        AdminTargetType.POST,
                        null,
                        pendingPostState(),
                        approvedPostState(ACTOR_ADMIN_ID)
                ),
                Arguments.of(
                        AdminAction.POST_REJECTED,
                        AdminTargetType.POST,
                        "검수 기준 미충족",
                        pendingPostState(),
                        rejectedPostState(ACTOR_ADMIN_ID)
                ),
                Arguments.of(
                        AdminAction.POST_DELETED,
                        AdminTargetType.POST,
                        "운영 정책 위반",
                        postDeletionState("APPROVED", null),
                        postDeletionState("APPROVED", OCCURRED_AT)
                ),
                Arguments.of(
                        AdminAction.USER_BANNED,
                        AdminTargetType.USER,
                        "운영 정책 위반",
                        Map.of("status", "ACTIVE"),
                        Map.of("status", "BANNED")
                ),
                Arguments.of(
                        AdminAction.USER_UNBANNED,
                        AdminTargetType.USER,
                        "정지 사유 해소",
                        Map.of("status", "BANNED"),
                        Map.of("status", "ACTIVE")
                ),
                Arguments.of(
                        AdminAction.TOPIC_CREATED,
                        AdminTargetType.TOPIC,
                        null,
                        Map.of(),
                        topicAfterState
                ),
                Arguments.of(
                        AdminAction.TOPIC_UPDATED,
                        AdminTargetType.TOPIC,
                        null,
                        topicBeforeState,
                        topicAfterState
                ),
                Arguments.of(
                        AdminAction.TOPIC_DELETED,
                        AdminTargetType.TOPIC,
                        "잘못 생성된 주제",
                        topicDeletionState(topicAfterState, null),
                        topicDeletionState(topicAfterState, OCCURRED_AT)
                )
        );
    }

    private static Map<String, Object> pendingPostState() {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("moderationStatus", "PENDING");
        state.put("moderatedAt", null);
        return state;
    }

    private static Map<String, Object> approvedPostState(UUID moderatedBy) {
        return Map.of(
                "moderationStatus", "APPROVED",
                "moderatedAt", OCCURRED_AT,
                "moderatedBy", moderatedBy
        );
    }

    private static Map<String, Object> rejectedPostState(UUID moderatedBy) {
        return Map.of(
                "moderationStatus", "REJECTED",
                "moderatedAt", OCCURRED_AT,
                "moderatedBy", moderatedBy
        );
    }

    private static Map<String, Object> postDeletionState(
            String moderationStatus,
            Instant deletedAt
    ) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("moderationStatus", moderationStatus);
        state.put("deletedAt", deletedAt);
        return state;
    }

    private static Map<String, Object> topicState(String title) {
        return Map.of(
                "title", title,
                "topicDate", LocalDate.of(2026, 8, 27),
                "startsAt", Instant.parse("2026-08-27T00:00:00Z"),
                "endsAt", Instant.parse("2026-08-27T12:00:00Z")
        );
    }

    private static Map<String, Object> topicDeletionState(
            Map<String, Object> topicState,
            Instant deletedAt
    ) {
        Map<String, Object> state = new LinkedHashMap<>(topicState);
        state.put("deletedAt", deletedAt);
        return state;
    }
}
