package com.chalkak.backend.aireview.service;

import com.chalkak.backend.aireview.domain.AiPromptType;
import com.chalkak.backend.aireview.domain.AiPromptVersion;
import com.chalkak.backend.aireview.domain.AiReview;
import com.chalkak.backend.aireview.domain.AiReviewResult;
import com.chalkak.backend.aireview.repository.AiPromptVersionRepository;
import com.chalkak.backend.aireview.repository.AiReviewRepository;
import com.chalkak.backend.common.logging.CurrentRequestId;
import com.chalkak.backend.common.logging.LogFields;
import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.exception.NotFoundException;
import com.chalkak.backend.post.repository.PostRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 작성자가 본인 게시물의 AI 리뷰를 요청하고 조회한다. 요청은 대기열에 한 행을 넣을 뿐이며 OpenAI는 호출하지 않는다.
 *
 * <p>
 * 작성자가 아니거나 공개 조건을 만족하지 않는 게시물은 리뷰 존재 여부를 숨기려고 게시물이 없는 것과 같게 404로 응답한다.
 */
@Service
@RequiredArgsConstructor
public class PostAiReviewService {

    private static final Logger AI_REVIEW_LOG = LoggerFactory.getLogger("chalkak.aireview");

    private final AiReviewRepository aiReviewRepository;
    private final AiPromptVersionRepository promptVersionRepository;
    private final PostRepository postRepository;
    private final AiReviewGenerator aiReviewGenerator;
    private final CurrentRequestId currentRequestId;
    private final Clock clock;

    @Transactional
    public ReviewResult request(UUID userId, UUID postId) {
        validateAuthoredVisiblePost(userId, postId);
        return aiReviewRepository.findByPostId(postId)
                .map(ReviewResult::from)
                .orElseGet(() -> createReview(userId, postId));
    }

    @Transactional(readOnly = true)
    public ReviewResult get(UUID userId, UUID postId) {
        validateAuthoredVisiblePost(userId, postId);
        return aiReviewRepository.findByPostId(postId)
                .map(ReviewResult::from)
                .orElseThrow(() -> new NotFoundException(
                        ErrorCode.BUSINESS_ERROR,
                        "AI 리뷰를 찾을 수 없습니다."));
    }

    private ReviewResult createReview(UUID userId, UUID postId) {
        AiPromptVersion version = getUsableLatestVersion();
        AiReview requested = AiReview.request(
                postId,
                userId,
                version.getId(),
                currentRequestId.resolve(),
                clock.instant()
        );
        int inserted = aiReviewRepository.insertIfAbsent(requested);
        AiReview review = aiReviewRepository.findByPostId(postId).orElseThrow();
        if (inserted == 1) {
            logRequested(review, version);
        }
        return ReviewResult.from(review);
    }

    /**
     * 가장 큰 버전만 쓴다. 현재 서버가 해석할 수 없는 버전이면 이전 버전으로 내려가지 않고 기능이 꺼진 것으로 본다.
     */
    private AiPromptVersion getUsableLatestVersion() {
        return promptVersionRepository.findLatestByType(AiPromptType.PHOTO_REVIEW)
                .filter(version -> version.isRunnableWith(aiReviewGenerator::supportsModel))
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.BUSINESS_ERROR,
                        "지금은 AI 리뷰를 요청할 수 없습니다."));
    }

    private void validateAuthoredVisiblePost(UUID userId, UUID postId) {
        postRepository.findVisibleById(postId)
                .filter(post -> post.isAuthoredBy(userId))
                .orElseThrow(() -> new NotFoundException(
                        ErrorCode.BUSINESS_ERROR,
                        "게시물을 찾을 수 없습니다."));
    }

    private void logRequested(AiReview review, AiPromptVersion version) {
        AI_REVIEW_LOG.atInfo()
                .addKeyValue(LogFields.TYPE, LogFields.TYPE_AI_REVIEW)
                .addKeyValue(LogFields.EVENT, "review_requested")
                .addKeyValue("aiReviewId", review.getId())
                .addKeyValue(LogFields.POST_ID, review.getPostId())
                .addKeyValue(LogFields.USER_ID, review.getUserId())
                .addKeyValue("promptVersion", version.getVersion())
                .addKeyValue("model", version.getModel())
                .log("AI 리뷰 요청");
    }

    /**
     * 요청·조회 결과. 앱에는 내부 대기와 처리 중을 합친 상태만 노출하고, 결과는 완료일 때만 있다.
     */
    public record ReviewResult(
            ApiStatus status,
            Instant requestedAt,
            Instant completedAt,
            AiReviewResult result
    ) {

        private static ReviewResult from(AiReview review) {
            return new ReviewResult(
                    ApiStatus.from(review.getStatus()),
                    review.getRequestedAt(),
                    review.getCompletedAt(),
                    review.getResult()
            );
        }

        public boolean isInProgress() {
            return status == ApiStatus.PROCESSING;
        }
    }

    /**
     * 앱에 노출하는 상태. 대기와 처리 중은 앱이 구분할 필요가 없어 하나로 합친다.
     */
    public enum ApiStatus {
        PROCESSING, COMPLETED, FAILED;

        static ApiStatus from(AiReview.Status status) {
            if (status == AiReview.Status.COMPLETED) {
                return COMPLETED;
            }
            if (status == AiReview.Status.FAILED) {
                return FAILED;
            }
            return PROCESSING;
        }
    }
}
