package com.chalkak.backend.admin.api.v1.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chalkak.backend.admin.domain.Admin;
import com.chalkak.backend.admin.repository.auth.AdminRepository;
import com.chalkak.backend.auth.domain.token.AccessTokenScope;
import com.chalkak.backend.auth.infrastructure.infra.token.access.JwtAccessTokenProvider;
import com.chalkak.backend.support.IntegrationTestSupport;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
class AdminBannerApiTest extends IntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AdminRepository adminRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JwtAccessTokenProvider accessTokenProvider;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    @DisplayName("관리자 토큰으로 등록·조회하면 배너를 반환하고 인증된 관리자를 등록 감사 로그에 기록한다")
    void createAndGetBanners_adminToken_returnsBannerAndRecordsActor() throws Exception {
        // Given
        Admin admin = adminRepository.save(Admin.create("banner-api-manager",
                passwordEncoder.encode("test-password")));
        entityManager.flush();
        String token = accessTokenProvider.issue(admin.getId(), AccessTokenScope.ADMIN).value();

        // When & Then
        mockMvc.perform(post("/api/v1/admin/banners")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"title":"제목","subtitle":"부제목",
                        "webViewUrl":"https://chalkak.vercel.app/event",
                        "thumbnailUrl":"https://cdn.example.com/banner.png"}
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.webViewUrl").value("https://chalkak.vercel.app/event"));
        entityManager.flush();
        entityManager.clear();
        mockMvc.perform(get("/api/v1/admin/banners")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.banners.length()").value(1))
                .andExpect(jsonPath("$.banners[0].webViewUrl")
                        .value("https://chalkak.vercel.app/event"));
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM banners", Integer.class))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT actor_admin_id FROM admin_audit_logs WHERE target_type = 'BANNER'
                """, UUID.class)).isEqualTo(admin.getId());
    }
    @ParameterizedTest
    @CsvSource({"0, 20", "1, 0", "1, 101", "21474838, 100"})
    @DisplayName("페이지 범위·offset 검증은 서비스에서 수행하고 HTTP 400으로 응답한다")
    void getBanners_invalidPagination_returnsBadRequest(int page, int pageSize) throws Exception {
        // Given
        String token = accessTokenProvider.issue(UUID.randomUUID(), AccessTokenScope.ADMIN).value();

        // When & Then
        mockMvc.perform(get("/api/v1/admin/banners")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .param("page", Integer.toString(page))
                .param("pageSize", Integer.toString(pageSize)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_ERROR"))
                .andExpect(jsonPath("$.message").value("조회 조건이 올바르지 않습니다."));
    }
    @Test
    @DisplayName("본문 없는 관리자 삭제는 204를 반환하고 인증된 관리자를 감사에 기록한다")
    void deleteBanner_adminToken_recordsAuthenticatedActor() throws Exception {
        // Given
        Admin admin = adminRepository
                .save(Admin.create("banner-delete-api", passwordEncoder.encode("test-password")));
        com.chalkak.backend.banner.domain.Banner banner = com.chalkak.backend.banner.domain.Banner
                .create("제목", "부제목",
                        "https://example.com/event", "https://example.com/image.png");
        entityManager.persist(banner);
        entityManager.flush();
        String token = accessTokenProvider.issue(admin.getId(), AccessTokenScope.ADMIN).value();

        // When & Then
        mockMvc.perform(delete("/api/v1/admin/banners/" + banner.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNoContent());
        entityManager.flush();
        assertThat(jdbcTemplate.queryForObject("""
                SELECT actor_admin_id FROM admin_audit_logs
                WHERE target_id = ? AND action = 'BANNER_DELETED'
                """, UUID.class, banner.getId())).isEqualTo(admin.getId());
        assertThat(jdbcTemplate.queryForObject(
                """
                        SELECT reason FROM admin_audit_logs WHERE target_id = ? AND action = 'BANNER_DELETED'
                        """,
                String.class, banner.getId())).isNull();
    }

}
