package com.chalkak.backend.admin.api.v1.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chalkak.backend.admin.infrastructure.bootstrap.DevelopmentAdminBootstrap;
import com.chalkak.backend.support.IntegrationTestSupport;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * 감사 로그의 requestId가 접근·에러 로그와 같은 값이어야, 장애 때 로그에서 찾은 요청이 어떤 관리자 변경이었는지 감사
 * 기록으로 이어진다. MockMvc는 요청 스레드와 테스트 스레드가 같아 롤백 격리를 그대로 쓴다.
 */
@Transactional
@AutoConfigureMockMvc
class AdminAuditRequestIdTest extends IntegrationTestSupport {

    private static final UUID USER_ID = UUID.fromString("0198fd10-0000-7000-8000-0000000000a2");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private DevelopmentAdminBootstrap developmentAdminBootstrap;

    @BeforeEach
    void setUp() {
        developmentAdminBootstrap.run(new DefaultApplicationArguments());
    }

    @Test
    @DisplayName("관리자 API 요청이 남긴 감사 로그의 requestId는 응답의 X-Request-Id와 같다")
    void updateStatus_adminRequest_savesResponseRequestIdInAuditLog() throws Exception {
        // Given
        jdbcTemplate.update("""
                INSERT INTO users (
                    id, email, status, signature_original_storage_key, created_at, updated_at
                ) VALUES (
                    ?, 'request-id-user@example.com', 'ACTIVE',
                    'signatures/request-id-user', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """, USER_ID);

        // When
        String responseRequestId = mockMvc.perform(
                patch("/api/v1/admin/users/{userId}/status", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status": "BANNED", "reason": "운영 정책 위반"}
                                """))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader("X-Request-Id");
        entityManager.flush();

        // Then
        UUID savedRequestId = jdbcTemplate.queryForObject(
                "SELECT request_id FROM admin_audit_logs WHERE target_id = ?",
                UUID.class,
                USER_ID);
        assertThat(savedRequestId).hasToString(responseRequestId);
    }
}
