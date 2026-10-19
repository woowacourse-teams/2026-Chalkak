package com.chalkak.backend.admin.banner.api.v1.controller;

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
import java.util.UUID;
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
    @DisplayName("관리자 토큰으로 등록하면 배너를 저장하고 인증된 관리자를 감사 로그에 기록한다")
    void createBanner_adminToken_persistsBannerAndActor() throws Exception {
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
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM banners", Integer.class))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT actor_admin_id FROM admin_audit_logs WHERE target_type = 'BANNER'
                """, UUID.class)).isEqualTo(admin.getId());
    }
}
