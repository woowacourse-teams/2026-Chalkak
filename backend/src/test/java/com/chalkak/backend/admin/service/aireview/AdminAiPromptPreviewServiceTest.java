package com.chalkak.backend.admin.service.aireview;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.chalkak.backend.admin.service.aireview.AdminAiPromptPreviewService.PreviewCommand;
import com.chalkak.backend.admin.service.aireview.AdminAiPromptPreviewService.PreviewResult;
import com.chalkak.backend.aireview.domain.AiPromptParameters.ImageDetail;
import com.chalkak.backend.aireview.domain.AiPromptType;
import com.chalkak.backend.aireview.domain.AiReviewResult;
import com.chalkak.backend.aireview.domain.PhotoReviewOutput;
import com.chalkak.backend.aireview.service.AiReviewGeneration;
import com.chalkak.backend.aireview.service.AiReviewGeneration.Attempt;
import com.chalkak.backend.aireview.service.AiReviewGeneration.Usage;
import com.chalkak.backend.aireview.service.AiReviewGenerationException;
import com.chalkak.backend.aireview.service.AiReviewGenerationException.FailureType;
import com.chalkak.backend.aireview.service.AiReviewGenerationRequest;
import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.NotFoundException;
import com.chalkak.backend.photo.service.ImageUrlProvider;
import com.chalkak.backend.support.DatabaseCleaner;
import com.chalkak.backend.support.IntegrationTestSupport;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * OpenAI 호출 중 DB 연결을 잡지 않도록 테스트 클래스에 @Transactional을 쓰지 않고 DatabaseCleaner로
 * 정리한다.
 */
class AdminAiPromptPreviewServiceTest extends IntegrationTestSupport {

    private static final UUID ADMIN_ID = UUID.fromString("019a0000-0000-7000-8000-000000000687");
    private static final UUID USER_ID = UUID.fromString("019a0000-0000-7000-8000-000000000688");
    private static final UUID TOPIC_ID = UUID.fromString("019a0000-0000-7000-8000-000000000689");
    private static final UUID PHOTO_ID = UUID.fromString("019a0000-0000-7000-8000-000000000690");
    private static final UUID POST_ID = UUID.fromString("019a0000-0000-7000-8000-000000000691");
    private static final String MODEL = "gpt-6.1-sol";
    private static final String STORAGE_KEY = "chalkak/test/ai-preview/original.webp";
    private static final String IMAGE_URL = "https://cdn.test/ai-preview/original.webp";
    private static final String TOPIC_TITLE = "노을";
    private static final String SYSTEM_PROMPT = "비밀 시스템 프롬프트 문장";

    @Autowired
    private AdminAiPromptPreviewService service;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private ImageUrlProvider imageUrlProvider;

    private final Logger logger = (Logger) LoggerFactory.getLogger("chalkak.aireview");
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    @BeforeEach
    void setUp() {
        new DatabaseCleaner(jdbcTemplate).clean();
        insertFixture(
                "APPROVED",
                false,
                false,
                false,
                false
        );
        given(imageUrlProvider.getUrl(STORAGE_KEY)).willReturn(IMAGE_URL);
        given(aiReviewGenerator.supportsModel(MODEL)).willReturn(true);
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.INFO);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
        new DatabaseCleaner(jdbcTemplate).clean();
    }

    @Test
    @DisplayName("공개 게시물에 초안을 실행하면 변환 결과 원본 점수 사용량을 반환하고 원본 이미지 URL과 주제 제목으로 호출한다")
    void preview_publicPost_returnsOutcomeAndCallsGenerator() {
        // Given
        AiReviewGeneration generation = successGeneration();
        given(aiReviewGenerator.generate(any())).willReturn(generation);

        // When
        PreviewResult result = service.preview(ADMIN_ID, command(MODEL, "주제: {{topicTitle}}"));

        // Then
        assertThat(result.succeeded()).isTrue();
        assertThat(result.failureType()).isNull();
        assertThat(result.retryable()).isNull();
        assertThat(result.result()).isEqualTo(generation.result());
        assertThat(result.output()).isEqualTo(new PhotoReviewOutput(
                8.72,
                8.0,
                "구도가 좋습니다."
        ));
        assertThat(result.usage()).isEqualTo(new Usage(
                1200,
                300,
                85,
                40
        ));
        assertThat(result.latencyMs()).isEqualTo(1234L);
        assertThat(result.responseId()).isEqualTo("resp_1");
        ArgumentCaptor<AiReviewGenerationRequest> captor = ArgumentCaptor
                .forClass(AiReviewGenerationRequest.class);
        verify(aiReviewGenerator).generate(captor.capture());
        assertThat(captor.getValue().imageUrl()).isEqualTo(IMAGE_URL);
        assertThat(captor.getValue().topicTitle()).isEqualTo(TOPIC_TITLE);
        assertThat(captor.getValue().draft().systemPrompt()).isEqualTo(SYSTEM_PROMPT);
    }

    @Test
    @DisplayName("OpenAI를 호출하는 동안에는 트랜잭션과 DB 연결을 잡지 않는다")
    void preview_duringGeneration_hasNoActiveTransaction() {
        // Given
        AtomicBoolean transactionActive = new AtomicBoolean(true);
        given(aiReviewGenerator.generate(any())).willAnswer(invocation -> {
            transactionActive.set(TransactionSynchronizationManager.isActualTransactionActive());
            return successGeneration();
        });

        // When
        service.preview(ADMIN_ID, command(MODEL, "주제: {{topicTitle}}"));

        // Then
        assertThat(transactionActive).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"PENDING", "REJECTED"})
    @DisplayName("승인되지 않은 게시물은 404로 거절하고 OpenAI를 호출하지 않는다")
    void preview_notApprovedPost_throwsNotFound(String status) {
        // Given
        new DatabaseCleaner(jdbcTemplate).clean();
        insertFixture(
                status,
                false,
                false,
                false,
                false
        );

        // When & Then
        assertNotFoundWithoutCall();
    }

    @ParameterizedTest
    @ValueSource(strings = {"post", "topic", "photo", "author"})
    @DisplayName("삭제된 게시물 주제 사진 작성자가 있으면 404로 거절하고 OpenAI를 호출하지 않는다")
    void preview_deletedRelation_throwsNotFound(String deleted) {
        // Given
        new DatabaseCleaner(jdbcTemplate).clean();
        insertFixture(
                "APPROVED",
                deleted.equals("post"),
                deleted.equals("topic"),
                deleted.equals("photo"),
                deleted.equals("author")
        );

        // When & Then
        assertNotFoundWithoutCall();
    }

    @Test
    @DisplayName("없는 게시물은 404로 거절한다")
    void preview_missingPost_throwsNotFound() {
        // When & Then
        assertThatThrownBy(() -> service.preview(ADMIN_ID, new PreviewCommand(
                AiPromptType.PHOTO_REVIEW,
                SYSTEM_PROMPT,
                "주제: {{topicTitle}}",
                MODEL,
                null,
                null,
                null,
                null,
                UUID.randomUUID()
        )))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("허용되지 않은 모델은 400으로 거절하고 OpenAI를 호출하지 않는다")
    void preview_unsupportedModel_throwsBusinessExceptionWithoutCall() {
        // Given
        given(aiReviewGenerator.supportsModel("gpt-unknown")).willReturn(false);

        // When & Then
        assertThatThrownBy(() -> service.preview(ADMIN_ID,
                command("gpt-unknown", "주제: {{topicTitle}}")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("허용되지 않은 모델입니다.");
        verify(aiReviewGenerator, never()).generate(any());
    }

    @Test
    @DisplayName("허용되지 않은 템플릿 변수는 400으로 거절하고 OpenAI를 호출하지 않는다")
    void preview_unknownPlaceholder_throwsBusinessExceptionWithoutCall() {
        // When & Then
        assertThatThrownBy(() -> service.preview(ADMIN_ID, command(MODEL, "작성자: {{userId}}")))
                .isInstanceOf(BusinessException.class);
        verify(aiReviewGenerator, never()).generate(any());
    }

    @Test
    @DisplayName("관리자 식별자가 없으면 400으로 거절한다")
    void preview_nullAdminId_throwsBusinessException() {
        // When & Then
        assertThatThrownBy(() -> service.preview(null, command(MODEL, "주제: {{topicTitle}}")))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("응답 본문을 받은 뒤 실패하면 200 결과로 실패 유형 재시도 여부 사용량을 반환한다")
    void preview_incompleteWithAttempt_returnsFailureOutcomeWithUsage() {
        // Given
        Attempt attempt = new Attempt(
                new Usage(
                        1000,
                        10,
                        16000,
                        15900
                ),
                Duration.ofMillis(900),
                "resp_9"
        );
        given(aiReviewGenerator.generate(any())).willThrow(
                new AiReviewGenerationException(FailureType.INCOMPLETE, attempt));

        // When
        PreviewResult result = service.preview(ADMIN_ID, command(MODEL, "주제: {{topicTitle}}"));

        // Then
        assertThat(result.succeeded()).isFalse();
        assertThat(result.failureType()).isEqualTo(FailureType.INCOMPLETE);
        assertThat(result.retryable()).isFalse();
        assertThat(result.result()).isNull();
        assertThat(result.output()).isNull();
        assertThat(result.usage()).isEqualTo(new Usage(
                1000,
                10,
                16000,
                15900
        ));
        assertThat(result.latencyMs()).isEqualTo(900L);
        assertThat(result.responseId()).isEqualTo("resp_9");
    }

    @Test
    @DisplayName("본문 없이 시간 초과로 실패하면 사용량 없이 재시도 가능으로 반환한다")
    void preview_timeoutWithoutAttempt_returnsFailureOutcomeWithoutUsage() {
        // Given
        given(aiReviewGenerator.generate(any()))
                .willThrow(new AiReviewGenerationException(FailureType.TIMEOUT));

        // When
        PreviewResult result = service.preview(ADMIN_ID, command(MODEL, "주제: {{topicTitle}}"));

        // Then
        assertThat(result.succeeded()).isFalse();
        assertThat(result.failureType()).isEqualTo(FailureType.TIMEOUT);
        assertThat(result.retryable()).isTrue();
        assertThat(result.usage()).isNull();
        assertThat(result.latencyMs()).isNull();
        assertThat(result.responseId()).isNull();
    }

    @Test
    @DisplayName("미리보기는 어떤 감사 로그도 남기지 않는다")
    void preview_success_doesNotPersistAuditLog() {
        // Given
        given(aiReviewGenerator.generate(any())).willReturn(successGeneration());

        // When
        service.preview(ADMIN_ID, command(MODEL, "주제: {{topicTitle}}"));

        // Then
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM admin_audit_logs",
                Integer.class)).isZero();
    }

    @Test
    @DisplayName("성공하면 구조화 로그 한 줄에 관리자 모델 사용량을 남기고 프롬프트 이미지 주제 피드백은 남기지 않는다")
    void preview_success_logsStructuredLineWithoutSensitiveText() {
        // Given
        given(aiReviewGenerator.generate(any())).willReturn(successGeneration());

        // When
        service.preview(ADMIN_ID, command(MODEL, "주제: {{topicTitle}}"));

        // Then
        assertThat(appender.list).singleElement().satisfies(event -> {
            String keyValues = event.getKeyValuePairs().toString();
            assertThat(keyValues).contains(
                    "type=\"ai_review\"",
                    "event=\"preview\"",
                    "adminId=\"" + ADMIN_ID + "\"",
                    "model=\"" + MODEL + "\"",
                    "succeeded=\"true\"",
                    "latencyMs=\"1234\"",
                    "inputTokens=\"1200\"",
                    "cachedInputTokens=\"300\"",
                    "outputTokens=\"85\"",
                    "reasoningTokens=\"40\""
            );
            String everything = event.getFormattedMessage() + keyValues;
            assertThat(everything).doesNotContain(
                    SYSTEM_PROMPT,
                    IMAGE_URL,
                    TOPIC_TITLE,
                    "구도가 좋습니다."
            );
        });
    }

    @Test
    @DisplayName("실패하면 로그에 실패 유형을 남기고 값이 없는 항목은 남기지 않는다")
    void preview_failure_logsFailureTypeOnly() {
        // Given
        given(aiReviewGenerator.generate(any()))
                .willThrow(new AiReviewGenerationException(FailureType.TIMEOUT));

        // When
        service.preview(ADMIN_ID, command(MODEL, "주제: {{topicTitle}}"));

        // Then
        assertThat(appender.list).singleElement().satisfies(event -> {
            String keyValues = event.getKeyValuePairs().toString();
            assertThat(keyValues).contains("succeeded=\"false\"", "failureType=\"TIMEOUT\"");
            assertThat(keyValues).doesNotContain("inputTokens", "latencyMs");
        });
    }

    private void assertNotFoundWithoutCall() {
        assertThatThrownBy(() -> service.preview(ADMIN_ID, command(MODEL, "주제: {{topicTitle}}")))
                .isInstanceOf(NotFoundException.class);
        verify(aiReviewGenerator, never()).generate(any());
    }

    private PreviewCommand command(String model, String template) {
        return new PreviewCommand(
                AiPromptType.PHOTO_REVIEW,
                SYSTEM_PROMPT,
                template,
                model,
                ImageDetail.HIGH,
                8000,
                null,
                null,
                POST_ID
        );
    }

    private AiReviewGeneration successGeneration() {
        PhotoReviewOutput output = new PhotoReviewOutput(
                8.72,
                8.0,
                "구도가 좋습니다."
        );
        AiReviewResult result = output.toResult(7.0);
        return new AiReviewGeneration(
                result,
                output,
                "{}",
                new Attempt(
                        new Usage(
                                1200,
                                300,
                                85,
                                40
                        ),
                        Duration.ofMillis(1234),
                        "resp_1"
                )
        );
    }

    private void insertFixture(
            String status,
            boolean postDeleted,
            boolean topicDeleted,
            boolean photoDeleted,
            boolean authorDeleted
    ) {
        String deletedAt = "TIMESTAMPTZ '2026-10-01T00:00:00Z'";
        jdbcTemplate.update("""
                INSERT INTO users (id, email, status, signature_original_storage_key,
                    created_at, updated_at, deleted_at)
                VALUES (?, 'ai-preview@example.com', 'ACTIVE', 'chalkak/test/sign.png',
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, %s)
                """.formatted(authorDeleted ? deletedAt : "NULL"), USER_ID);
        jdbcTemplate.update(
                """
                        INSERT INTO topics (id, title, topic_date, starts_at, ends_at,
                            created_at, updated_at, deleted_at)
                        VALUES (?, ?, CURRENT_DATE + 301, '2026-10-01T00:00:00Z', '2026-10-02T00:00:00Z',
                            CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, %s)
                        """
                        .formatted(topicDeleted ? deletedAt : "NULL"),
                TOPIC_ID,
                TOPIC_TITLE
        );
        jdbcTemplate.update(
                """
                        INSERT INTO photos (id, original_storage_key, thumbnail_storage_key, metadata,
                            created_at, updated_at, deleted_at)
                        VALUES (?, ?, 'chalkak/test/ai-preview/thumb.webp',
                            CAST('{"width":1024,"height":768}' AS jsonb),
                            CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, %s)
                        """
                        .formatted(photoDeleted ? deletedAt : "NULL"),
                PHOTO_ID,
                STORAGE_KEY
        );
        jdbcTemplate.update(
                """
                        INSERT INTO posts (id, user_id, topic_id, photo_id, post_image_upload_id, title,
                            moderation_status, moderated_at, created_at, updated_at, deleted_at)
                        VALUES (?, ?, ?, ?, NULL, '미리보기 대상', CAST(? AS moderation_status),
                            NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, %s)
                        """
                        .formatted(postDeleted ? deletedAt : "NULL"),
                POST_ID,
                USER_ID,
                TOPIC_ID,
                PHOTO_ID,
                status
        );
    }
}
