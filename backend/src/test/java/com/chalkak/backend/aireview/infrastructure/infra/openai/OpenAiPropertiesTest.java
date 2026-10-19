package com.chalkak.backend.aireview.infrastructure.infra.openai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class OpenAiPropertiesTest {

    private static final URI BASE_URL = URI.create("https://api.openai.com/v1");

    @Test
    @DisplayName("생성_모든 값이 올바르면_성공한다")
    void create_validValues_succeeds() {
        // When & Then
        assertThatCode(() -> new OpenAiProperties(
                "test-key", BASE_URL, Duration.ofSeconds(5), Duration.ofSeconds(60)))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    @DisplayName("생성_API 키가 비어 있으면_거부한다")
    void create_blankApiKey_throwsException(String apiKey) {
        // When & Then
        assertThatThrownBy(() -> new OpenAiProperties(
                apiKey, BASE_URL, Duration.ofSeconds(5), Duration.ofSeconds(60)))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("생성_연결 제한 시간이 0이면_거부한다")
    void create_zeroConnectTimeout_throwsException() {
        // When & Then
        assertThatThrownBy(() -> new OpenAiProperties(
                "test-key", BASE_URL, Duration.ZERO, Duration.ofSeconds(60)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("생성_응답 제한 시간이 음수이면_거부한다")
    void create_negativeReadTimeout_throwsException() {
        // When & Then
        assertThatThrownBy(() -> new OpenAiProperties(
                "test-key", BASE_URL, Duration.ofSeconds(5), Duration.ofSeconds(-1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("toString_API 키를 노출하지 않는다")
    void toString_doesNotExposeApiKey() {
        // Given
        OpenAiProperties properties = new OpenAiProperties(
                "sk-secret-value", BASE_URL, Duration.ofSeconds(5), Duration.ofSeconds(60));

        // When & Then
        assertThat(properties.toString()).doesNotContain("sk-secret-value");
    }
}
