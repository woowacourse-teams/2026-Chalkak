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
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.logging.LoggingInitializationContext;
import org.springframework.boot.logging.logback.LogbackLoggingSystem;
import org.springframework.mock.env.MockEnvironment;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 실제 logback-spring.xml을 dev 프로필로 읽어 파일 경로 설정과 JSON 출력 계약을 확인한다. 설정이 깨지면 애플리케이션은
 * 정상 기동하고 파일 로그만 조용히 사라지므로 여기서 잡는다. 로깅 시스템은 JVM 전체가 공유하므로 끝나면 프로필 없는 기본
 * 설정으로 되돌린다.
 */
class JsonFileLogTest {

    private static final String CONFIG_LOCATION = "classpath:logback-spring.xml";
    private static final JsonMapper STRICT_MAPPER = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .build();

    private final LogbackLoggingSystem loggingSystem =
            new LogbackLoggingSystem(getClass().getClassLoader());

    @TempDir
    private Path tempDir;

    @AfterEach
    void restoreDefaultLogging() {
        MDC.remove(LogFields.REQUEST_ID);
        initialize(new MockEnvironment());
    }

    @Test
    @DisplayName("dev 프로필에서는 설정한 경로의 파일에 MDC와 key-value가 최상위 필드로 한 번씩 담긴 JSON 한 줄이 남는다")
    void initialize_devProfile_writesJsonLineToConfiguredFile() throws IOException {
        // Given
        Path logFile = tempDir.resolve("logs").resolve("application.log");
        MockEnvironment environment = new MockEnvironment()
                .withProperty("chalkak.logging.file", logFile.toString());
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
