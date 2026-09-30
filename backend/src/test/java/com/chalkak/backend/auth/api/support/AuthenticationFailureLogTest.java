package com.chalkak.backend.auth.api.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.chalkak.backend.auth.domain.AccessTokenScope;
import com.chalkak.backend.auth.infrastructure.infra.access.JwtAccessTokenProvider;
import com.chalkak.backend.exception.GlobalExceptionHandler;
import com.chalkak.backend.support.IntegrationTestSupport;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Security 필터가 끊은 401·403이 요청 추적용 로그를 남기는지 확인한다. 토큰은 로그에 나오면 안 된다.
 */
@AutoConfigureMockMvc
class AuthenticationFailureLogTest extends IntegrationTestSupport {

    private static final String LIKE_PATH = "/api/v1/posts/{postId}/likes";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtAccessTokenProvider accessTokenProvider;

    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private final ListAppender<ILoggingEvent> handlerAppender = new ListAppender<>();
    private final Logger handlerLogger =
            (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private final List<Logger> loggers = List.of(
            (Logger) LoggerFactory.getLogger(UnauthorizedEntryPoint.class),
            (Logger) LoggerFactory.getLogger(ForbiddenAccessDeniedHandler.class));

    @BeforeEach
    void attachAppender() {
        appender.start();
        handlerAppender.start();
        handlerLogger.addAppender(handlerAppender);
        loggers.forEach(logger -> logger.addAppender(appender));
    }

    @AfterEach
    void detachAppender() {
        loggers.forEach(logger -> logger.detachAppender(appender));
        appender.stop();
        handlerLogger.detachAppender(handlerAppender);
        handlerAppender.stop();
    }

    @Test
    @DisplayName("토큰 없이 보호 경로를 호출하면 401 WARN 1건에 예외 종류가 담긴다")
    void commence_withoutToken_logsWarnWithExceptionType() throws Exception {
        // When
        mockMvc.perform(put(LIKE_PATH, UUID.randomUUID()))
                .andExpect(status().isUnauthorized());

        // Then
        assertThat(appender.list).hasSize(1);
        ILoggingEvent event = appender.list.getFirst();
        assertThat(event.getLevel()).isEqualTo(Level.WARN);
        assertThat(keyValue(event, "type")).isEqualTo("error");
        assertThat(keyValue(event, "errorCode")).isEqualTo("UNAUTHORIZED");
        assertThat(keyValue(event, "status")).isEqualTo(401);
        assertThat(keyValue(event, "exception")).isEqualTo("InsufficientAuthenticationException");
        assertThat(event.getMDCPropertyMap()).containsKey("requestId");
    }

    @Test
    @DisplayName("잘못된 Bearer 토큰은 401 WARN 1건을 남기고 토큰 문자열은 로그 어디에도 없다")
    void commence_invalidBearerToken_logsWarnWithoutToken() throws Exception {
        // Given
        String token = "invalid.bearer.token-secret";

        // When
        mockMvc.perform(put(LIKE_PATH, UUID.randomUUID())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());

        // Then
        assertThat(appender.list).hasSize(1);
        ILoggingEvent event = appender.list.getFirst();
        assertThat(event.getLevel()).isEqualTo(Level.WARN);
        assertThat(keyValue(event, "type")).isEqualTo("error");
        assertThat(keyValue(event, "exception")).isEqualTo("InvalidBearerTokenException");
        assertThat(event.getFormattedMessage()).doesNotContain(token);
        assertThat(event.getKeyValuePairs())
                .noneMatch(pair -> String.valueOf(pair.value).contains(token));
        assertThat(event.getThrowableProxy()).isNull();
    }

    @Test
    @DisplayName("관리자 토큰으로 회원 API를 호출하면 403 WARN 1건이 남고 전역 예외 처리기는 로그를 남기지 않는다")
    void handle_adminTokenOnUserApi_logsWarn() throws Exception {
        // Given
        String token = accessTokenProvider.issue(UUID.randomUUID(), AccessTokenScope.ADMIN).value();

        // When
        mockMvc.perform(put(LIKE_PATH, UUID.randomUUID())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden());

        // Then
        assertThat(appender.list).hasSize(1);
        ILoggingEvent event = appender.list.getFirst();
        assertThat(event.getLevel()).isEqualTo(Level.WARN);
        assertThat(keyValue(event, "type")).isEqualTo("error");
        assertThat(keyValue(event, "errorCode")).isEqualTo("FORBIDDEN");
        assertThat(keyValue(event, "status")).isEqualTo(403);
        assertThat(keyValue(event, "exception")).isEqualTo("AuthorizationDeniedException");
        assertThat(event.getFormattedMessage()).doesNotContain(token);
        assertThat(handlerAppender.list).isEmpty();
    }

    private Object keyValue(ILoggingEvent event, String key) {
        return event.getKeyValuePairs().stream()
                .filter(pair -> pair.key.equals(key))
                .findFirst()
                .orElseThrow()
                .value;
    }
}
