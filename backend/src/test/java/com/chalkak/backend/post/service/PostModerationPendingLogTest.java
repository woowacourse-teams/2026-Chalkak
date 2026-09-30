package com.chalkak.backend.post.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.chalkak.backend.photo.service.ImageUrlProvider;
import com.chalkak.backend.post.repository.PostImageStorage;
import com.chalkak.backend.support.IntegrationTestSupport;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class PostModerationPendingLogTest extends IntegrationTestSupport {

    private static final UUID USER_ID =
            UUID.fromString("0199a003-0000-7000-8000-000000000001");
    private static final UUID TOPIC_ID =
            UUID.fromString("0199a003-0000-7000-8000-000000000002");
    private static final UUID UPLOAD_ID =
            UUID.fromString("0199a003-0000-7000-8000-000000000003");

    @Autowired
    private PostCommandService postCommandService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private PostImageStorage postImageStorage;

    @MockitoBean
    private ImageUrlProvider imageUrlProvider;

    @MockitoBean
    private RandomSeedGenerator randomSeedGenerator;

    private final Logger moderationLogger = (Logger) LoggerFactory.getLogger("chalkak.moderation");
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("""
                INSERT INTO users (
                    id, email, status, signature_original_storage_key, created_at, updated_at
                ) VALUES (
                    ?, 'moderation-log@example.com', 'ACTIVE',
                    'chalkak/signatures/test/original/moderation-log.png',
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """, USER_ID);
        jdbcTemplate.update("""
                INSERT INTO topics (
                    id, title, topic_date, starts_at, ends_at, created_at, updated_at
                ) VALUES (
                    ?, '검수 로그 테스트', CURRENT_DATE + 102,
                    CURRENT_TIMESTAMP - INTERVAL '1 hour',
                    CURRENT_TIMESTAMP + INTERVAL '1 hour',
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """, TOPIC_ID);
        jdbcTemplate.update("""
                INSERT INTO post_image_uploads (
                    id, user_id, status, image_metadata, expires_at, created_at, updated_at
                ) VALUES (
                    ?, ?, CAST('ISSUED' AS post_image_upload_status), NULL,
                    CURRENT_TIMESTAMP + INTERVAL '1 hour',
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """, UPLOAD_ID, USER_ID);
        given(postImageStorage.toOriginalStorageKey(UPLOAD_ID))
                .willReturn("chalkak/posts/test/original/" + UPLOAD_ID + ".webp");
        given(postImageStorage.toThumbnailStorageKey(UPLOAD_ID))
                .willReturn("chalkak/posts/test/thumbnail/" + UPLOAD_ID + ".webp");
        given(postImageStorage.existsUploadedImage(UPLOAD_ID)).willReturn(true);
        appender.start();
        moderationLogger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        moderationLogger.detachAppender(appender);
        appender.stop();
    }

    @Test
    @DisplayName("이미지 완료 콜백이 게시물을 PENDING으로 전환하면 검수 대기 로그를 한 건 남긴다")
    void completePostImageProcessing_validatingPost_logsPending() {
        // Given
        UUID postId = postCommandService.createPost(USER_ID, TOPIC_ID, UPLOAD_ID, "대기 로그")
                .postId();
        assertThat(appender.list).isEmpty();

        // When
        postCommandService.completePostImageProcessing(
                UPLOAD_ID,
                Map.of("width", 4032, "height", 3024));

        // Then
        assertThat(appender.list).hasSize(1);
        ILoggingEvent event = appender.list.getFirst();
        assertThat(event.getLevel()).isEqualTo(Level.INFO);
        assertThat(keyValue(event, "type")).isEqualTo("moderation");
        assertThat(keyValue(event, "event")).isEqualTo("pending");
        assertThat(keyValue(event, "postId")).isEqualTo(postId);
    }

    private Object keyValue(ILoggingEvent event, String key) {
        return event.getKeyValuePairs().stream()
                .filter(pair -> pair.key.equals(key))
                .findFirst()
                .orElseThrow()
                .value;
    }
}
