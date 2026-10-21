package com.chalkak.backend.aireview.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chalkak.backend.aireview.domain.AiPromptParameters.ImageDetail;
import com.chalkak.backend.aireview.domain.AiPromptParameters.ReasoningEffort;
import com.chalkak.backend.exception.BusinessException;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.util.ReflectionTestUtils;

class AiPromptVersionTest {

    private static final UUID ADMIN_ID = UUID.fromString("019a0000-0000-7000-8000-000000000688");
    private static final AiPromptParameters PARAMETERS = new AiPromptParameters(
            ImageDetail.LOW,
            700,
            ReasoningEffort.HIGH,
            6.5
    );

    @Test
    @DisplayName("버전 생성은 초안 값과 유형의 출력 스키마 버전을 그대로 담는다")
    void create_validDraft_keepsDraftAndSchemaVersion() {
        // When
        AiPromptVersion version = AiPromptVersion.create(
                draft(),
                3,
                "메모",
                ADMIN_ID
        );

        // Then
        assertThat(version.getType()).isEqualTo(AiPromptType.PHOTO_REVIEW);
        assertThat(version.getVersion()).isEqualTo(3);
        assertThat(version.getSystemPrompt()).isEqualTo("시스템");
        assertThat(version.getUserPromptTemplate()).isEqualTo("주제는 {{topicTitle}}");
        assertThat(version.getModel()).isEqualTo("gpt-6.1-sol");
        assertThat(version.getParameters()).isEqualTo(PARAMETERS);
        assertThat(version.getOutputSchemaVersion())
                .isEqualTo(String.valueOf(PhotoReviewOutput.schemaVersion()));
        assertThat(version.getMemo()).isEqualTo("메모");
        assertThat(version.getCreatedByAdminId()).isEqualTo(ADMIN_ID);
    }

    @Test
    @DisplayName("메모는 앞뒤 공백을 제거하고 비어 있으면 null로 저장한다")
    void create_blankMemo_storesNull() {
        // When & Then
        assertThat(AiPromptVersion.create(
                draft(),
                1,
                "  ",
                ADMIN_ID
        ).getMemo()).isNull();
        assertThat(AiPromptVersion.create(
                draft(),
                1,
                " 메모 ",
                ADMIN_ID
        ).getMemo())
                .isEqualTo("메모");
    }

    @Test
    @DisplayName("메모 길이는 앞뒤 공백을 제거한 뒤 유니코드 코드포인트 500개까지 받는다")
    void create_memoLength_validatesLimitByCodePoints() {
        // When & Then
        assertThatCode(() -> AiPromptVersion.create(
                draft(),
                1,
                "가".repeat(500),
                ADMIN_ID
        ))
                .doesNotThrowAnyException();
        assertThatCode(() -> AiPromptVersion.create(
                draft(),
                1,
                "😀".repeat(500),
                ADMIN_ID
        ))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> AiPromptVersion.create(
                draft(),
                1,
                "😀".repeat(501),
                ADMIN_ID
        ))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> AiPromptVersion.create(
                draft(),
                1,
                "가".repeat(501),
                ADMIN_ID
        ))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("앞뒤 공백은 길이에 세지 않고 제거해 저장하며, 공백뿐인 긴 메모는 null이다")
    void create_memoWithSurroundingWhitespace_countsAfterStrip() {
        // When
        AiPromptVersion trimmed = AiPromptVersion.create(
                draft(),
                1,
                " " + "가".repeat(500) + " ",
                ADMIN_ID
        );
        AiPromptVersion blank = AiPromptVersion.create(
                draft(),
                1,
                " ".repeat(501),
                ADMIN_ID
        );

        // Then
        assertThat(trimmed.getMemo()).isEqualTo("가".repeat(500));
        assertThat(blank.getMemo()).isNull();
    }

    @Test
    @DisplayName("버전이 1 미만이거나 초안·관리자가 없으면 거절한다")
    void create_invalidArguments_throwsBusinessException() {
        // When & Then
        assertThatThrownBy(() -> AiPromptVersion.create(
                draft(),
                0,
                null,
                ADMIN_ID
        ))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> AiPromptVersion.create(
                null,
                1,
                null,
                ADMIN_ID
        ))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> AiPromptVersion.create(
                draft(),
                1,
                null,
                null
        ))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("출력 스키마 버전이 유형의 현재 버전과 같으면 호환된다")
    void hasCurrentOutputSchema_sameSchemaVersion_returnsTrue() {
        // Given
        AiPromptVersion version = AiPromptVersion.create(
                draft(),
                1,
                null,
                ADMIN_ID
        );

        // When & Then
        assertThat(version.hasCurrentOutputSchema()).isTrue();
    }

    @Test
    @DisplayName("출력 스키마 버전이 유형의 현재 버전과 다르면 호환되지 않는다")
    void hasCurrentOutputSchema_differentSchemaVersion_returnsFalse() {
        // Given
        AiPromptVersion version = AiPromptVersion.create(
                draft(),
                1,
                null,
                ADMIN_ID
        );
        ReflectionTestUtils.setField(
                version,
                "outputSchemaVersion",
                "0"
        );

        // When & Then
        assertThat(version.hasCurrentOutputSchema()).isFalse();
    }

    @ParameterizedTest
    @CsvSource({
            "gpt-6.1-sol, 1, true",
            "gpt-6.1-sol, 0, false",
            "gpt-unknown, 1, false"
    })
    @DisplayName("현재 출력 스키마이고 허용된 모델일 때만 실행할 수 있다")
    void isRunnableWith_byOutputSchemaAndModel(
            String model,
            String outputSchemaVersion,
            boolean expected
    ) {
        // Given
        AiPromptVersion version = AiPromptVersion.create(
                draft(),
                1,
                null,
                ADMIN_ID
        );
        ReflectionTestUtils.setField(
                version,
                "model",
                model
        );
        ReflectionTestUtils.setField(
                version,
                "outputSchemaVersion",
                outputSchemaVersion
        );

        // When & Then
        assertThat(version.isRunnableWith("gpt-6.1-sol"::equals)).isEqualTo(expected);
    }

    @Test
    @DisplayName("버전에서 만든 초안은 버전을 만든 초안과 같은 값이다")
    void toDraft_createdVersion_returnsSameDraft() {
        // Given
        AiPromptVersion version = AiPromptVersion.create(
                draft(),
                1,
                "메모",
                ADMIN_ID
        );

        // When & Then
        assertThat(version.toDraft()).isEqualTo(draft());
    }

    private AiPromptDraft draft() {
        return new AiPromptDraft(
                AiPromptType.PHOTO_REVIEW,
                "시스템",
                "주제는 {{topicTitle}}",
                "gpt-6.1-sol",
                PARAMETERS
        );
    }
}
