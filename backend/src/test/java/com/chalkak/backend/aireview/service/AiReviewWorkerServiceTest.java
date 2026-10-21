package com.chalkak.backend.aireview.service;

import static com.chalkak.backend.aireview.service.AiReviewFixture.AUTHOR_ID;
import static com.chalkak.backend.aireview.service.AiReviewFixture.MODEL;
import static com.chalkak.backend.aireview.service.AiReviewFixture.ORIGINAL_STORAGE_KEY;
import static com.chalkak.backend.aireview.service.AiReviewFixture.POST_ID;
import static com.chalkak.backend.aireview.service.AiReviewFixture.TOPIC_TITLE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.chalkak.backend.aireview.domain.AiReview.FailureReason;
import com.chalkak.backend.aireview.domain.AiReviewResult;
import com.chalkak.backend.aireview.domain.PhotoReviewOutput;
import com.chalkak.backend.aireview.repository.AiPromptVersionRepository;
import com.chalkak.backend.aireview.repository.AiReviewRepository;
import com.chalkak.backend.aireview.service.AiReviewGeneration.Attempt;
import com.chalkak.backend.aireview.service.AiReviewGeneration.Usage;
import com.chalkak.backend.aireview.service.AiReviewGenerationException.FailureType;
import com.chalkak.backend.aireview.service.AiReviewWorkerService.Claim;
import com.chalkak.backend.photo.service.ImageUrlProvider;
import com.chalkak.backend.post.repository.PostRepository;
import com.chalkak.backend.support.IntegrationTestSupport;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.AdditionalAnswers;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

/**
 * 워커는 행을 잠그고 가져가는 동안만 트랜잭션을 쓰고 OpenAI를 호출하는 동안은 쓰지 않으므로 테스트
 * 클래스에 @Transactional을 쓰지 않고 커밋된 행을 직접 정리한다.
 */
class AiReviewWorkerServiceTest extends IntegrationTestSupport {

    private static final String COMPATIBLE_SCHEMA = "1";
    private static final Duration LEASE = Duration.ofSeconds(120);
    private static final int MAX_CLAIMS = 3;
    private static final Instant REQUESTED_AT = Instant.parse("2026-10-20T05:00:00Z");
    private static final Instant CLAIMED_AT = Instant.parse("2026-10-20T05:00:10Z");
    private static final String IMAGE_URL = "https://cdn.test/ai-review/original.webp";
    private static final String FEEDBACK = "구도가 좋습니다.";
    private static final String RAW_OUTPUT = """
            {"composition":7.46,"theme_expression":8,"feedback":"구도가 좋습니다."}""";

    @Autowired
    private AiReviewRepository reviewRepository;

    @Autowired
    private AiPromptVersionRepository promptVersionRepository;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private AiReviewGenerationRequestFactory requestFactory;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private ImageUrlProvider imageUrlProvider;

    private final MutableClock clock = new MutableClock(CLAIMED_AT);
    private final Logger logger = (Logger) LoggerFactory.getLogger("chalkak.aireview");
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    private AiReviewFixture fixture;
    private AiReviewWorkerService service;
    private UUID versionId;

    @BeforeEach
    void setUp() {
        fixture = new AiReviewFixture(jdbcTemplate);
        fixture.clean();
        fixture.insertPublicPost();
        versionId = fixture.insertPromptVersion(
                1,
                MODEL,
                COMPATIBLE_SCHEMA
        );
        given(imageUrlProvider.getUrl(ORIGINAL_STORAGE_KEY)).willReturn(IMAGE_URL);
        given(aiReviewGenerator.supportsModel(MODEL)).willReturn(true);
        service = createService(reviewRepository);
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.INFO);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
        appender.stop();
        Thread.interrupted();
        fixture.clean();
    }

    @Test
    @DisplayName("대기 행을 가져가면 처리 중으로 바꾸고 시도 번호·임대 만료·시작 시각을 저장한다")
    void claimNext_queuedReview_claimsAndPersistsLease() {
        // Given
        UUID reviewId = fixture.insertQueuedReview(versionId, REQUESTED_AT);

        // When
        Claim claim = service.claimNext().orElseThrow();

        // Then
        assertThat(claim.reviewId()).isEqualTo(reviewId);
        assertThat(claim.attempt()).isEqualTo(1);
        assertThat(claim.version().getId()).isEqualTo(versionId);
        Map<String, Object> row = findRow(reviewId);
        assertThat(row).containsEntry("status", "PROCESSING").containsEntry("attempt", 1);
        assertThat(instant(row, "lease_expires_at")).isEqualTo(CLAIMED_AT.plus(LEASE));
        assertThat(instant(row, "started_at")).isEqualTo(CLAIMED_AT);
    }

    @Test
    @DisplayName("가져갈 수 있는 행이 둘이면 오래 기다린 요청부터 가져가고 없으면 비어 있다")
    void claimNext_twoQueuedReviews_claimsOldestFirst() {
        // Given
        fixture.insertSecondPublicPost();
        UUID newer = fixture.insertSecondQueuedReview(versionId, REQUESTED_AT.plusSeconds(5));
        UUID older = fixture.insertQueuedReview(versionId, REQUESTED_AT);

        // When & Then
        assertThat(service.claimNext()).map(Claim::reviewId).contains(older);
        assertThat(service.claimNext()).map(Claim::reviewId).contains(newer);
        assertThat(service.claimNext()).isEmpty();
    }

    @Test
    @DisplayName("임대가 남은 처리 중 행은 가져가지 않고, 끝나면 시도 번호를 늘려 다시 가져간다")
    void claimNext_processingReview_reclaimsOnlyAfterLeaseExpires() {
        // Given
        Instant leaseEnd = CLAIMED_AT.plus(LEASE);
        UUID reviewId = fixture.insertQueuedReview(versionId, REQUESTED_AT);
        service.claimNext().orElseThrow();

        // When & Then
        clock.set(leaseEnd);
        assertThat(service.claimNext()).isEmpty();

        clock.set(leaseEnd.plusSeconds(1));
        Claim reclaimed = service.claimNext().orElseThrow();
        assertThat(reclaimed.reviewId()).isEqualTo(reviewId);
        assertThat(reclaimed.attempt()).isEqualTo(2);
        assertThat(instant(findRow(reviewId), "started_at")).isEqualTo(CLAIMED_AT);
    }

    @Test
    @DisplayName("다른 트랜잭션이 잠근 행은 기다리지 않고 건너뛰어 같은 행을 가져가지 않는다")
    void claimNext_lockedReview_skipsToNextRow() throws Exception {
        // Given
        fixture.insertSecondPublicPost();
        UUID locked = fixture.insertQueuedReview(versionId, REQUESTED_AT);
        UUID other = fixture.insertSecondQueuedReview(versionId, REQUESTED_AT.plusSeconds(5));
        ExecutorService holder = Executors.newSingleThreadExecutor();
        CountDownLatch lockHeld = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Future<?> holding = holder.submit(() -> new TransactionTemplate(transactionManager)
                .executeWithoutResult(status -> {
                    reviewRepository.findClaimableForUpdate(CLAIMED_AT).orElseThrow();
                    lockHeld.countDown();
                    awaitUninterruptibly(release);
                }));
        assertThat(lockHeld.await(5, TimeUnit.SECONDS)).isTrue();

        try {
            // When
            Optional<Claim> claim = CompletableFuture.supplyAsync(service::claimNext).get(5,
                    TimeUnit.SECONDS);

            // Then
            assertThat(claim).map(Claim::reviewId).contains(other);
            assertThat(findRow(locked)).containsEntry("status", "QUEUED");
        } finally {
            release.countDown();
            holding.get(5, TimeUnit.SECONDS);
            holder.shutdown();
        }
    }

    @Test
    @DisplayName("행 둘을 두 스레드가 동시에 가져가면 서로 다른 행을 가져간다")
    void claimNext_concurrentClaimersTwoRows_claimDistinctRows() throws Exception {
        // Given
        fixture.insertSecondPublicPost();
        fixture.insertQueuedReview(versionId, REQUESTED_AT);
        fixture.insertSecondQueuedReview(versionId, REQUESTED_AT.plusSeconds(5));

        // When
        List<Optional<Claim>> results = claimConcurrently(2);

        // Then
        assertThat(results).allMatch(Optional::isPresent);
        assertThat(results.stream().map(result -> result.orElseThrow().reviewId()).distinct())
                .hasSize(2);
    }

    @Test
    @DisplayName("가져간 횟수 상한을 넘은 회수 행은 호출하지 않고 LEASE_EXHAUSTED로 끝낸 뒤 다음 행을 가져간다")
    void claimNext_overMaxClaims_failsExhaustedAndClaimsNext() {
        // Given
        fixture.insertSecondPublicPost();
        UUID exhausted = fixture.insertProcessingReview(
                versionId,
                MAX_CLAIMS,
                CLAIMED_AT.minusSeconds(1)
        );
        UUID next = fixture.insertSecondQueuedReview(versionId, REQUESTED_AT.plusSeconds(5));

        // When
        Optional<Claim> claim = service.claimNext();

        // Then
        assertThat(claim).map(Claim::reviewId).contains(next);
        assertThat(findRow(exhausted)).containsEntry("status", "FAILED")
                .containsEntry("error_code", FailureReason.LEASE_EXHAUSTED.name());
        verify(aiReviewGenerator, never()).generate(any());
        assertThat(singleEventKeyValues("review_failed")).contains(
                "aiReviewId=\"" + exhausted + "\"",
                "errorCode=\"LEASE_EXHAUSTED\"",
                "attempt=\"" + MAX_CLAIMS + "\""
        );
    }

    @Test
    @DisplayName("성공하면 결과·원출력·사용량을 저장해 완료로 끝내고, 완료 로그에는 피드백·프롬프트·주소·주제를 남기지 않는다")
    void process_success_storesResultAndLogsCompletedWithoutSensitiveText() {
        // Given
        given(aiReviewGenerator.generate(any())).willAnswer(invocation -> {
            clock.advance(Duration.ofSeconds(21));
            return generation();
        });

        // When
        UUID reviewId = processQueuedReview();

        // Then
        Map<String, Object> row = findRow(reviewId);
        assertThat(row).containsEntry("status", "COMPLETED")
                .containsEntry("input_tokens", 1200)
                .containsEntry("cached_input_tokens", 0)
                .containsEntry("output_tokens", 300)
                .containsEntry("reasoning_tokens", 64)
                .containsEntry("latency_ms", 21_000L)
                .containsEntry("openai_response_id", "resp_abc");
        assertThat(instant(row, "completed_at")).isEqualTo(CLAIMED_AT.plusSeconds(21));
        assertThat(objectMapper.readTree(row.get("result").toString())).isEqualTo(
                objectMapper.readTree("""
                        {"overallScore":7.5,
                         "criteria":[{"key":"COMPOSITION","label":"구도","score":7.5}],
                         "distinctivePerspective":true,
                         "feedback":"구도가 좋습니다."}"""));
        assertThat(objectMapper.readTree(row.get("output").toString())).isEqualTo(
                objectMapper.readTree(RAW_OUTPUT));
        ILoggingEvent event = singleEvent("review_completed");
        String keyValues = event.getKeyValuePairs().toString();
        assertThat(keyValues).contains(
                "type=\"ai_review\"",
                "aiReviewId=\"" + reviewId + "\"",
                "postId=\"" + POST_ID + "\"",
                "userId=\"" + AUTHOR_ID + "\"",
                "promptVersion=\"1\"",
                "model=\"" + MODEL + "\"",
                "attempt=\"1\"",
                "queueWaitMs=\"10000\"",
                "latencyMs=\"21000\"",
                "totalMs=\"31000\"",
                "inputTokens=\"1200\"",
                "cachedInputTokens=\"0\"",
                "outputTokens=\"300\"",
                "reasoningTokens=\"64\""
        );
        assertThat(keyValues).doesNotContain("errorCode");
        assertThat(keyValues + event.getFormattedMessage())
                .doesNotContain(
                        FEEDBACK,
                        "시스템",
                        "노을",
                        "original.webp",
                        IMAGE_URL
                );
    }

    @Test
    @DisplayName("요청 뒤에 새 버전이 만들어져도 요청 시점에 고정한 버전의 초안과 원본 사진 주소·주제 제목으로 호출한다")
    void process_newerVersionCreated_usesPinnedVersion() {
        // Given
        fixture.insertPromptVersion(
                2,
                "gpt-6-luna",
                COMPATIBLE_SCHEMA
        );
        given(aiReviewGenerator.supportsModel("gpt-6-luna")).willReturn(true);
        given(aiReviewGenerator.generate(any())).willReturn(generation());

        // When
        processQueuedReview();

        // Then
        ArgumentCaptor<AiReviewGenerationRequest> captor = ArgumentCaptor
                .forClass(AiReviewGenerationRequest.class);
        verify(aiReviewGenerator).generate(captor.capture());
        assertThat(captor.getValue().draft())
                .isEqualTo(promptVersionRepository.findById(versionId).orElseThrow().toDraft());
        assertThat(captor.getValue().draft().model()).isEqualTo(MODEL);
        assertThat(captor.getValue().imageUrl()).isEqualTo(IMAGE_URL);
        assertThat(captor.getValue().topicTitle()).isEqualTo(TOPIC_TITLE);
    }

    @Test
    @DisplayName("응답을 받은 뒤 생성이 실패하면 실패 유형을 오류 코드로 저장하고 받은 사용량을 남긴다")
    void process_generationFailureWithAttempt_failsWithFailureTypeAndUsage() {
        // Given
        given(aiReviewGenerator.generate(any())).willThrow(new AiReviewGenerationException(
                FailureType.INVALID_OUTPUT, attempt()));

        // When
        UUID reviewId = processQueuedReview();

        // Then
        assertThat(findRow(reviewId)).containsEntry("status", "FAILED")
                .containsEntry("error_code", "INVALID_OUTPUT")
                .containsEntry("input_tokens", 1200)
                .containsEntry("openai_response_id", "resp_abc");
    }

    @Test
    @DisplayName("응답을 받지 못한 실패 로그는 오류 코드와 대기 시간을 남기고 값이 없는 사용량은 남기지 않는다")
    void process_generationFailureWithoutAttempt_logsFailedWithOnlyPresentFields() {
        // Given
        given(aiReviewGenerator.generate(any()))
                .willThrow(new AiReviewGenerationException(FailureType.TIMEOUT));

        // When
        UUID reviewId = processQueuedReview();

        // Then
        assertThat(findRow(reviewId)).containsEntry("status", "FAILED")
                .containsEntry("error_code", "TIMEOUT");
        String keyValues = singleEventKeyValues("review_failed");
        assertThat(keyValues).contains(
                "errorCode=\"TIMEOUT\"",
                "queueWaitMs=\"10000\"",
                "totalMs=\"10000\""
        );
        assertThat(keyValues).doesNotContain(
                "inputTokens",
                "latencyMs",
                "outputTokens"
        );
    }

    @Test
    @DisplayName("생성기가 예상 못 한 예외를 던지면 UNEXPECTED_ERROR로 실패시킨다")
    void process_unexpectedGeneratorException_failsWithUnexpectedError() {
        // Given
        given(aiReviewGenerator.generate(any())).willThrow(new IllegalStateException("boom"));

        // When
        UUID reviewId = processQueuedReview();

        // Then
        assertThat(findRow(reviewId)).containsEntry("status", "FAILED")
                .containsEntry("error_code", FailureReason.UNEXPECTED_ERROR.name());
    }

    @Test
    @DisplayName("호출 전에 게시물이 공개 조건을 잃었으면 호출하지 않고 POST_UNAVAILABLE로 실패시킨다")
    void process_postNoLongerVisible_failsWithoutCalling() {
        // Given
        UUID reviewId = fixture.insertQueuedReview(versionId, REQUESTED_AT);
        Claim claim = service.claimNext().orElseThrow();
        jdbcTemplate.update("UPDATE posts SET deleted_at = CURRENT_TIMESTAMP");

        // When
        service.process(claim, () -> false);

        // Then
        assertThat(findRow(reviewId)).containsEntry("status", "FAILED")
                .containsEntry("error_code", FailureReason.POST_UNAVAILABLE.name());
        verify(aiReviewGenerator, never()).generate(any());
    }

    @ParameterizedTest
    @CsvSource({
            "0, true",
            "1, false"
    })
    @DisplayName("고정한 버전의 출력 스키마가 다르거나 모델이 더는 허용되지 않으면 호출하지 않고 PROMPT_VERSION_UNAVAILABLE로 실패시킨다")
    void process_versionNoLongerRunnable_failsWithoutCalling(
            String outputSchemaVersion,
            boolean modelSupported
    ) {
        // Given
        UUID pinned = fixture.insertPromptVersion(
                2,
                MODEL,
                outputSchemaVersion
        );
        UUID reviewId = fixture.insertQueuedReview(pinned, REQUESTED_AT);
        given(aiReviewGenerator.supportsModel(MODEL)).willReturn(modelSupported);

        // When
        service.process(service.claimNext().orElseThrow(), () -> false);

        // Then
        assertThat(findRow(reviewId)).containsEntry("status", "FAILED")
                .containsEntry("error_code", FailureReason.PROMPT_VERSION_UNAVAILABLE.name());
        verify(aiReviewGenerator, never()).generate(any());
    }

    @Test
    @DisplayName("임대가 끝나 다른 워커가 다시 가져간 뒤 늦게 끝난 워커의 결과는 반영하지 않고 로그만 남긴다")
    void process_staleAttempt_ignoresResultAndLogsStaleUpdate() {
        // Given
        UUID reviewId = fixture.insertQueuedReview(versionId, REQUESTED_AT);
        given(aiReviewGenerator.generate(any())).willReturn(generation());
        Claim first = service.claimNext().orElseThrow();
        clock.set(CLAIMED_AT.plus(LEASE).plusSeconds(1));
        Claim second = service.claimNext().orElseThrow();

        // When
        service.process(first, () -> false);

        // Then
        assertThat(findRow(reviewId)).containsEntry("status", "PROCESSING");
        assertThat(singleEventKeyValues("review_stale_update"))
                .contains("aiReviewId=\"" + reviewId + "\"", "attempt=\"1\"");

        service.process(second, () -> false);
        assertThat(findRow(reviewId)).containsEntry("status", "COMPLETED");
        // 재회수돼도 대기 시간은 요청부터 처음 가져간 때까지다.
        assertThat(singleEventKeyValues("review_completed")).contains("queueWaitMs=\"10000\"");
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("스레드가 interrupt됐거나 종료 중이면 생성이 실패해도 실패를 쓰지 않고 처리 중으로 남긴다")
    void process_interruptedOrStoppingGenerationFailure_leavesProcessing(boolean interrupted) {
        // Given
        UUID reviewId = fixture.insertQueuedReview(versionId, REQUESTED_AT);
        given(aiReviewGenerator.generate(any())).willAnswer(invocation -> {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
            throw new AiReviewGenerationException(FailureType.TIMEOUT);
        });
        Claim claim = service.claimNext().orElseThrow();

        // When
        try {
            service.process(claim, () -> !interrupted);
        } finally {
            Thread.interrupted();
        }

        // Then
        assertThat(findRow(reviewId)).containsEntry("status", "PROCESSING")
                .containsEntry("attempt", 1);
        assertThat(singleEventKeyValues("review_abandoned"))
                .contains("aiReviewId=\"" + reviewId + "\"");
    }

    @Test
    @DisplayName("결과를 저장하는 DB 단계가 실패하면 실패로 쓰지 않고 처리 중으로 남겨 임대 회수에 맡긴다")
    void process_databaseFailureOnApply_leavesProcessing() {
        // Given
        UUID reviewId = fixture.insertQueuedReview(versionId, REQUESTED_AT);
        given(aiReviewGenerator.generate(any())).willReturn(generation());
        AiReviewRepository failing = mock(AiReviewRepository.class,
                AdditionalAnswers.delegatesTo(reviewRepository));
        doThrow(new DataAccessResourceFailureException("connection lost"))
                .when(failing).findByIdForUpdate(eq(reviewId));
        AiReviewWorkerService failingService = createService(failing);
        Claim claim = failingService.claimNext().orElseThrow();

        // When
        failingService.process(claim, () -> false);

        // Then
        assertThat(findRow(reviewId)).containsEntry("status", "PROCESSING");
        assertThat(singleEventKeyValues("review_abandoned"))
                .contains("aiReviewId=\"" + reviewId + "\"", "reason=\"DB_ERROR\"");
    }

    @Test
    @DisplayName("호출 전 확인 단계의 실패도 실패로 쓰지 않고 호출하지 않는다")
    void process_databaseFailureOnPrepare_leavesProcessingWithoutCalling() {
        // Given
        UUID reviewId = fixture.insertQueuedReview(versionId, REQUESTED_AT);
        PostRepository failingPosts = mock(PostRepository.class);
        given(failingPosts.findVisibleById(any()))
                .willThrow(new DataAccessResourceFailureException("connection lost"));
        AiReviewWorkerService failingService = new AiReviewWorkerService(
                reviewRepository,
                promptVersionRepository,
                failingPosts,
                aiReviewGenerator,
                requestFactory,
                transactionManager,
                clock,
                LEASE,
                MAX_CLAIMS
        );
        Claim claim = failingService.claimNext().orElseThrow();

        // When
        failingService.process(claim, () -> false);

        // Then
        assertThat(findRow(reviewId)).containsEntry("status", "PROCESSING");
        verify(aiReviewGenerator, never()).generate(any());
        assertThat(singleEventKeyValues("review_abandoned"))
                .contains("reason=\"PREPARE_FAILED\"");
    }

    @Test
    @DisplayName("처리 중 예상 못 한 예외로 중단하면 reason과 예외 이름을 남기고 행은 그대로 둔다")
    void abandon_unexpectedWhileProcessing_logsReasonAndErrorCode() {
        // Given
        UUID reviewId = fixture.insertQueuedReview(versionId, REQUESTED_AT);
        Claim claim = service.claimNext().orElseThrow();

        // When
        service.abandon(
                claim,
                AbandonReason.UNEXPECTED_WHILE_PROCESSING,
                new IllegalStateException("x")
        );

        // Then
        assertThat(findRow(reviewId)).containsEntry("status", "PROCESSING");
        assertThat(singleEventKeyValues("review_abandoned"))
                .contains(
                        "reason=\"UNEXPECTED_WHILE_PROCESSING\"",
                        "errorCode=\"IllegalStateException\""
                );
    }

    private UUID processQueuedReview() {
        UUID reviewId = fixture.insertQueuedReview(versionId, REQUESTED_AT);
        service.process(service.claimNext().orElseThrow(), () -> false);
        return reviewId;
    }

    private AiReviewWorkerService createService(AiReviewRepository repository) {
        return new AiReviewWorkerService(
                repository,
                promptVersionRepository,
                postRepository,
                aiReviewGenerator,
                requestFactory,
                transactionManager,
                clock,
                LEASE,
                MAX_CLAIMS
        );
    }

    private List<Optional<Claim>> claimConcurrently(int claimers) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(claimers);
        CyclicBarrier barrier = new CyclicBarrier(claimers);
        try {
            List<Future<Optional<Claim>>> futures = IntStream.range(0, claimers)
                    .mapToObj(index -> executor.submit((Callable<Optional<Claim>>) () -> {
                        barrier.await(5, TimeUnit.SECONDS);
                        return service.claimNext();
                    }))
                    .toList();
            List<Optional<Claim>> results = new ArrayList<>();
            for (Future<Optional<Claim>> future : futures) {
                results.add(future.get(10, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            executor.shutdownNow();
        }
    }

    private AiReviewGeneration generation() {
        PhotoReviewOutput output = new PhotoReviewOutput(
                7.46,
                8.0,
                FEEDBACK
        );
        AiReviewResult result = output.toResult(7.0);
        return new AiReviewGeneration(
                result,
                output,
                RAW_OUTPUT,
                attempt()
        );
    }

    private Attempt attempt() {
        return new Attempt(
                new Usage(
                        1200,
                        0,
                        300,
                        64
                ),
                Duration.ofMillis(21_000),
                "resp_abc"
        );
    }

    private Map<String, Object> findRow(UUID reviewId) {
        return jdbcTemplate.queryForMap("SELECT * FROM ai_reviews WHERE id = ?", reviewId);
    }

    private Instant instant(Map<String, Object> row, String column) {
        return ((Timestamp) row.get(column)).toInstant();
    }

    private ILoggingEvent singleEvent(String event) {
        List<ILoggingEvent> events = appender.list.stream()
                .filter(logged -> logged.getKeyValuePairs().toString()
                        .contains("event=\"" + event + "\""))
                .toList();
        assertThat(events).hasSize(1);
        return events.get(0);
    }

    private String singleEventKeyValues(String event) {
        return singleEvent(event).getKeyValuePairs().toString();
    }

    private void awaitUninterruptibly(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private static final class MutableClock extends Clock {

        private final AtomicReference<Instant> now;

        private MutableClock(Instant initial) {
            this.now = new AtomicReference<>(initial);
        }

        private void set(Instant instant) {
            now.set(instant);
        }

        private void advance(Duration duration) {
            now.updateAndGet(current -> current.plus(duration));
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now.get();
        }
    }
}
