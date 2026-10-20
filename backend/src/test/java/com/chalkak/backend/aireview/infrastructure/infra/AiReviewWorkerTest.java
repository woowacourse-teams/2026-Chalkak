package com.chalkak.backend.aireview.infrastructure.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.chalkak.backend.aireview.service.AiReviewWorkerService;
import com.chalkak.backend.aireview.service.AiReviewWorkerService.Claim;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;

class AiReviewWorkerTest {

    private static final Duration GRACE = Duration.ofMillis(200);

    private final AiReviewWorkerService service = mock(AiReviewWorkerService.class);
    private final Logger logger = (Logger) LoggerFactory.getLogger("chalkak.aireview");
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    private AiReviewWorker worker;

    @BeforeEach
    void setUp() {
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.INFO);
    }

    @AfterEach
    void tearDown() {
        if (worker != null) {
            worker.stop();
        }
        logger.detachAppender(appender);
        appender.stop();
    }

    @Test
    @DisplayName("동시에 처리하는 건은 설정한 수를 넘지 않고 끝나면 다음 건을 가져간다")
    void poll_manyClaimable_neverExceedsConcurrency() throws Exception {
        // Given
        worker = worker(2, GRACE);
        given(service.claimNext()).willAnswer(invocation -> Optional.of(claim()));
        CountDownLatch started = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger running = new AtomicInteger();
        AtomicInteger maxRunning = new AtomicInteger();
        doAnswer(invocation -> {
            maxRunning.accumulateAndGet(running.incrementAndGet(), Math::max);
            started.countDown();
            release.await(5, TimeUnit.SECONDS);
            running.decrementAndGet();
            return null;
        }).when(service).process(any(), any());

        // When
        worker.poll();

        // Then
        assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
        verify(service, times(2)).claimNext();
        assertThat(maxRunning.get()).isEqualTo(2);

        release.countDown();
        given(service.claimNext()).willReturn(Optional.empty());
        pollUntilClaimNextCalled(3);
        verify(service, times(3)).claimNext();
    }

    @Test
    @DisplayName("가져갈 행이 없으면 자리를 돌려주고 멈췄다가 다음 주기에 다시 가져간다")
    void poll_noClaimable_releasesPermitAndStops() {
        // Given
        worker = worker(1, GRACE);
        given(service.claimNext()).willReturn(Optional.empty());

        // When
        worker.poll();
        worker.poll();

        // Then
        verify(service, times(2)).claimNext();
        verify(service, never()).process(any(), any());
    }

    @Test
    @DisplayName("처리가 예외로 끝나도 자리를 돌려주고 다음 건을 가져간다")
    void poll_processThrows_releasesPermit() throws Exception {
        // Given
        worker = worker(1, GRACE);
        CountDownLatch processed = new CountDownLatch(1);
        given(service.claimNext()).willReturn(Optional.of(claim()), Optional.empty());
        doAnswer(invocation -> {
            processed.countDown();
            throw new IllegalStateException("boom");
        }).when(service).process(any(), any());

        // When
        worker.poll();
        assertThat(processed.await(5, TimeUnit.SECONDS)).isTrue();
        pollUntilClaimNextCalled(2);

        // Then
        verify(service, times(2)).claimNext();
        verify(service).abandon(
                any(),
                eq(AiReviewWorkerService.UNEXPECTED),
                any()
        );
    }

    @Test
    @DisplayName("가져오기가 실패해도 예외를 밖으로 던지지 않고 worker_poll_failed를 남긴 뒤 자리를 돌려준다")
    void poll_claimFails_logsAndReleasesPermit() {
        // Given
        worker = worker(1, GRACE);
        given(service.claimNext()).willThrow(new IllegalStateException("db down"))
                .willReturn(Optional.empty());

        // When
        worker.poll();
        worker.poll();

        // Then
        verify(service, times(2)).claimNext();
        assertThat(appender.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.ERROR);
            assertThat(event.getKeyValuePairs().toString())
                    .contains(
                            "type=\"ai_review\"",
                            "event=\"worker_poll_failed\"",
                            "errorCode=\"IllegalStateException\""
                    );
            assertThat(event.getFormattedMessage()).doesNotContain("db down");
        });
    }

    @Test
    @DisplayName("종료가 시작되면 새 건을 가져가지 않는다")
    void poll_afterStop_doesNotClaim() {
        // Given
        worker = worker(2, GRACE);
        worker.stop();

        // When
        worker.poll();

        // Then
        verify(service, never()).claimNext();
    }

    @Test
    @DisplayName("종료하면 진행 중인 처리에 종료 신호를 알리고, 유예 시간이 지나면 interrupt한다")
    void stop_inFlightProcess_signalsStoppingThenInterrupts() throws Exception {
        // Given
        worker = worker(1, GRACE);
        given(service.claimNext()).willReturn(Optional.of(claim()), Optional.empty());
        CountDownLatch started = new CountDownLatch(1);
        AtomicBoolean interrupted = new AtomicBoolean();
        doAnswer(invocation -> {
            started.countDown();
            try {
                new CountDownLatch(1).await();
            } catch (InterruptedException exception) {
                interrupted.set(true);
            }
            return null;
        }).when(service).process(any(), any());
        worker.poll();
        assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();

        // When
        worker.stop();

        // Then
        assertThat(interrupted).isTrue();
        @SuppressWarnings("unchecked")
        ArgumentCaptor<BooleanSupplier> stopping = ArgumentCaptor.forClass(BooleanSupplier.class);
        verify(service).process(any(), stopping.capture());
        assertThat(stopping.getValue().getAsBoolean()).isTrue();
    }

    @Test
    @DisplayName("진행 중인 처리가 유예 시간 안에 끝나면 interrupt하지 않는다")
    void stop_processFinishesWithinGrace_doesNotInterrupt() throws Exception {
        // Given
        worker = worker(1, Duration.ofSeconds(5));
        given(service.claimNext()).willReturn(Optional.of(claim()), Optional.empty());
        CountDownLatch started = new CountDownLatch(1);
        AtomicBoolean interrupted = new AtomicBoolean();
        AtomicBoolean finished = new AtomicBoolean();
        doAnswer(invocation -> {
            started.countDown();
            try {
                Thread.sleep(300);
                finished.set(true);
            } catch (InterruptedException exception) {
                interrupted.set(true);
            }
            return null;
        }).when(service).process(any(), any());
        worker.poll();
        assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();

        // When
        worker.stop();

        // Then
        assertThat(finished).isTrue();
        assertThat(interrupted).isFalse();
    }

    @Test
    @DisplayName("가져온 뒤 종료 경합으로 실행기가 거절하면 자리를 돌려주고 중단 처리를 알린다")
    void poll_executorRejects_releasesPermitAndLogsAbandoned() {
        // Given
        worker = worker(1, GRACE);
        Claim claim = claim();
        given(service.claimNext()).willAnswer(invocation -> {
            worker.stop();
            return Optional.of(claim);
        });

        // When
        worker.poll();

        // Then
        verify(service, never()).process(any(), any());
        verify(service).abandon(claim, AiReviewWorkerService.SHUTDOWN);
    }

    private AiReviewWorker worker(int concurrency, Duration grace) {
        return new AiReviewWorker(
                service,
                concurrency,
                grace
        );
    }

    private Claim claim() {
        return new Claim(
                UUID.randomUUID(),
                1,
                UUID.randomUUID(),
                UUID.randomUUID(),
                null
        );
    }

    // 자리가 돌아와야 다시 가져오기를 시도하므로, 호출 횟수가 늘 때까지 폴링해 반납을 행동으로 확인한다.
    private void pollUntilClaimNextCalled(int expected) throws InterruptedException {
        for (int waited = 0; waited < 500; waited++) {
            if (claimNextCalls() >= expected) {
                return;
            }
            worker.poll();
            Thread.sleep(10);
        }
        assertThat(claimNextCalls()).isGreaterThanOrEqualTo(expected);
    }

    private long claimNextCalls() {
        return mockingDetails(service).getInvocations().stream()
                .filter(invocation -> invocation.getMethod().getName().equals("claimNext"))
                .count();
    }
}
