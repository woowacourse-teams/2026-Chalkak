package com.chalkak.backend.aireview.service;

import com.chalkak.backend.aireview.domain.AiPromptVersion;
import com.chalkak.backend.aireview.domain.AiReview;
import com.chalkak.backend.aireview.domain.AiReview.FailureReason;
import com.chalkak.backend.aireview.domain.AiReview.Invocation;
import com.chalkak.backend.aireview.repository.AiPromptVersionRepository;
import com.chalkak.backend.aireview.repository.AiReviewRepository;
import com.chalkak.backend.aireview.service.AiReviewGeneration.Attempt;
import com.chalkak.backend.aireview.service.AiReviewGeneration.Usage;
import com.chalkak.backend.aireview.service.AiReviewRetryBackoff.RetryDelay;
import com.chalkak.backend.notification.service.NotificationService;
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
 *
 * <p>
 * 일시적인 생성 실패는 행을 대기로 되돌리고 백오프 뒤에 다시 가져간다. 임대 회수도 같은 회차의 가져간 횟수에 포함하므로 한 번의 사용자
 * 요청이 처리되는 시도는 상한을 넘지 않는다.
 */
public class AiReviewWorkerService {

    private final AiReviewRepository reviewRepository;
    private final AiPromptVersionRepository promptVersionRepository;
    private final PostRepository postRepository;
    private final AiReviewGenerator aiReviewGenerator;
    private final AiReviewGenerationRequestFactory requestFactory;
    private final NotificationService notificationService;
    private final Clock clock;
    private final Duration leaseDuration;
    private final int maxClaims;
    private final AiReviewRetryBackoff retryBackoff;
    private final TransactionTemplate transactionTemplate;

    public AiReviewWorkerService(
            AiReviewRepository reviewRepository,
            AiPromptVersionRepository promptVersionRepository,
            PostRepository postRepository,
            AiReviewGenerator aiReviewGenerator,
            AiReviewGenerationRequestFactory requestFactory,
            NotificationService notificationService,
            PlatformTransactionManager transactionManager,
            Clock clock,
            Duration leaseDuration,
            int maxClaims,
            AiReviewRetryBackoff retryBackoff
    ) {
        this.reviewRepository = reviewRepository;
        this.promptVersionRepository = promptVersionRepository;
        this.postRepository = postRepository;
        this.aiReviewGenerator = aiReviewGenerator;
        this.requestFactory = requestFactory;
        this.notificationService = notificationService;
        this.clock = clock;
        this.leaseDuration = leaseDuration;
        this.maxClaims = maxClaims;
        this.retryBackoff = retryBackoff;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate
                .setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * 가장 오래 기다린 처리 대상을 하나 가져간다. 가져간 횟수 상한을 넘은 행은 처리하지 않고 실패로 끝낸 뒤 다음 행을 찾는다. 이때 실패
     * 결과 알림 저장이 예외로 막히면 알림 없이 한 번 더 가져가, 같은 행이 대기열을 막지 않게 한다.
     *
     * <p>
     * 첫 시도가 어떤 이유로든 실패하면 그 폴링에서 소진으로 끝나는 행들의 실패 알림은 남기지 않는다. 소진 자체가 드물고 대기열 정지를 막는
     * 것이 우선이다. 다시 가져가기도 실패하면 예외를 그대로 던진다.
     */
    public Optional<Claim> claimNext() {
        try {
            return Optional.ofNullable(transactionTemplate.execute(status -> claimOne(true)));
        } catch (RuntimeException exception) {
            // 소진된 행의 결과 알림 저장이 막혀도 같은 행이 대기열 맨 앞에서 반복되지 않게, 알림 없이 한 번 더 가져간다.
            AiReviewWorkerLog.claimRetriedWithoutNotification(exception);
            return Optional.ofNullable(transactionTemplate.execute(status -> claimOne(false)));
        }
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
                        Failure.internal(FailureReason.POST_UNAVAILABLE.name())
                );
                return;
            }
            if (!version.isRunnableWith(aiReviewGenerator::supportsModel)) {
                applyFailure(
                        claim,
                        Failure.internal(FailureReason.PROMPT_VERSION_UNAVAILABLE.name())
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
                    new Failure(
                            exception.getFailureType().name(),
                            exception.getFailureType().isRetryable(),
                            toInvocation(exception.findAttempt()),
                            exception.findRetryAfter()
                    )
            );
            return;
        } catch (RuntimeException exception) {
            handleGenerationFailure(
                    claim,
                    stopping,
                    Failure.internal(FailureReason.UNEXPECTED_ERROR.name())
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

    private Claim claimOne(boolean notifyExhausted) {
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
            finishExhausted(
                    review,
                    toClaim(review, version),
                    notifyExhausted
            );
            found = reviewRepository.findClaimableForUpdate(now);
        }
        return null;
    }

    // 소진으로 끝난 행을 마무리한다. 알림을 만들거나, 만들지 않았다는 로그를 남기고 종료 로그를 남긴다.
    private void finishExhausted(
            AiReview review,
            Claim claim,
            boolean notify
    ) {
        if (notify) {
            notifyResult(review);
            AiReviewWorkerLog.finished(review, claim);
            return;
        }
        AiReviewWorkerLog.resultNotificationSkipped(claim);
        AiReviewWorkerLog.finished(review, claim);
    }

    private void handleGenerationFailure(
            Claim claim,
            BooleanSupplier stopping,
            Failure failure
    ) {
        // interrupt로 끊긴 호출은 일반 실패처럼 올라오므로 어떤 처리보다 먼저 확인한다.
        if (Thread.currentThread().isInterrupted() || stopping.getAsBoolean()) {
            abandon(claim, AbandonReason.SHUTDOWN);
            return;
        }
        applyFailure(
                claim,
                failure
        );
    }

    private void applyCompletion(Claim claim, AiReviewGeneration generation) {
        Invocation invocation = toInvocation(Optional.of(generation.attempt()));
        apply(
                claim,
                RetryDelay.NONE,
                review -> review.complete(
                        claim.attempt(),
                        generation.result(),
                        generation.rawOutput(),
                        invocation,
                        clock.instant()
                )
        );
    }

    private void applyFailure(Claim claim, Failure failure) {
        Instant now = clock.instant();
        RetryDelay retryDelay = retryBackoff.delayAfter(
                claim.roundAttempt(),
                failure.retryAfter()
        );
        apply(
                claim,
                retryDelay,
                review -> review.failOrRetry(
                        claim.attempt(),
                        failure.errorCode(),
                        failure.retryable(),
                        failure.invocation(),
                        now.plus(retryDelay.delay()),
                        maxClaims,
                        now
                )
        );
    }

    /**
     * @param retryDelay
     *            재시도 대기로 전환될 때 로그에 남길 대기 시간. 완료로 끝나는 전이에서는 쓰지 않는다.
     */
    private void apply(
            Claim claim,
            RetryDelay retryDelay,
            Predicate<AiReview> transition
    ) {
        AiReview applied;
        try {
            applied = transactionTemplate.execute(status -> {
                AiReview review = reviewRepository.findByIdForUpdate(claim.reviewId())
                        .orElseThrow();
                if (!transition.test(review)) {
                    return null;
                }
                notifyResult(review);
                return review;
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
        if (applied.isRetryScheduled()) {
            AiReviewWorkerLog.retryScheduled(
                    applied,
                    toClaim(applied, claim.version()),
                    retryDelay.delay(),
                    retryDelay.retryAfterUsed()
            );
            return;
        }
        AiReviewWorkerLog.finished(
                applied,
                toClaim(applied, claim.version())
        );
    }

    /**
     * 리뷰 상태를 바꾼 트랜잭션 안에서 결과 알림을 만든다. 재시도 대기처럼 끝나지 않았거나 알릴 필요 없는 상태는 건너뛴다.
     */
    private void notifyResult(AiReview review) {
        if (!review.isResultNotifiable()) {
            return;
        }
        notificationService.createForAiReview(
                review.getUserId(),
                review.getPostId(),
                review.getId(),
                review.getRequestCount(),
                review.isCompleted(),
                review.canRequestAgain(),
                review.getCompletedAt()
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
                review.getRoundAttempt(),
                review.getPostId(),
                review.getUserId(),
                version
        );
    }

    /**
     * 실패 한 건을 반영하는 데 필요한 값. 워커 자체 사유와 예상 못 한 예외는 재시도하지 않고 호출 결과도 없다.
     *
     * @param retryAfter
     *            외부 서비스가 알려 준 최소 대기 시간
     */
    private record Failure(
            String errorCode,
            boolean retryable,
            Invocation invocation,
            Optional<Duration> retryAfter
    ) {

        private static Failure internal(String errorCode) {
            return new Failure(
                    errorCode,
                    false,
                    Invocation.NONE,
                    Optional.empty()
            );
        }
    }

    /**
     * 워커가 가져간 행의 식별 정보와 요청 시점에 고정된 프롬프트 버전. 시도 번호는 반영할 때 늦은 워커를 가려내는 기준이고, 시도 번호가
     * 같으면 회차 시도 횟수도 같다.
     */
    public record Claim(
            UUID reviewId,
            int attempt,
            int roundAttempt,
            UUID postId,
            UUID userId,
            AiPromptVersion version
    ) {
    }
}
