package com.chalkak.backend.aireview.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 게시물 한 건의 AI 리뷰. 게시물당 하나이며 요청 시점에 만든 프롬프트 버전을 끝까지 쓴다.
 */
@Entity
@Table(name = "ai_reviews")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiReview {

    @Id
    @Generated
    @ColumnDefault("uuidv7()")
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "post_id", nullable = false, updatable = false)
    private UUID postId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "prompt_version_id", nullable = false, updatable = false)
    private UUID promptVersionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(nullable = false)
    private int attempt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private StoredResult result;

    @Column(name = "request_id", nullable = false, updatable = false)
    private UUID requestId;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private Instant requestedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    public static AiReview request(
            UUID postId,
            UUID userId,
            UUID promptVersionId,
            UUID requestId,
            Instant requestedAt
    ) {
        AiReview review = new AiReview();
        review.postId = postId;
        review.userId = userId;
        review.promptVersionId = promptVersionId;
        review.requestId = requestId;
        review.requestedAt = requestedAt;
        review.status = Status.QUEUED;
        return review;
    }

    public ApiStatus getApiStatus() {
        return status.toApiStatus();
    }

    /**
     * 서버 안에서 쓰는 처리 단계.
     */
    public enum Status {
        QUEUED,
        PROCESSING,
        COMPLETED,
        FAILED;

        public ApiStatus toApiStatus() {
            if (this == COMPLETED) {
                return ApiStatus.COMPLETED;
            }
            if (this == FAILED) {
                return ApiStatus.FAILED;
            }
            return ApiStatus.PROCESSING;
        }
    }

    /**
     * 앱에 노출하는 상태. 대기와 처리 중은 앱이 구분할 필요가 없어 하나로 합친다.
     */
    public enum ApiStatus {
        PROCESSING,
        COMPLETED,
        FAILED
    }

    /**
     * 앱 응답 계약 그대로 저장한 결과. 필드 이름이 곧 API 계약이므로 바꾸지 않으며, 저장된 값을 다시 계산하지 않고 그대로 돌려준다.
     */
    public record StoredResult(
            double overallScore,
            List<Criterion> criteria,
            boolean distinctivePerspective,
            String feedback
    ) {

        public record Criterion(String key, String label, double score) {
        }
    }
}
