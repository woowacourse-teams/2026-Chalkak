package com.chalkak.backend.aireview.infrastructure.infra;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI 리뷰 워커 설정. 값은 application.yml이 정한다. {@code concurrency}는 서버 자원이 아니라 OpenAI의 분당 요청·토큰 한도를
 * 지키기 위한 상한이고, 임대 시간의 하한은 설정 클래스가 시작할 때 검증한다.
 */
@ConfigurationProperties("chalkak.ai-review.worker")
public record AiReviewWorkerProperties(
        int concurrency,
        Duration leaseDuration,
        int maxClaims
) {

    public AiReviewWorkerProperties {
        if (concurrency < 1) {
            throw new IllegalArgumentException("AI 리뷰 동시 처리 수는 1 이상이어야 합니다.");
        }
        if (maxClaims < 1) {
            throw new IllegalArgumentException("AI 리뷰 가져가기 상한은 1 이상이어야 합니다.");
        }
    }
}
