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

class AiPromptVersionAuditTransitionTest {

    private static final Map<String, Object> VALID = Map.of(
            "type",
            "PHOTO_REVIEW",
            "version",
            1
    );

    @Test
    @DisplayName("프롬프트 버전 생성은 빈 이전 상태와 유형·버전만 있는 이후 상태를 허용한다")
    void create_validTransition_recordsTypeAndVersion() {
        // When
        AdminAuditLog audit = create(
                Map.of(),
                VALID,
                AdminTargetType.AI_PROMPT_VERSION
        );

        // Then
        assertThat(audit.getBeforeState()).isEmpty();
        assertThat(audit.getAfterState()).containsExactlyInAnyOrderEntriesOf(VALID);
    }

    @ParameterizedTest
    @MethodSource("invalidTransitions")
    @DisplayName("프롬프트 버전 생성은 잘못된 대상·이전 상태·필드·값을 거절한다")
    void create_invalidTransition_throwsBusinessException(
            Map<String, Object> before,
            Map<String, Object> after,
            AdminTargetType targetType
    ) {
        // When & Then
        assertThatThrownBy(() -> create(
                before,
                after,
                targetType
        ))
                .isInstanceOf(BusinessException.class);
    }

    private static Stream<Arguments> invalidTransitions() {
        return Stream.of(
                Arguments.of(
                        VALID,
                        VALID,
                        AdminTargetType.AI_PROMPT_VERSION
                ),
                Arguments.of(
                        Map.of(),
                        VALID,
                        AdminTargetType.BANNER
                ),
                Arguments.of(
                        Map.of(),
                        Map.of("type", "PHOTO_REVIEW"),
                        AdminTargetType.AI_PROMPT_VERSION
                ),
                Arguments.of(
                        Map.of(),
                        Map.of("version", 1),
                        AdminTargetType.AI_PROMPT_VERSION
                ),
                Arguments.of(
                        Map.of(),
                        Map.of(
                                "type",
                                "PHOTO_REVIEW",
                                "version",
                                1,
                                "model",
                                "gpt-6.1-sol"
                        ),
                        AdminTargetType.AI_PROMPT_VERSION
                ),
                Arguments.of(
                        Map.of(),
                        Map.of(
                                "type",
                                "UNKNOWN",
                                "version",
                                1
                        ),
                        AdminTargetType.AI_PROMPT_VERSION
                ),
                Arguments.of(
                        Map.of(),
                        Map.of(
                                "type",
                                1,
                                "version",
                                1
                        ),
                        AdminTargetType.AI_PROMPT_VERSION
                ),
                Arguments.of(
                        Map.of(),
                        Map.of(
                                "type",
                                "PHOTO_REVIEW",
                                "version",
                                0
                        ),
                        AdminTargetType.AI_PROMPT_VERSION
                ),
                Arguments.of(
                        Map.of(),
                        Map.of(
                                "type",
                                "PHOTO_REVIEW",
                                "version",
                                -1
                        ),
                        AdminTargetType.AI_PROMPT_VERSION
                ),
                Arguments.of(
                        Map.of(),
                        Map.of(
                                "type",
                                "PHOTO_REVIEW",
                                "version",
                                "1"
                        ),
                        AdminTargetType.AI_PROMPT_VERSION
                ),
                Arguments.of(
                        Map.of(),
                        Map.of(
                                "type",
                                "PHOTO_REVIEW",
                                "version",
                                1.5
                        ),
                        AdminTargetType.AI_PROMPT_VERSION
                )
        );
    }

    private AdminAuditLog create(
            Map<String, Object> before,
            Map<String, Object> after,
            AdminTargetType targetType
    ) {
        return AdminAuditLog.create(
                UUID.randomUUID(),
                AdminAction.AI_PROMPT_VERSION_CREATED,
                targetType,
                UUID.randomUUID(),
                null,
                AdminAuditSnapshot.from(before),
                AdminAuditSnapshot.from(after),
                Instant.now(),
                UUID.randomUUID()
        );
    }
}
