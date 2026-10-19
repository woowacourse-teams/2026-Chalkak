package com.chalkak.backend.aireview.infrastructure.infra.openai;

import java.net.URI;
import java.time.Duration;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("chalkak.ai-review.openai")
public record OpenAiProperties(
        String apiKey,
        URI baseUrl,
        Duration connectTimeout,
        Duration readTimeout
) {

    public OpenAiProperties {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("OpenAI API 키가 필요합니다.");
        }
        Objects.requireNonNull(baseUrl, "OpenAI 기본 URL이 필요합니다.");
        validatePositive(connectTimeout, "OpenAI 연결 제한 시간은 0보다 커야 합니다.");
        validatePositive(readTimeout, "OpenAI 응답 제한 시간은 0보다 커야 합니다.");
    }

    /**
     * 로그나 예외에 설정이 찍혀도 API 키가 노출되지 않게 한다.
     */
    @Override
    public String toString() {
        return "OpenAiProperties[baseUrl=" + baseUrl
                + ", connectTimeout=" + connectTimeout
                + ", readTimeout=" + readTimeout + "]";
    }

    private static void validatePositive(Duration duration, String message) {
        Objects.requireNonNull(duration, message);
        if (duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException(message);
        }
    }
}
