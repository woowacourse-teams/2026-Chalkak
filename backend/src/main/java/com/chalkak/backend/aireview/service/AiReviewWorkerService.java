package com.chalkak.backend.aireview.service;

import com.chalkak.backend.aireview.domain.AiPromptVersion;
import com.chalkak.backend.aireview.domain.AiReview;
import com.chalkak.backend.aireview.domain.AiReview.FailureReason;
import com.chalkak.backend.aireview.domain.AiReview.Invocation;
import com.chalkak.backend.aireview.repository.AiPromptVersionRepository;
import com.chalkak.backend.aireview.repository.AiReviewRepository;
import com.chalkak.backend.aireview.service.AiReviewGeneration.Attempt;
import com.chalkak.backend.aireview.service.AiReviewGeneration.Usage;
import com.chalkak.backend.post.domain.Post;
import com.chalkak.backend.post.repository.PostRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 대기열의 AI 리뷰 한 건을 가져가 처리한다. 행을 잠가 가져가는 짧은 트랜잭션과 결과를 반영하는 짧은 트랜잭션 사이에서 OpenAI를
 * 호출하므로 호출하는 동안 DB 연결과 행 잠금을 잡지 않는다.
 *
 * <p>
 * 가져간 행은 임대 시간 안에 끝내야 한다. 워커가 죽거나 종료되면 행은 처리 중으로 남고 임대가 끝난 뒤 다시 가져간다. 그래서
 * 종료·interrupt·DB 오류처럼 호출 결과를 알 수 없는 상황에서는 실패를 쓰지 않고 행을 그대로 둔다. 반복해서 워커를 멈추게 하는
 * 행은 가져간 횟수 상한에서 실패로 끝난다.
 */
public class AiReviewWorkerService {

    private final AiReviewRepository reviewRepository;
    private final AiPromptVersionRepository promptVersionRepository;
    private final PostRepository postRepository;
    private final AiReviewGenerator aiReviewGenerator;
    private final AiReviewGenerationRequestFactory requestFactory;
    private final Clock clock;
    private final Duration leaseDuration;
    private final int maxClaims;
    private final TransactionTemplate transactionTemplate;

    public AiReviewWorkerService(
            AiReviewRepository reviewRepository,
            AiPromptVersionRepository promptVersionRepository,
            PostRepository postRepository,
            AiReviewGenerator aiReviewGenerator,
            AiReviewGenerationRequestFactory requestFactory,
            PlatformTransactionManager transactionManager,
            Clock clock,
            Duration leaseDuration,
            int maxClaims
    ) {
        this.reviewRepository = reviewRepository;
        this.promptVersionRepository = promptVersionRepository;
        this.postRepository = postRepository;
        this.aiReviewGenerator = aiReviewGenerator;
        this.requestFactory = requestFactory;
        this.clock = clock;
        this.leaseDuration = leaseDuration;
        this.maxClaims = maxClaims;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate
                .setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * 가장 오래 기다린 처리 대상을 하나 가져간다. 가져간 횟수 상한을 넘은 행은 처리하지 않고 실패로 끝낸 뒤 다음 행을 찾는다.
     */
    public Optional<Claim> claimNext() {
        return Optional.ofNullable(transactionTemplate.execute(status -> claimOne()));
    }

    /**
     * 가져간 행 하나를 끝까지 처리한다. 예상 가능한 실패는 모두 행의 상태로 기록하며, 결과를 알 수 없는 경우에는 행을 처리 중으로 둔다.
     *
     * @param stopping
     *            애플리케이션 종료가 시작됐는지. interrupt 표시는 라이브러리가 지울 수 있어 이 값도 함께 본다.
     */
    public void process(Claim claim, BooleanSupplier stopping) {
        // 사진과 주제를 함께 읽어 오므로 트랜잭션이 없어도 OpenAI를 호출하는 동안 DB 연결을 잡지 않는다.
        AiPromptVersion version = claim.version();
        AiReviewGenerationRequest request;
        try {
            Optional<Post> post = postRepository.findVisibleById(claim.postId());
            if (post.isEmpty()) {
                applyFailure(
                        claim,
                        FailureReason.POST_UNAVAILABLE.name(),
                        Invocation.NONE
                );
                return;
            }
            if (!version.isRunnableWith(aiReviewGenerator::supportsModel)) {
                applyFailure(
                        claim,
                        FailureReason.PROMPT_VERSION_UNAVAILABLE.name(),
                        Invocation.NONE
                );
                return;
            }
            request = requestFactory.create(version.toDraft(), post.get());
        } catch (RuntimeException exception) {
            abandon(
                    claim,
                    AbandonReason.PREPARE_FAILED,
                    exception
            );
            return;
        }

        AiReviewGeneration generation;
        try {
            generation = aiReviewGenerator.generate(request);
        } catch (AiReviewGenerationException exception) {
            handleGenerationFailure(
                    claim,
                    stopping,
                    exception.getFailureType().name(),
                    toInvocation(exception.findAttempt())
            );
            return;
        } catch (RuntimeException exception) {
            handleGenerationFailure(
                    claim,
                    stopping,
                    FailureReason.UNEXPECTED_ERROR.name(),
                    Invocation.NONE
            );
            return;
        }
        applyCompletion(claim, generation);
    }

    /**
     * 처리를 끝내지 못한 채 행을 처리 중으로 남긴다. 임대가 끝나면 다른 워커가 다시 가져간다.
     */
    public void abandon(Claim claim, AbandonReason reason) {
        abandon(
                claim,
                reason,
                null
        );
    }

    public void abandon(
            Claim claim,
            AbandonReason reason,
            RuntimeException exception
    ) {
        AiReviewWorkerLog.abandoned(
                claim,
                reason,
                exception
        );
    }

    private Claim claimOne() {
        Instant now = clock.instant();
        Optional<AiReview> found = reviewRepository.findClaimableForUpdate(now);
        while (found.isPresent()) {
            AiReview review = found.get();
            AiPromptVersion version = promptVersionRepository.findById(review.getPromptVersionId())
                    .orElseThrow();
            if (review.claim(
                    now,
                    now.plus(leaseDuration),
                    maxClaims
            )) {
                return toClaim(review, version);
            }
            AiReviewWorkerLog.finished(
                    review,
                    toClaim(review, version)
            );
            found = reviewRepository.findClaimableForUpdate(now);
        }
        return null;
    }

    private void handleGenerationFailure(
            Claim claim,
            BooleanSupplier stopping,
            String errorCode,
            Invocation invocation
    ) {
        // interrupt로 끊긴 호출은 일반 실패처럼 올라오므로 어떤 처리보다 먼저 확인한다.
        if (Thread.currentThread().isInterrupted() || stopping.getAsBoolean()) {
            abandon(claim, AbandonReason.SHUTDOWN);
            return;
        }
        applyFailure(
                claim,
                errorCode,
                invocation
        );
    }

    private void applyCompletion(Claim claim, AiReviewGeneration generation) {
        Invocation invocation = toInvocation(Optional.of(generation.attempt()));
        apply(claim, review -> review.complete(
                claim.attempt(),
                generation.result(),
                generation.rawOutput(),
                invocation,
                clock.instant()
        ));
    }

    private void applyFailure(
            Claim claim,
            String errorCode,
            Invocation invocation
    ) {
        apply(claim, review -> review.fail(
                claim.attempt(),
                errorCode,
                invocation,
                clock.instant()
        ));
    }

    private void apply(Claim claim, Predicate<AiReview> transition) {
        AiReview applied;
        try {
            applied = transactionTemplate.execute(status -> {
                AiReview review = reviewRepository.findByIdForUpdate(claim.reviewId())
                        .orElseThrow();
                return transition.test(review) ? review : null;
            });
        } catch (RuntimeException exception) {
            abandon(
                    claim,
                    AbandonReason.DB_ERROR,
                    exception
            );
            return;
        }
        if (applied == null) {
            AiReviewWorkerLog.staleUpdate(claim);
            return;
        }
        AiReviewWorkerLog.finished(
                applied,
                toClaim(applied, claim.version())
        );
    }

    private Invocation toInvocation(Optional<Attempt> found) {
        if (found.isEmpty()) {
            return Invocation.NONE;
        }
        Attempt attempt = found.get();
        Usage usage = attempt.usage();
        return new Invocation(
                usage.inputTokens(),
                usage.cachedInputTokens(),
                usage.outputTokens(),
                usage.reasoningTokens(),
                attempt.latency().toMillis(),
                attempt.responseId()
        );
    }

    private Claim toClaim(AiReview review, AiPromptVersion version) {
        return new Claim(
                review.getId(),
                review.getAttempt(),
                review.getPostId(),
                review.getUserId(),
                version
        );
    }

    /**
     * 워커가 가져간 행의 식별 정보와 요청 시점에 고정된 프롬프트 버전. 시도 번호는 반영할 때 늦은 워커를 가려내는 기준이다.
     */
    public record Claim(
            UUID reviewId,
            int attempt,
            UUID postId,
            UUID userId,
            AiPromptVersion version
    ) {
    }
}
