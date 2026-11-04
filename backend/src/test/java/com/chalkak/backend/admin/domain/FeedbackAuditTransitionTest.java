package com.chalkak.backend.admin.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chalkak.backend.exception.BusinessException;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class FeedbackAuditTransitionTest {

    private static final String ANSWERED_AT = "2026-11-03T11:00:00Z";
    private static final String DELETED_AT = "2026-11-03T12:00:00Z";

    @Test
    @DisplayName("문의 삭제 감사는 원문 없이 삭제 시각의 전이만 기록한다")
    void create_validDeletion_recordsOnlyDeletionTime() {
        // When
        AdminAuditLog audit = create(
                Collections.singletonMap("deletedAt", null),
                Map.of("deletedAt", DELETED_AT),
                AdminTargetType.FEEDBACK
        );

        // Then
        assertThat(audit.getReason()).isNull();
        assertThat(audit.getBeforeState()).containsOnlyKeys("deletedAt");
        assertThat(audit.getBeforeState().get("deletedAt")).isNull();
        assertThat(audit.getAfterState()).containsExactlyEntriesOf(Map.of("deletedAt", DELETED_AT));
    }

    @ParameterizedTest
    @MethodSource("invalidTransitions")
    @DisplayName("문의 삭제 감사는 잘못된 대상·전이·원문 필드를 거절한다")
    void create_invalidDeletion_rejectsAudit(
            Map<String, Object> before,
            Map<String, Object> after,
            AdminTargetType target
    ) {
        // When & Then
        assertThatThrownBy(() -> create(
                before,
                after,
                target
        )).isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("문의 답변 감사는 원문 없이 답변 등록·삭제 시각을 기록한다")
    void create_validAnswer_recordsAnswerTimestamps() {
        AdminAuditLog audit = create(
                AdminAction.FEEDBACK_ANSWERED,
                answerState(null, null),
                answerState(DELETED_AT, null),
                AdminTargetType.FEEDBACK
        );

        assertThat(audit.getReason()).isNull();
        assertThat(audit.getBeforeState()).containsEntry("answeredAt", null);
        assertThat(audit.getAfterState())
                .containsExactlyEntriesOf(answerState(DELETED_AT, null));
    }

    @ParameterizedTest
    @MethodSource("invalidAnswerTransitions")
    @DisplayName("문의 답변 감사는 잘못된 대상·시각·원문 필드를 거절한다")
    void create_invalidAnswer_rejectsAudit(
            Map<String, Object> before,
            Map<String, Object> after,
            AdminTargetType target
    ) {
        assertThatThrownBy(() -> create(
                AdminAction.FEEDBACK_ANSWERED,
                before,
                after,
                target
        )).isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("답변 삭제 감사는 같은 답변의 삭제 시각만 추가한다")
    void create_validAnswerDeletion_recordsDeletionTime() {
        AdminAuditLog audit = create(
                AdminAction.FEEDBACK_ANSWER_DELETED,
                answerState(ANSWERED_AT, null),
                answerState(ANSWERED_AT, DELETED_AT),
                AdminTargetType.FEEDBACK
        );

        assertThat(audit.getAfterState())
                .containsExactlyEntriesOf(answerState(ANSWERED_AT, DELETED_AT));
    }

    @ParameterizedTest
    @MethodSource("invalidAnswerDeletionTransitions")
    @DisplayName("답변 삭제 감사에서 다른 답변을 가리키거나 전이가 맞지 않으면 거절한다")
    void create_invalidAnswerDeletion_rejectsAudit(
            Map<String, Object> before,
            Map<String, Object> after
    ) {
        assertThatThrownBy(() -> create(
                AdminAction.FEEDBACK_ANSWER_DELETED,
                before,
                after,
                AdminTargetType.FEEDBACK
        )).isInstanceOf(BusinessException.class);
    }

    private static Stream<Arguments> invalidAnswerDeletionTransitions() {
        return Stream.of(
                Arguments.of(answerState(ANSWERED_AT, null), answerState(DELETED_AT, DELETED_AT)),
                Arguments.of(answerState(ANSWERED_AT, null), answerState(ANSWERED_AT, null)),
                Arguments.of(answerState(ANSWERED_AT, DELETED_AT),
                        answerState(ANSWERED_AT, DELETED_AT))
        );
    }

    @Test
    @DisplayName("삭제된 답변의 재답변 감사는 더 늦은 등록 시각과 삭제 해제를 기록한다")
    void create_validReanswer_recordsNewAnswerState() {
        String reansweredAt = "2026-11-03T13:00:00Z";
        AdminAuditLog audit = create(
                AdminAction.FEEDBACK_ANSWERED,
                answerState(ANSWERED_AT, DELETED_AT),
                answerState(reansweredAt, null),
                AdminTargetType.FEEDBACK
        );

        assertThat(audit.getAfterState())
                .containsExactlyEntriesOf(answerState(reansweredAt, null));
    }

    @ParameterizedTest
    @MethodSource("invalidReanswerTransitions")
    @DisplayName("이전 답변이 삭제되지 않았거나 시각이 증가하지 않으면 재답변 감사를 거절한다")
    void create_invalidReanswer_rejectsAudit(
            Map<String, Object> before,
            Map<String, Object> after
    ) {
        assertThatThrownBy(() -> create(
                AdminAction.FEEDBACK_ANSWERED,
                before,
                after,
                AdminTargetType.FEEDBACK
        )).isInstanceOf(BusinessException.class);
    }

    private static Stream<Arguments> invalidReanswerTransitions() {
        return Stream.of(
                Arguments.of(answerState(ANSWERED_AT, null), answerState(DELETED_AT, null)),
                Arguments.of(answerState(ANSWERED_AT, DELETED_AT), answerState(ANSWERED_AT, null)),
                Arguments.of(answerState(DELETED_AT, DELETED_AT), answerState(ANSWERED_AT, null)),
                Arguments.of(answerState(ANSWERED_AT, DELETED_AT),
                        answerState(DELETED_AT, DELETED_AT))
        );
    }

    private static Stream<Arguments> invalidAnswerTransitions() {
        Map<String, Object> before = answerState(null, null);
        Map<String, Object> after = answerState(DELETED_AT, null);
        return Stream.of(
                Arguments.of(
                        before,
                        after,
                        AdminTargetType.POST
                ),
                Arguments.of(
                        after,
                        after,
                        AdminTargetType.FEEDBACK
                ),
                Arguments.of(
                        before,
                        answerState("invalid", null),
                        AdminTargetType.FEEDBACK
                ),
                Arguments.of(
                        before,
                        Map.of(
                                "answeredAt",
                                DELETED_AT,
                                "answerContent",
                                "원문"
                        ),
                        AdminTargetType.FEEDBACK
                )
        );
    }

    private static Map<String, Object> answerState(String answeredAt, String answerDeletedAt) {
        Map<String, Object> state = new HashMap<>();
        state.put("answeredAt", answeredAt);
        state.put("answerDeletedAt", answerDeletedAt);
        return state;
    }

    private static Stream<Arguments> invalidTransitions() {
        Map<String, Object> before = Collections.singletonMap("deletedAt", null);
        Map<String, Object> after = Map.of("deletedAt", DELETED_AT);
        return Stream.of(
                Arguments.of(
                        before,
                        after,
                        AdminTargetType.POST
                ),
                Arguments.of(
                        after,
                        after,
                        AdminTargetType.FEEDBACK
                ),
                Arguments.of(
                        before,
                        before,
                        AdminTargetType.FEEDBACK
                ),
                Arguments.of(
                        before,
                        Map.of("deletedAt", "invalid"),
                        AdminTargetType.FEEDBACK
                ),
                Arguments.of(
                        Map.of(),
                        after,
                        AdminTargetType.FEEDBACK
                ),
                Arguments.of(
                        before,
                        Map.of(
                                "deletedAt",
                                DELETED_AT,
                                "content",
                                "원문"
                        ),
                        AdminTargetType.FEEDBACK
                )
        );
    }

    private AdminAuditLog create(
            Map<String, Object> before,
            Map<String, Object> after,
            AdminTargetType target
    ) {
        return create(
                AdminAction.FEEDBACK_DELETED,
                before,
                after,
                target
        );
    }

    private AdminAuditLog create(
            AdminAction action,
            Map<String, Object> before,
            Map<String, Object> after,
            AdminTargetType target
    ) {
        return AdminAuditLog.create(
                UUID.randomUUID(),
                action,
                target,
                UUID.randomUUID(),
                null,
                AdminAuditSnapshot.from(before),
                AdminAuditSnapshot.from(after),
                Instant.now(),
                UUID.randomUUID()
        );
    }
}
