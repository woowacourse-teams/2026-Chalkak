package com.chalkak.backend.aireview.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.chalkak.backend.aireview.domain.AiReview.ApiStatus;
import com.chalkak.backend.aireview.domain.AiReview.Status;
import com.chalkak.backend.aireview.domain.AiReview.StoredResult;
import com.chalkak.backend.aireview.domain.AiReview.StoredResult.Criterion;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

class AiReviewTest {

    private static final UUID POST_ID = UUID.fromString("019a0000-0000-7000-8000-000000000001");
    private static final UUID USER_ID = UUID.fromString("019a0000-0000-7000-8000-000000000002");
    private static final UUID PROMPT_VERSION_ID =
            UUID.fromString("019a0000-0000-7000-8000-000000000003");
    private static final UUID REQUEST_ID = UUID.fromString("019a0000-0000-7000-8000-000000000004");
    private static final Instant REQUESTED_AT = Instant.parse("2026-10-20T05:00:00Z");

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Test
    @DisplayName("요청하면 대기 상태로 시작하고 처리 결과와 완료 시각은 비어 있다")
    void request_newReview_startsQueued() {
        // When
        AiReview review = AiReview.request(
                POST_ID, USER_ID, PROMPT_VERSION_ID, REQUEST_ID, REQUESTED_AT);

        // Then
        assertThat(review.getPostId()).isEqualTo(POST_ID);
        assertThat(review.getUserId()).isEqualTo(USER_ID);
        assertThat(review.getPromptVersionId()).isEqualTo(PROMPT_VERSION_ID);
        assertThat(review.getRequestId()).isEqualTo(REQUEST_ID);
        assertThat(review.getRequestedAt()).isEqualTo(REQUESTED_AT);
        assertThat(review.getStatus()).isEqualTo(Status.QUEUED);
        assertThat(review.getAttempt()).isZero();
        assertThat(review.getResult()).isNull();
        assertThat(review.getCompletedAt()).isNull();
        assertThat(review.getApiStatus()).isEqualTo(ApiStatus.PROCESSING);
    }

    @ParameterizedTest
    @CsvSource({
            "QUEUED, PROCESSING",
            "PROCESSING, PROCESSING",
            "COMPLETED, COMPLETED",
            "FAILED, FAILED"
    })
    @DisplayName("내부 대기·처리 상태는 모두 처리 중으로 노출하고 완료·실패는 그대로 노출한다")
    void toApiStatus_mapsInternalStatus(Status status, ApiStatus expected) {
        // When & Then
        assertThat(status.toApiStatus()).isEqualTo(expected);
    }

    @Test
    @DisplayName("저장 결과는 API 계약의 필드 이름으로 직렬화되고 그대로 역직렬화된다")
    void storedResult_roundTrip_keepsFixedFieldNames() throws Exception {
        // Given
        StoredResult result = new StoredResult(
                7.5,
                List.of(new Criterion("COMPOSITION", "구도", 7.5)),
                true,
                "구도가 좋습니다.");

        // When
        String json = jsonMapper.writeValueAsString(result);
        Map<String, Object> tree = jsonMapper.readValue(json, new TypeReference<>() { });
        List<Map<String, Object>> criteria = jsonMapper.convertValue(
                tree.get("criteria"), new TypeReference<>() { });

        // Then
        assertThat(tree.keySet()).containsExactlyInAnyOrder(
                "overallScore", "criteria", "distinctivePerspective", "feedback");
        assertThat(criteria.get(0).keySet()).containsExactlyInAnyOrder("key", "label", "score");
        assertThat(jsonMapper.readValue(json, StoredResult.class)).isEqualTo(result);
    }
}
