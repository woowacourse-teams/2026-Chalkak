package com.chalkak.backend.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.chalkak.backend.auth.api.support.ForbiddenAccessDeniedHandler;
import com.chalkak.backend.auth.api.support.UnauthorizedEntryPoint;
import com.chalkak.backend.support.IntegrationTestSupport;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 콘솔 패턴에는 key-value와 MDC가 나오지 않으므로 로그 이벤트를 직접 받아 검증한다. 실패 로그가 요청 값을 새기지
 * 않는지를 함께 고정한다.
 */
@AutoConfigureMockMvc
class GlobalExceptionHandlerLogTest extends IntegrationTestSupport {

    private static final String SOCIAL_LOGIN_PATH = "/api/v1/auth/social-login";
    private static final String LIKE_PATH = "/api/v1/posts/{postId}/likes";

    @Autowired
    private MockMvc mockMvc;

    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private final List<Logger> loggers = List.of(
            (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class),
            (Logger) LoggerFactory.getLogger(UnauthorizedEntryPoint.class),
            (Logger) LoggerFactory.getLogger(ForbiddenAccessDeniedHandler.class));

    @BeforeEach
    void attachAppender() {
        appender.start();
        loggers.forEach(logger -> logger.addAppender(appender));
    }

    @AfterEach
    void detachAppender() {
        loggers.forEach(logger -> logger.detachAppender(appender));
        appender.stop();
    }

    @Test
    @DisplayName("비즈니스 4xx 응답은 errorCode, status, exception 종류가 담긴 WARN 1건을 requestId와 함께 남긴다")
    void handleHttpMessageNotReadableException_malformedJson_logsWarnWithoutMessage() throws Exception {
        // When
        mockMvc.perform(post(SOCIAL_LOGIN_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"malformed@chalkak.test\","))
                .andExpect(status().isBadRequest());

        // Then
        assertThat(appender.list).hasSize(1);
        ILoggingEvent event = appender.list.getFirst();
        assertThat(event.getLevel()).isEqualTo(Level.WARN);
        assertThat(keyValue(event, "type")).isEqualTo("error");
        assertThat(keyValue(event, "errorCode")).isEqualTo("BUSINESS_ERROR");
        assertThat(keyValue(event, "status")).isEqualTo(400);
        assertThat(keyValue(event, "exception")).isEqualTo("HttpMessageNotReadableException");
        assertThat(event.getThrowableProxy()).isNull();
        assertThat(event.getMDCPropertyMap()).containsKey("requestId");
    }

    @Test
    @DisplayName("도메인 예외는 errorCode, status, exception 종류만 담은 WARN 1건을 남기고 예외 메시지는 남기지 않는다")
    void handleBaseException_businessException_logsWarnWithoutMessage() throws Exception {
        // When
        mockMvc.perform(post(
                "/internal/v1/signature-processing/{uploadId}/upload-urls",
                "not-a-uuid"))
                .andExpect(status().isBadRequest());

        // Then
        assertThat(appender.list).hasSize(1);
        ILoggingEvent event = appender.list.getFirst();
        assertThat(event.getLevel()).isEqualTo(Level.WARN);
        assertThat(keyValue(event, "type")).isEqualTo("error");
        assertThat(keyValue(event, "errorCode")).isEqualTo("BUSINESS_ERROR");
        assertThat(keyValue(event, "status")).isEqualTo(400);
        assertThat(keyValue(event, "exception")).isEqualTo("BusinessException");
        assertThat(event.getFormattedMessage()).doesNotContain("ID 형식이 올바르지 않습니다.");
        assertThat(event.getKeyValuePairs())
                .noneMatch(pair -> String.valueOf(pair.value).contains("not-a-uuid"));
        assertThat(event.getThrowableProxy()).isNull();
    }

    @Test
    @DisplayName("Spring이 만든 4xx 예외(405)는 WARN 1건만 남기고 ERROR는 남기지 않는다")
    void handleUnexpectedException_methodNotAllowed_logsWarnOnly() throws Exception {
        // When
        mockMvc.perform(get(SOCIAL_LOGIN_PATH))
                .andExpect(status().isMethodNotAllowed());

        // Then
        assertThat(appender.list).hasSize(1);
        ILoggingEvent event = appender.list.getFirst();
        assertThat(event.getLevel()).isEqualTo(Level.WARN);
        assertThat(keyValue(event, "type")).isEqualTo("error");
        assertThat(keyValue(event, "errorCode")).isEqualTo("BUSINESS_ERROR");
        assertThat(keyValue(event, "status")).isEqualTo(405);
        assertThat(keyValue(event, "exception")).isEqualTo("HttpRequestMethodNotSupportedException");
        assertThat(event.getThrowableProxy()).isNull();
    }

    @Test
    @DisplayName("처리되지 않은 예외는 스택 트레이스가 담긴 ERROR를 requestId와 함께 남긴다")
    void handleUnexpectedException_unexpectedFailure_logsErrorWithThrowable() {
        // Given
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        IllegalStateException failure = new IllegalStateException("예상하지 못한 장애");
        MDC.put("requestId", "request-id-for-test");

        // When
        try {
            handler.handleUnexpectedException(failure);
        } finally {
            MDC.remove("requestId");
        }

        // Then
        assertThat(appender.list).hasSize(1);
        ILoggingEvent event = appender.list.getFirst();
        assertThat(event.getLevel()).isEqualTo(Level.ERROR);
        assertThat(keyValue(event, "type")).isEqualTo("error");
        assertThat(keyValue(event, "errorCode")).isEqualTo("INTERNAL_ERROR");
        assertThat(keyValue(event, "status")).isEqualTo(500);
        assertThat(event.getThrowableProxy().getMessage()).isEqualTo("예상하지 못한 장애");
        assertThat(event.getMDCPropertyMap()).containsEntry("requestId", "request-id-for-test");
    }

    @Test
    @DisplayName("존재하지 않는 경로 404는 경고나 오류 로그를 남기지 않는다")
    void handleApiNotFoundException_unknownPath_logsNothing() throws Exception {
        // When
        mockMvc.perform(get("/api/v1/auth/no-such-path"))
                .andExpect(status().isNotFound());

        // Then
        assertThat(appender.list).isEmpty();
    }

    @Test
    @DisplayName("Authorization, Cookie, 요청 본문과 파라미터 값이 있는 실패 요청도 로그에 그 값을 남기지 않는다")
    void handleHttpMessageNotReadableException_requestWithSensitiveValues_logsWithoutThem() throws Exception {
        // Given
        String email = "leak-check@chalkak.test";
        String token = "secret-bearer-token-value";
        String cookie = "secret-cookie-value";

        // When
        mockMvc.perform(post(SOCIAL_LOGIN_PATH)
                .queryParam("email", email)
                .cookie(new Cookie("session", cookie))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\","))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put(LIKE_PATH, UUID.randomUUID())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .cookie(new Cookie("session", cookie)))
                .andExpect(status().isUnauthorized());

        // Then
        assertThat(appender.list).hasSize(2);
        for (ILoggingEvent event : appender.list) {
            assertThat(event.getFormattedMessage()).doesNotContain(email, token, cookie);
            event.getKeyValuePairs().forEach(pair -> assertThat(String.valueOf(pair.value))
                    .doesNotContain(email, token, cookie));
            assertThat(event.getThrowableProxy()).isNull();
        }
    }

    private Object keyValue(ILoggingEvent event, String key) {
        return event.getKeyValuePairs().stream()
                .filter(pair -> pair.key.equals(key))
                .findFirst()
                .orElseThrow()
                .value;
    }
}
