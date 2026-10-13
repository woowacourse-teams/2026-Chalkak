package com.chalkak.backend.admin.api.v1.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chalkak.backend.admin.domain.Admin;
import com.chalkak.backend.admin.repository.auth.AdminRepository;
import com.chalkak.backend.auth.domain.token.AccessTokenScope;
import com.chalkak.backend.auth.infrastructure.infra.token.access.JwtAccessTokenProvider;
import com.chalkak.backend.support.IntegrationTestSupport;
import jakarta.persistence.EntityManager;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = "chalkak.admin.authentication.development-bypass-enabled=false")
class AdminNoticeApiTest extends IntegrationTestSupport {

    private static final String PATH = "/api/v1/admin/notices";

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

    private Admin admin;
    private String adminToken;

    @BeforeEach
    void setUp() {
        admin = adminRepository.save(Admin.create("notice-api-manager",
                passwordEncoder.encode("test-password")));
        entityManager.flush();
        adminToken = accessTokenProvider.issue(admin.getId(), AccessTokenScope.ADMIN).value();
    }

    @Test
    @DisplayName("관리자 토큰으로 공지를 등록하면 저장하고 처리 관리자를 감사 로그에 남긴다")
    void createNotice_adminToken_persistsNoticeWithAuditActor() throws Exception {
        // Given
        Instant scheduledAt = Instant.now().plus(Duration.ofDays(1))
                .truncatedTo(ChronoUnit.SECONDS);

        // When & Then
        mockMvc.perform(post(PATH)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"서비스 점검 안내\",\"body\":\"점검합니다.\",\"scheduledAt\":\""
                        + scheduledAt + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.scheduledAt").value(scheduledAt.toString()));
        entityManager.flush();
        assertThat(jdbcTemplate.queryForObject("SELECT scheduled_at FROM notices", Instant.class))
                .isEqualTo(scheduledAt);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT actor_admin_id FROM admin_audit_logs
                WHERE action = CAST('NOTICE_CREATED' AS admin_action)
                """, UUID.class)).isEqualTo(admin.getId());
    }
}
