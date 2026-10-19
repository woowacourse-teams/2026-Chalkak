package com.chalkak.backend.common.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerMapping;

class RequestIdFilterTest {

    private final RequestIdFilter filter = new RequestIdFilter();
    private final Logger accessLogger = (Logger) LoggerFactory.getLogger("chalkak.access");
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    @BeforeEach
    void attachAppender() {
        appender.start();
        accessLogger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        accessLogger.detachAppender(appender);
        appender.stop();
    }

    @Test
    @DisplayName("체인이 예외를 던지면 status 500으로 접근 로그를 남기고 예외를 다시 던지며 MDC를 정리한다")
    void doFilter_chainThrows_logsStatus500AndRethrowsAndClearsMdc() {
        // Given
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/posts");
        request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/api/v1/posts");
        request.setAttribute(LogFields.FEED_QUERY_REQUEST_ATTRIBUTE,
                new FeedQueryLog("recent", 1, 20));
        MockHttpServletResponse response = new MockHttpServletResponse();
        IllegalStateException failure = new IllegalStateException("예상하지 못한 장애");
        FilterChain failingChain = (req, res) -> {
            throw failure;
        };

        // When & Then
        assertThatThrownBy(() -> filter.doFilter(request, response, failingChain))
                .isSameAs(failure);
        assertThat(appender.list).hasSize(1);
        ILoggingEvent event = appender.list.getFirst();
        assertThat(event.getKeyValuePairs())
                .anySatisfy(pair -> {
                    assertThat(pair.key).isEqualTo("status");
                    assertThat(pair.value).isEqualTo(500);
                });
        assertThat(event.getMDCPropertyMap()).containsKey("requestId");
        assertThat(response.getHeader("X-Request-Id")).isEqualTo(
                event.getMDCPropertyMap().get("requestId"));
        assertThat(MDC.get("requestId")).isNull();
        assertThat(event.getKeyValuePairs()).noneSatisfy(
                pair -> assertThat(pair.key).isIn("sort", "page", "pageSize", "postIds"));
    }

    @ParameterizedTest
    @CsvSource({"GET, /api/v1/topics, 200", "POST, /api/v1/posts, 201", "GET, /api/v1/posts, 500"})
    @DisplayName("정상 피드가 아닌 요청은 준비된 피드 메타데이터도 기록하지 않는다")
    void doFilter_nonSuccessfulFeed_doesNotLogFeedMetadata(
            String method,
            String path,
            int status
    ) throws Exception {
        // Given
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, path);
        request.setAttribute(LogFields.FEED_QUERY_REQUEST_ATTRIBUTE,
                new FeedQueryLog("recent", 1, 20));
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> response.setStatus(status);

        // When
        filter.doFilter(request, response, chain);

        // Then
        assertThat(appender.list).hasSize(1);
        for (org.slf4j.event.KeyValuePair pair : appender.list.getFirst().getKeyValuePairs()) {
            assertThat(pair.key).isNotIn("sort", "page", "pageSize", "postIds");
        }
    }

    @Test
    @DisplayName("actuator와 이름만 비슷한 경로는 접근 로그를 남긴다")
    void doFilter_pathLooksLikeActuator_logsAccess() throws Exception {
        // Given
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuatorx");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> {
        };

        // When
        filter.doFilter(request, response, chain);

        // Then
        assertThat(appender.list).hasSize(1);
    }

    @Test
    @DisplayName("정확히 /actuator 경로는 접근 로그를 남기지 않는다")
    void doFilter_exactActuatorPath_logsNothing() throws Exception {
        // Given
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> {
        };

        // When
        filter.doFilter(request, response, chain);

        // Then
        assertThat(appender.list).isEmpty();
    }
}
