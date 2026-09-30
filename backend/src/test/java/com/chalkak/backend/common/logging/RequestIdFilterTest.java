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
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

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
