package com.chalkak.backend.common.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.chalkak.backend.auth.domain.token.AccessTokenScope;
import com.chalkak.backend.auth.infrastructure.infra.token.access.JwtAccessTokenProvider;
import com.chalkak.backend.support.IntegrationTestSupport;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

/**
 * 콘솔 패턴에는 key-value와 MDC가 나오지 않으므로 출력을 캡처하지 않고 로그 이벤트를 직접 받아 검증한다.
 */
@AutoConfigureMockMvc
@Transactional
class AccessLogTest extends IntegrationTestSupport {

    private static final String REQUEST_ID_HEADER = "X-Request-Id";
    private static final String LIKE_PATH = "/api/v1/posts/{postId}/likes";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtAccessTokenProvider accessTokenProvider;

    @Autowired
    private JdbcTemplate jdbcTemplate;

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
    @DisplayName("handler가 매칭된 요청은 UUID 형식 X-Request-Id 헤더와 route 템플릿이 담긴 접근 로그 1건을 남긴다")
    void accessLog_matchedRoute_logsTemplateWithRequestId() throws Exception {
        // When
        MvcResult result = mockMvc.perform(post(
                "/internal/v1/signature-processing/{uploadId}/upload-urls",
                "not-a-uuid"))
                .andReturn();

        // Then
        String requestId = result.getResponse().getHeader(REQUEST_ID_HEADER);
        assertThat(UUID.fromString(requestId)).hasToString(requestId);
        assertThat(appender.list).hasSize(1);
        ILoggingEvent event = appender.list.getFirst();
        assertThat(keyValue(event, "type")).isEqualTo("access");
        assertThat(keyValue(event, "method")).isEqualTo("POST");
        assertThat(keyValue(event, "route"))
                .isEqualTo("/internal/v1/signature-processing/{uploadId}/upload-urls");
        assertThat(keyValue(event, "status")).isEqualTo(result.getResponse().getStatus());
        assertThat((Long) keyValue(event, "durationMs")).isGreaterThanOrEqualTo(0L);
        assertThat(event.getMDCPropertyMap()).containsEntry("requestId", requestId);
        assertThat(event.getKeyValuePairs()).noneMatch(pair -> pair.key.equals("requestId"));
    }

    @Test
    @DisplayName("토큰 없이 보호 경로를 호출하면 401 접근 로그에 route는 UNMATCHED이고 응답에는 X-Request-Id가 있다")
    void accessLog_rejectedBySecurity_logsUnmatchedRoute() throws Exception {
        // Given
        UUID postId = UUID.randomUUID();

        // When
        MvcResult result = mockMvc.perform(put(LIKE_PATH, postId)).andReturn();

        // Then
        assertThat(result.getResponse().getHeader(REQUEST_ID_HEADER)).isNotBlank();
        assertThat(appender.list).hasSize(1);
        ILoggingEvent event = appender.list.getFirst();
        assertThat(keyValue(event, "status")).isEqualTo(401);
        assertThat(keyValue(event, "route")).isEqualTo("UNMATCHED");
        assertThat(event.getKeyValuePairs())
                .noneMatch(pair -> String.valueOf(pair.value).contains(postId.toString()));
    }

    @Test
    @DisplayName("로그인한 회원의 요청은 접근 로그에 userId를 정확히 1개 남긴다")
    void accessLog_memberRequest_logsUserId() throws Exception {
        // Given
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        String token = accessTokenProvider.issueForSession(userId, sessionId).value();

        // When
        mockMvc.perform(get("/api/v1/topics")
                .queryParam("date", "2026-01-01")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andReturn();

        // Then
        assertThat(appender.list).hasSize(1);
        assertThat(appender.list.getFirst().getKeyValuePairs()
                .stream().filter(pair -> pair.key.equals("userId")))
                .singleElement()
                .satisfies(pair -> assertThat(pair.value).isEqualTo(userId.toString()));
    }

    @Test
    @DisplayName("토큰이 없는 요청은 접근 로그에 userId 키가 없다")
    void accessLog_anonymousRequest_hasNoUserId() throws Exception {
        // When
        mockMvc.perform(get("/api/v1/topics").queryParam("date", "2026-01-01")).andReturn();

        // Then
        assertThat(appender.list).hasSize(1);
        assertThat(appender.list.getFirst().getKeyValuePairs())
                .noneMatch(pair -> pair.key.equals("userId"));
    }

    @Test
    @DisplayName("actuator 경로는 접근 로그를 남기지 않는다")
    void accessLog_actuatorPath_logsNothing() throws Exception {
        // When
        mockMvc.perform(get("/actuator/health")).andReturn();

        // Then
        assertThat(appender.list).isEmpty();
    }

    @Test
    @DisplayName("요청마다 서로 다른 requestId를 발급한다")
    void requestId_twoRequests_areDifferent() throws Exception {
        // When
        String first = mockMvc.perform(put(LIKE_PATH, UUID.randomUUID()))
                .andReturn().getResponse().getHeader(REQUEST_ID_HEADER);
        String second = mockMvc.perform(put(LIKE_PATH, UUID.randomUUID()))
                .andReturn().getResponse().getHeader(REQUEST_ID_HEADER);

        // Then
        assertThat(first).isNotBlank();
        assertThat(second).isNotBlank().isNotEqualTo(first);
    }

    @Test
    @DisplayName("피드 조회가 200으로 끝나면 접근 로그에 요청한 topicDate를 남긴다")
    void accessLog_successfulFeedQuery_logsTopicDate() throws Exception {
        // Given
        UUID userId = UUID.randomUUID();
        insertUser(userId);
        insertTopic("2026-08-12");
        String token = accessTokenProvider.issue(userId, AccessTokenScope.USER).value();

        // When
        MvcResult result = mockMvc.perform(get("/api/v1/posts")
                .queryParam("topicDate", "2026-08-12")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andReturn();

        // Then
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(appender.list).hasSize(1);
        assertThat(keyValue(appender.list.getFirst(), "topicDate")).isEqualTo("2026-08-12");
    }

    @Test
    @DisplayName("피드 조회의 topicDate가 날짜 형식이 아니면 400이고 접근 로그에 topicDate 키가 없다")
    void accessLog_invalidTopicDateFeedQuery_hasNoTopicDate() throws Exception {
        // When
        MvcResult result = mockMvc.perform(get("/api/v1/posts")
                .queryParam("topicDate", "not-a-date"))
                .andReturn();

        // Then
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(appender.list).hasSize(1);
        assertThat(appender.list.getFirst().getKeyValuePairs())
                .noneMatch(pair -> pair.key.equals("topicDate"));
    }

    @Test
    @DisplayName("피드 조회가 아닌 경로는 접근 로그에 topicDate 키가 없다")
    void accessLog_otherRoute_hasNoTopicDate() throws Exception {
        // When
        mockMvc.perform(get("/api/v1/topics").queryParam("date", "2026-01-01")).andReturn();

        // Then
        assertThat(appender.list).hasSize(1);
        assertThat(appender.list.getFirst().getKeyValuePairs())
                .noneMatch(pair -> pair.key.equals("topicDate"));
    }

    private void insertUser(UUID userId) {
        jdbcTemplate.update("""
                INSERT INTO users (
                    id, email, status, signature_original_storage_key,
                    signature_thumbnail_storage_key, created_at, updated_at
                ) VALUES (
                    ?, 'access-log@example.com', 'ACTIVE',
                    'chalkak/dev/signatures/signature.png',
                    'chalkak/dev/signatures/signature-thumbnail.png',
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """, userId);
    }

    private void insertTopic(String topicDate) {
        jdbcTemplate.update("""
                INSERT INTO topics (
                    id, title, topic_date, starts_at, ends_at, created_at, updated_at
                ) VALUES (
                    ?, '오늘 가장 기억에 남은 순간', CAST(? AS date),
                    CAST(? AS timestamptz), CAST(? AS timestamptz),
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """, UUID.randomUUID(), topicDate, topicDate + "T00:00:00Z", topicDate + "T23:59:59Z");
    }

    private Object keyValue(ILoggingEvent event, String key) {
        return event.getKeyValuePairs().stream()
                .filter(pair -> pair.key.equals(key))
                .findFirst()
                .orElseThrow().value;
    }
}
