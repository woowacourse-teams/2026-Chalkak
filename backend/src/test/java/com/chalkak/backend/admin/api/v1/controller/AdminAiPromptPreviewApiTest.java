package com.chalkak.backend.admin.api.v1.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chalkak.backend.admin.domain.Admin;
import com.chalkak.backend.admin.repository.auth.AdminRepository;
import com.chalkak.backend.aireview.domain.PhotoReviewOutput;
import com.chalkak.backend.aireview.service.AiReviewGeneration;
import com.chalkak.backend.aireview.service.AiReviewGeneration.Attempt;
import com.chalkak.backend.aireview.service.AiReviewGeneration.Usage;
import com.chalkak.backend.auth.domain.token.AccessTokenScope;
import com.chalkak.backend.auth.infrastructure.infra.token.access.JwtAccessTokenProvider;
import com.chalkak.backend.photo.service.ImageUrlProvider;
import com.chalkak.backend.support.IntegrationTestSupport;
import jakarta.persistence.EntityManager;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = "chalkak.admin.authentication.development-bypass-enabled=false")
class AdminAiPromptPreviewApiTest extends IntegrationTestSupport {

    private static final String PATH = "/api/v1/admin/ai-prompt-previews";
    private static final UUID USER_ID = UUID.fromString("019a0000-0000-7000-8000-000000000701");
    private static final UUID TOPIC_ID = UUID.fromString("019a0000-0000-7000-8000-000000000702");
    private static final UUID PHOTO_ID = UUID.fromString("019a0000-0000-7000-8000-000000000703");
    private static final UUID POST_ID = UUID.fromString("019a0000-0000-7000-8000-000000000704");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AdminRepository adminRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtAccessTokenProvider accessTokenProvider;

    @MockitoBean
    private ImageUrlProvider imageUrlProvider;

    private String adminToken;

    @BeforeEach
    void setUp() {
        Admin admin = adminRepository.save(Admin.create("ai-preview-api-manager",
                passwordEncoder.encode("test-password")));
        entityManager.flush();
        adminToken = accessTokenProvider.issue(admin.getId(), AccessTokenScope.ADMIN).value();
        given(aiReviewGenerator.supportsModel("gpt-6.1-sol")).willReturn(true);
        given(imageUrlProvider.getUrl("chalkak/test/ai-preview-api/original.webp"))
                .willReturn("https://cdn.test/ai-preview-api/original.webp");
    }

    @Test
    @DisplayName("관리자가 공개 게시물에 초안을 실행하면 200과 결과를 응답하고 감사 로그는 남기지 않는다")
    void preview_adminToken_returnsOutcome() throws Exception {
        // Given
        insertApprovedPost();
        PhotoReviewOutput output = new PhotoReviewOutput(
                8.72,
                8.0,
                "구도가 좋습니다."
        );
        given(aiReviewGenerator.generate(any())).willReturn(new AiReviewGeneration(
                output.toResult(7.0),
                output,
                "{}",
                new Attempt(
                        new Usage(
                                1200,
                                null,
                                85,
                                null
                        ),
                        Duration.ofMillis(10),
                        "resp_1"
                )
        ));

        // When & Then
        mockMvc.perform(post(PATH)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(POST_ID, "gpt-6.1-sol")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.succeeded").value(true))
                .andExpect(jsonPath("$.result.overallScore").value(8.7))
                .andExpect(jsonPath("$.usage.cachedInputTokens")
                        .value(org.hamcrest.Matchers.nullValue()));
        org.assertj.core.api.Assertions.assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM admin_audit_logs", Integer.class)).isZero();
    }

    @Test
    @DisplayName("없는 게시물은 404이다")
    void preview_missingPost_returnsNotFound() throws Exception {
        // When & Then
        mockMvc.perform(post(PATH)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(UUID.randomUUID(), "gpt-6.1-sol")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_ERROR"));
    }

    @Test
    @DisplayName("허용되지 않은 모델은 400이다")
    void preview_unsupportedModel_returnsBadRequest() throws Exception {
        // When & Then
        mockMvc.perform(post(PATH)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(POST_ID, "gpt-unknown")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("허용되지 않은 모델입니다."));
    }

    private String body(UUID postId, String model) {
        return """
                {"type":"PHOTO_REVIEW","systemPrompt":"시스템","userPromptTemplate":"주제: {{topicTitle}}",
                 "model":"%s","postId":"%s"}
                """
                .formatted(model, postId);
    }

    private void insertApprovedPost() {
        jdbcTemplate.update("""
                INSERT INTO users (id, email, status, signature_original_storage_key,
                    created_at, updated_at)
                VALUES (?, 'ai-preview-api@example.com', 'ACTIVE', 'chalkak/test/sign.png',
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, USER_ID);
        jdbcTemplate.update(
                """
                        INSERT INTO topics (id, title, topic_date, starts_at, ends_at, created_at, updated_at)
                        VALUES (?, '노을', CURRENT_DATE + 302, '2026-10-01T00:00:00Z',
                            '2026-10-02T00:00:00Z', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                        """,
                TOPIC_ID);
        jdbcTemplate.update("""
                INSERT INTO photos (id, original_storage_key, thumbnail_storage_key, metadata,
                    created_at, updated_at)
                VALUES (?, 'chalkak/test/ai-preview-api/original.webp',
                    'chalkak/test/ai-preview-api/thumb.webp',
                    CAST('{"width":1024,"height":768}' AS jsonb),
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, PHOTO_ID);
        jdbcTemplate.update(
                """
                        INSERT INTO posts (id, user_id, topic_id, photo_id, post_image_upload_id, title,
                            moderation_status, moderated_at, created_at, updated_at)
                        VALUES (?, ?, ?, ?, NULL, '미리보기 대상', 'APPROVED', NULL,
                            CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                        """,
                POST_ID,
                USER_ID,
                TOPIC_ID,
                PHOTO_ID
        );
    }
}
