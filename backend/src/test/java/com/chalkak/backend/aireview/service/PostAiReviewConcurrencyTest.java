package com.chalkak.backend.aireview.service;

import static com.chalkak.backend.aireview.service.AiReviewFixture.AUTHOR_ID;
import static com.chalkak.backend.aireview.service.AiReviewFixture.MODEL;
import static com.chalkak.backend.aireview.service.AiReviewFixture.POST_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.chalkak.backend.aireview.domain.AiReview.ApiStatus;
import com.chalkak.backend.aireview.service.PostAiReviewService.ReviewResult;
import com.chalkak.backend.common.logging.LogFields;
import com.chalkak.backend.support.IntegrationTestSupport;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class PostAiReviewConcurrencyTest extends IntegrationTestSupport {

    private static final int THREADS = 5;

    @Autowired
    private PostAiReviewService service;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final Logger logger = (Logger) LoggerFactory.getLogger("chalkak.aireview");
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    private AiReviewFixture fixture;

    @BeforeEach
    void setUp() {
        fixture = new AiReviewFixture(jdbcTemplate);
        fixture.clean();
        fixture.insertPublicPost();
        fixture.insertPromptVersion(1, MODEL, "1");
        given(aiReviewGenerator.supportsModel(MODEL)).willReturn(true);
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.INFO);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
        fixture.clean();
    }

    @Test
    @DisplayName("같은 게시물에 동시에 요청해도 행은 하나이고 모두 같은 리뷰를 응답받는다")
    void request_concurrent_createsSingleRow() throws Exception {
        // Given
        CountDownLatch ready = new CountDownLatch(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<ReviewResult>> futures = new ArrayList<>();

        // When
        try (ExecutorService executor = Executors.newFixedThreadPool(THREADS)) {
            for (int index = 0; index < THREADS; index++) {
                futures.add(executor.submit(requestAfterStart(ready, start)));
            }
            ready.await(10, TimeUnit.SECONDS);
            start.countDown();
            List<UUID> reviewIds = new ArrayList<>();
            for (Future<ReviewResult> future : futures) {
                ReviewResult result = future.get(20, TimeUnit.SECONDS);
                assertThat(result.status()).isEqualTo(ApiStatus.PROCESSING);
                reviewIds.add(result.id());
            }

            // Then
            assertThat(reviewIds).hasSize(THREADS).doesNotContainNull()
                    .containsOnly(reviewIds.get(0));
        }
        assertThat(fixture.countReviews()).isEqualTo(1);
        assertThat(appender.list).singleElement().satisfies(event ->
                assertThat(event.getKeyValuePairs().toString())
                        .contains("event=\"review_requested\""));
    }

    private Callable<ReviewResult> requestAfterStart(CountDownLatch ready, CountDownLatch start) {
        return () -> {
            MDC.put(LogFields.REQUEST_ID, UUID.randomUUID().toString());
            try {
                ready.countDown();
                start.await(10, TimeUnit.SECONDS);
                return service.request(AUTHOR_ID, POST_ID);
            } finally {
                MDC.remove(LogFields.REQUEST_ID);
            }
        };
    }
}
