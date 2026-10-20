package com.chalkak.backend.aireview.infrastructure.infra.openai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class OpenAiPropertiesTest {

    private static final URI BASE_URL = URI.create("https://api.openai.com/v1");
    private static final List<String> MODELS = List.of("gpt-6.1-sol");

    @Test
    @DisplayName("생성_모든 값이 올바르면_성공한다")
    void create_validValues_succeeds() {
        // When & Then
        assertThatCode(() -> new OpenAiProperties(
                "test-key",
                BASE_URL,
                Duration.ofSeconds(5),
                Duration.ofSeconds(60),
                MODELS
        ))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    @DisplayName("생성_API 키가 비어 있으면_거부한다")
    void create_blankApiKey_throwsException(String apiKey) {
        // When & Then
        assertThatThrownBy(() -> new OpenAiProperties(
                apiKey,
                BASE_URL,
                Duration.ofSeconds(5),
                Duration.ofSeconds(60),
                MODELS
        ))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("생성_연결 제한 시간이 0이면_거부한다")
    void create_zeroConnectTimeout_throwsException() {
        // When & Then
        assertThatThrownBy(() -> new OpenAiProperties(
                "test-key",
                BASE_URL,
                Duration.ZERO,
                Duration.ofSeconds(60),
                MODELS
        ))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("생성_응답 제한 시간이 음수이면_거부한다")
    void create_negativeReadTimeout_throwsException() {
        // When & Then
        assertThatThrownBy(() -> new OpenAiProperties(
                "test-key",
                BASE_URL,
                Duration.ofSeconds(5),
                Duration.ofSeconds(-1),
                MODELS
        ))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("생성_허용 모델 목록이 null이거나 비어 있으면_거부한다")
    void create_emptyAllowedModels_throwsException() {
        // When & Then
        assertThatThrownBy(() -> new OpenAiProperties(
                "test-key",
                BASE_URL,
                Duration.ofSeconds(5),
                Duration.ofSeconds(60),
                null
        ))
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> new OpenAiProperties(
                "test-key",
                BASE_URL,
                Duration.ofSeconds(5),
                Duration.ofSeconds(60),
                List.of()
        ))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("생성_허용 모델에 공백 항목이 있으면_거부한다")
    void create_blankAllowedModel_throwsException() {
        // When & Then
        assertThatThrownBy(() -> new OpenAiProperties(
                "test-key",
                BASE_URL,
                Duration.ofSeconds(5),
                Duration.ofSeconds(60),
                List.of("gpt-6.1-sol", " ")
        ))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("toString_API 키를 노출하지 않는다")
    void toString_doesNotExposeApiKey() {
        // Given
        OpenAiProperties properties = new OpenAiProperties(
                "sk-secret-value",
                BASE_URL,
                Duration.ofSeconds(5),
                Duration.ofSeconds(60),
                MODELS
        );

        // When & Then
        assertThat(properties.toString()).doesNotContain("sk-secret-value");
    }
}
