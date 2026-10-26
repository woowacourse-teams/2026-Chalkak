package com.chalkak.backend.admin.service.aireview;

import com.chalkak.backend.admin.repository.AdminPage;
import com.chalkak.backend.admin.repository.aireview.AdminAiReviewQueryRepository;
import com.chalkak.backend.admin.repository.aireview.AdminAiReviewQueryRepository.Criteria;
import com.chalkak.backend.admin.repository.aireview.AdminAiReviewQueryRepository.DetailProjection;
import com.chalkak.backend.admin.repository.aireview.AdminAiReviewQueryRepository.SummaryProjection;
import com.chalkak.backend.aireview.domain.AiReview;
import com.chalkak.backend.aireview.domain.AiReviewResult;
import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.exception.NotFoundException;
import com.chalkak.backend.photo.service.ImageUrlProvider;
import com.chalkak.backend.post.domain.ModerationStatus;
import com.chalkak.backend.user.domain.UserStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 관리자가 전체 AI 리뷰를 상태·프롬프트 버전으로 걸러 보고 하나씩 상세히 본다. 사용자 앱에서 숨겨진 리뷰도 거르지 않는다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminAiReviewQueryService {

    private static final int MAX_PAGE_SIZE = 100;

    private final AdminAiReviewQueryRepository aiReviewQueryRepository;
    private final ImageUrlProvider imageUrlProvider;
    private final ObjectMapper objectMapper;

    public ListResult getAiReviews(
            AiReview.Status status,
            UUID promptVersionId,
            int page,
            int pageSize
    ) {
        validatePagination(page, pageSize);
        AdminPage<SummaryProjection> result = aiReviewQueryRepository.findAiReviews(
                new Criteria(status, promptVersionId),
                page,
                pageSize
        );
        return new ListResult(
                result.currentPage(),
                result.pageSize(),
                result.hasNext(),
                result.items().stream().map(this::toSummary).toList()
        );
    }

    public AiReviewDetail getAiReview(UUID aiReviewId) {
        DetailProjection detail = aiReviewQueryRepository.findAiReview(aiReviewId)
                .orElseThrow(() -> new NotFoundException(
                        ErrorCode.BUSINESS_ERROR,
                        "AI 리뷰를 찾을 수 없습니다."));
        return new AiReviewDetail(
                toSummary(detail.summary()),
                detail.summary().result(),
                detail.output() == null ? null : objectMapper.readTree(detail.output()),
                detail.openaiResponseId(),
                detail.attempt(),
                detail.startedAt(),
                imageUrlProvider.getUrl(detail.originalStorageKey())
        );
    }

    private AiReviewSummary toSummary(SummaryProjection review) {
        return new AiReviewSummary(
                review.aiReviewId(),
                review.status(),
                review.errorCode(),
                review.requestCount(),
                review.result() == null ? null : review.result().overallScore(),
                new PromptVersionSummary(
                        review.promptVersionId(),
                        review.promptVersion(),
                        review.promptModel()
                ),
                new PostSummary(
                        review.postId(),
                        imageUrlProvider.getUrl(review.thumbnailStorageKey()),
                        review.moderationStatus(),
                        review.postDeletedAt()
                ),
                new AuthorSummary(
                        review.authorId(),
                        review.authorEmail(),
                        review.authorStatus(),
                        review.authorDeletedAt()
                ),
                new UsageSummary(
                        review.inputTokens(),
                        review.cachedInputTokens(),
                        review.outputTokens(),
                        review.reasoningTokens()
                ),
                review.latencyMs(),
                review.requestedAt(),
                review.lastRequestedAt(),
                review.completedAt(),
                review.visibleToAuthor()
        );
    }

    private void validatePagination(int page, int pageSize) {
        long offset = ((long) page - 1) * pageSize;
        if (page < 1 || pageSize < 1 || pageSize > MAX_PAGE_SIZE
                || offset > Integer.MAX_VALUE) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "조회 조건이 올바르지 않습니다.");
        }
    }

    public record ListResult(
            int currentPage,
            int pageSize,
            boolean hasNext,
            List<AiReviewSummary> aiReviews
    ) {
    }

    public record AiReviewSummary(
            UUID aiReviewId,
            AiReview.Status status,
            String errorCode,
            int requestCount,
            Double overallScore,
            PromptVersionSummary promptVersion,
            PostSummary post,
            AuthorSummary author,
            UsageSummary usage,
            Long latencyMs,
            Instant requestedAt,
            Instant lastRequestedAt,
            Instant completedAt,
            boolean visibleToAuthor
    ) {
    }

    /**
     * output은 모델이 돌려준 원출력을 JSON 객체로 푼 값이다. 저장 컬럼이 jsonb라 항상 올바른 JSON이다.
     */
    public record AiReviewDetail(
            AiReviewSummary summary,
            AiReviewResult result,
            JsonNode output,
            String openaiResponseId,
            int attempt,
            Instant startedAt,
            String originalImageUrl
    ) {
    }

    public record PromptVersionSummary(
            UUID promptVersionId,
            int version,
            String model
    ) {
    }

    public record PostSummary(
            UUID postId,
            String thumbnailUrl,
            ModerationStatus moderationStatus,
            Instant deletedAt
    ) {
    }

    public record AuthorSummary(
            UUID userId,
            String email,
            UserStatus status,
            Instant deletedAt
    ) {
    }

    public record UsageSummary(
            Integer inputTokens,
            Integer cachedInputTokens,
            Integer outputTokens,
            Integer reasoningTokens
    ) {
    }
}
