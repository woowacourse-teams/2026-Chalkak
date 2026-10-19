package com.chalkak.backend.common.logging;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.logging.LoggingInitializationContext;
import org.springframework.boot.logging.logback.LogbackLoggingSystem;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerMapping;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 실제 logback-spring.xml을 dev 프로필로 읽어 파일 경로 설정과 JSON 출력 계약을 확인한다. 설정이 깨지면
 * 애플리케이션은 정상 기동하고 파일 로그만 조용히 사라지므로 여기서 잡는다. 로깅 시스템은 JVM 전체가 공유하므로 끝나면 프로필 없는 기본
 * 설정으로 되돌린다.
 */
class JsonFileLogTest {

    private static final String CONFIG_LOCATION = "classpath:logback-spring.xml";
    private static final JsonMapper STRICT_MAPPER = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .build();

    private final LogbackLoggingSystem loggingSystem = new LogbackLoggingSystem(
            getClass().getClassLoader());

    @TempDir
    private Path tempDir;

    @AfterEach
    void restoreDefaultLogging() {
        MDC.clear();
        initialize(new MockEnvironment());
    }

    @Test
    @DisplayName("dev 프로필에서는 설정한 경로의 파일에 MDC와 key-value가 최상위 필드로 한 번씩 담긴 JSON 한 줄이 남는다")
    void initialize_devProfile_writesJsonLineToConfiguredFile() throws IOException {
        // Given
        Path logFile = tempDir.resolve("logs").resolve("application.log");
        MockEnvironment environment = new MockEnvironment()
                .withProperty("chalkak.logging.file", logFile.toString())
                .withProperty("chalkak.logging.notification-file",
                        tempDir.resolve("notification.log").toString());
        environment.setActiveProfiles("dev");
        initialize(environment);
        MDC.put(LogFields.REQUEST_ID, "request-id-1");

        // When
        LoggerFactory.getLogger("chalkak.access").atInfo()
                .addKeyValue(LogFields.TYPE, LogFields.TYPE_ACCESS)
                .addKeyValue(LogFields.ROUTE, "/api/v1/posts/{postId}")
                .addKeyValue(LogFields.STATUS, 200)
                .log("access");

        // Then
        List<String> lines = Files.readAllLines(logFile);
        assertThat(lines).hasSize(1);
        JsonNode json = STRICT_MAPPER.readTree(lines.getFirst());
        assertThat(json.has("@timestamp")).isTrue();
        assertThat(json.get("level").asString()).isEqualTo("INFO");
        assertThat(json.get("message").asString()).isEqualTo("access");
        assertThat(json.get(LogFields.REQUEST_ID).asString()).isEqualTo("request-id-1");
        assertThat(json.get(LogFields.TYPE).asString()).isEqualTo("access");
        assertThat(json.get(LogFields.ROUTE).asString()).isEqualTo("/api/v1/posts/{postId}");
        assertThat(json.get(LogFields.STATUS).asInt()).isEqualTo(200);
    }

    @ParameterizedTest
    @ValueSource(strings = {"dev", "prod"})
    @DisplayName("피드 접근 로그는 조회 조건만 JSON 한 줄에 기록하고 게시물 ID 목록은 남기지 않는다")
    void initialize_deployedProfile_writesFeedConditionsWithoutPostIds(String profile)
            throws Exception {
        // Given
        Path logFile = tempDir.resolve("feed.log");
        MockEnvironment environment = new MockEnvironment()
                .withProperty("chalkak.logging.file", logFile.toString())
                .withProperty("chalkak.logging.notification-file",
                        tempDir.resolve("notification.log").toString());
        environment.setActiveProfiles(profile);
        initialize(environment);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/posts");
        request.setParameter("topicDate", "2026-08-12");
        request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/api/v1/posts");
        request.setAttribute(LogFields.FEED_QUERY_REQUEST_ATTRIBUTE,
                new FeedQueryLog("popular", 2, 20));
        MockHttpServletResponse response = new MockHttpServletResponse();

        // When
        new RequestIdFilter().doFilter(request, response, (req, res) -> response.setStatus(200));

        // Then
        List<String> lines = Files.readAllLines(logFile);
        assertThat(lines).hasSize(1);
        JsonNode json = STRICT_MAPPER.readTree(lines.getFirst());
        assertThat(json.has("postIds")).isFalse();
        assertThat(json.get("sort").asString()).isEqualTo("popular");
        assertThat(json.get("page").asInt()).isEqualTo(2);
        assertThat(json.get("pageSize").asInt()).isEqualTo(20);
        assertThat(json.get("requestId").asString()).isEqualTo(response.getHeader("X-Request-Id"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"dev", "prod"})
    @DisplayName("개발·운영의 알림 로그는 MDC를 유지해 전용 파일에만 남고 일반 로그와 분리된다")
    void initialize_deployedProfile_routesNotificationSeparately(String profile)
            throws IOException {
        // Given
        Path applicationFile = tempDir.resolve("application.log");
        Path notificationFile = tempDir.resolve("notification.log");
        MockEnvironment environment = new MockEnvironment()
                .withProperty("chalkak.logging.file", applicationFile.toString())
                .withProperty("chalkak.logging.notification-file", notificationFile.toString());
        environment.setActiveProfiles(profile);
        initialize(environment);
        MDC.put("sqsMessageId", "sqs-id-1");
        MDC.put("receiveCount", "2");

        // When
        LoggerFactory.getLogger("com.chalkak.backend.notification.service.PushWorkerService")
                .atInfo().addKeyValue("type", "notification")
                .addKeyValue("stage", "fcm_result").log("FCM 수락");
        LoggerFactory
                .getLogger("com.chalkak.backend.notification.infrastructure.infra.SqsPushService")
                .atError().addKeyValue("type", "notification")
                .addKeyValue("stage", "worker_processing_failed").log("푸시 처리 실패");
        MDC.clear();
        LoggerFactory.getLogger("chalkak.access").atInfo()
                .addKeyValue("type", "access").log("일반 요청");

        // Then
        List<String> notificationLines = Files.readAllLines(notificationFile);
        assertThat(notificationLines).hasSize(2);
        for (String line : notificationLines) {
            JsonNode json = STRICT_MAPPER.readTree(line);
            assertThat(json.get("sqsMessageId").asString()).isEqualTo("sqs-id-1");
            assertThat(json.get("receiveCount").asString()).isEqualTo("2");
            assertThat(json.get("type").asString()).isEqualTo("notification");
        }
        List<String> applicationLines = Files.readAllLines(applicationFile);
        assertThat(applicationLines).hasSize(1);
        assertThat(STRICT_MAPPER.readTree(applicationLines.getFirst()).get("type").asString())
                .isEqualTo("access");
    }

    @ParameterizedTest
    @ValueSource(strings = {"local", "test"})
    @DisplayName("로컬·테스트에서는 알림 전용 파일과 일반 파일을 생성하지 않는다")
    void initialize_localOrTestProfile_doesNotWriteFiles(String profile) {
        // Given
        Path applicationFile = tempDir.resolve("application.log");
        Path notificationFile = tempDir.resolve("notification.log");
        MockEnvironment environment = new MockEnvironment()
                .withProperty("chalkak.logging.file", applicationFile.toString())
                .withProperty("chalkak.logging.notification-file", notificationFile.toString());
        environment.setActiveProfiles(profile);
        initialize(environment);

        // When
        LoggerFactory.getLogger("com.chalkak.backend.notification.service.PushWorkerService")
                .info("알림 로그");
        LoggerFactory.getLogger("chalkak.access").info("일반 로그");

        // Then
        assertThat(applicationFile).doesNotExist();
        assertThat(notificationFile).doesNotExist();
    }

    private void initialize(MockEnvironment environment) {
        loggingSystem.getSystemProperties(environment).apply(null);
        loggingSystem.cleanUp();
        loggingSystem.beforeInitialize();
        loggingSystem.initialize(
                new LoggingInitializationContext(environment),
                CONFIG_LOCATION,
                null);
    }
}
