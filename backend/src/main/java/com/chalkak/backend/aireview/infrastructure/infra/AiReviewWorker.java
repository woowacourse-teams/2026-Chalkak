package com.chalkak.backend.aireview.infrastructure.infra;

import com.chalkak.backend.aireview.service.AbandonReason;
import com.chalkak.backend.aireview.service.AiReviewWorkerLog;
import com.chalkak.backend.aireview.service.AiReviewWorkerService;
import com.chalkak.backend.aireview.service.AiReviewWorkerService.Claim;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * 폴러 하나가 대기열에서 행을 하나씩 가져가 가상 스레드에 넘긴다. 처리 중인 건이 {@code concurrency}에 이르면 더 가져가지
 * 않으며, 이 상한은 서버 자원이 아니라 OpenAI의 분당 요청·토큰 한도를 지키기 위한 것이다.
 *
 * <p>
 * 빈이 소멸할 때 {@link #stop()}이 불린다. 새 건을 가져가지 않고 진행 중인 처리를 유예 시간까지 기다린 뒤
 * interrupt하며, 끝나지 못한 행은 처리 중으로 남아 임대가 끝난 뒤 다시 가져간다. 이 빈은 서비스·저장소를 거쳐 데이터 소스에
 * 의존하므로 데이터 소스보다 먼저 소멸한다.
 */
public class AiReviewWorker {

    private static final long POLL_DELAY_MILLIS = 1000;
    private static final Duration INTERRUPT_WAIT = Duration.ofSeconds(2);

    private final AiReviewWorkerService service;
    private final Semaphore permits;
    private final Duration shutdownGrace;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private volatile boolean stopping;

    public AiReviewWorker(
            AiReviewWorkerService service,
            int concurrency,
            Duration shutdownGrace
    ) {
        this.service = service;
        this.permits = new Semaphore(concurrency);
        this.shutdownGrace = shutdownGrace;
    }

    @Scheduled(fixedDelay = POLL_DELAY_MILLIS, scheduler = "aiReviewWorkerScheduler")
    public void poll() {
        try {
            while (!stopping && permits.tryAcquire()) {
                if (!submitNext()) {
                    return;
                }
            }
        } catch (RuntimeException exception) {
            AiReviewWorkerLog.pollFailed(exception);
        }
    }

    public void stop() {
        stopping = true;
        executor.shutdown();
        if (!await(shutdownGrace)) {
            executor.shutdownNow();
            await(INTERRUPT_WAIT);
        }
    }

    /**
     * 얻어 둔 자리 하나로 행을 가져가 실행기에 넘긴다. 넘기지 못하면 자리를 바로 돌려준다.
     *
     * @return 처리를 시작했으면 true, 더 진행할 수 없으면 false
     */
    private boolean submitNext() {
        Optional<Claim> claim;
        try {
            claim = service.claimNext();
        } catch (RuntimeException exception) {
            permits.release();
            throw exception;
        }
        if (claim.isEmpty()) {
            permits.release();
            return false;
        }
        try {
            executor.execute(() -> process(claim.get()));
        } catch (RejectedExecutionException exception) {
            permits.release();
            service.abandon(claim.get(), AbandonReason.SHUTDOWN);
            return false;
        }
        return true;
    }

    private void process(Claim claim) {
        try {
            service.process(claim, () -> stopping);
        } catch (RuntimeException exception) {
            service.abandon(
                    claim,
                    AbandonReason.UNEXPECTED_WHILE_PROCESSING,
                    exception
            );
        } finally {
            permits.release();
        }
    }

    private boolean await(Duration timeout) {
        try {
            return executor.awaitTermination(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
