package com.chalkak.backend.aireview.service;

import com.chalkak.backend.aireview.domain.AiReviewResult;
import com.chalkak.backend.aireview.domain.PhotoReviewOutput;
import java.time.Duration;

/**
 * 생성 성공 결과. {@code rawOutput}은 모델이 돌려준 output_text 그대로이며, 검증을 거친
 * {@code output}과 달리 가공하지 않는다.
 */
public record AiReviewGeneration(
        AiReviewResult result,
        PhotoReviewOutput output,
        String rawOutput,
        Attempt attempt
) {

    /**
     * 응답 본문을 받은 호출의 기록. 성공과 실패 모두 같은 형태로 사용량·지연시간·응답 ID를 남긴다.
     */
    public record Attempt(
            Usage usage,
            Duration latency,
            String responseId
    ) {
    }

    /**
     * OpenAI가 보고한 토큰 사용량. 각 값이 null이면 OpenAI가 해당 사용량을 주지 않은 것이고, 0은 실제로 0이라고 보고한
     * 값이다.
     */
    public record Usage(
            Integer inputTokens,
            Integer cachedInputTokens,
            Integer outputTokens,
            Integer reasoningTokens
    ) {
    }
}
