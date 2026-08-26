package com.chalkak.backend.post.service;

import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 이미지 처리 결과를 언제까지 기다릴지 정하는 정책.
 *
 * <p>콜백이 유실되거나 영구 거부되면 게시물이 검수 대기 상태에 그대로 남는다. 피드와 단건 조회는 공개된
 * 게시물만 보여주므로 작성자에게는 사라진 게시물이고, 중복 판정에는 걸려 같은 주제에 다시 올릴 수도 없다.
 * 이 시간을 넘긴 검수 대기 게시물은 처리가 끝나지 않은 것으로 보고 거절 처리한다.
 *
 * <p>정상 처리는 수 초 안에 끝나므로 기본값은 넉넉한 편이다. 운영에서 조정할 수 있도록
 * {@code POST_IMAGE_PROCESSING_TIMEOUT} 환경 변수로 뺐다.
 */
@Component
public class PostProcessingPolicy {

    private final Duration imageProcessingTimeout;

    public PostProcessingPolicy(
            @Value("${chalkak.post.image-processing-timeout}") Duration imageProcessingTimeout
    ) {
        if (imageProcessingTimeout == null || imageProcessingTimeout.isNegative()
                || imageProcessingTimeout.isZero()) {
            throw new IllegalArgumentException(
                    "chalkak.post.image-processing-timeout은 0보다 커야 합니다."
            );
        }
        this.imageProcessingTimeout = imageProcessingTimeout;
    }

    public boolean isProcessingTimedOut(Instant startedAt, Instant now) {
        return startedAt.plus(imageProcessingTimeout).isBefore(now);
    }

    public Duration getImageProcessingTimeout() {
        return imageProcessingTimeout;
    }
}
