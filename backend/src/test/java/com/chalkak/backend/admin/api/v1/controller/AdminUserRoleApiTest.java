package com.chalkak.backend.admin.api.v1.controller;

import static com.chalkak.backend.post.support.AuthorPostFixtures.createUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chalkak.backend.admin.service.user.AdminUserRoleService;
import com.chalkak.backend.auth.domain.token.AccessTokenScope;
import com.chalkak.backend.auth.infrastructure.infra.token.access.JwtAccessTokenProvider;
import com.chalkak.backend.support.DatabaseCleaner;
import com.chalkak.backend.support.IntegrationTestSupport;
import com.chalkak.backend.user.domain.UserRole;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@AutoConfigureMockMvc
@TestPropertySource(properties = "chalkak.admin.authentication.development-bypass-enabled=false")
class AdminUserRoleApiTest extends IntegrationTestSupport {

    private static final String PATH = "/api/v1/admin/users/{userId}/role";
    private static final String GRANT = """
            {"role":"ARTIST","reason":"  운영팀 추천  "}
            """;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private JwtAccessTokenProvider accessTokenProvider;
    @Autowired
    private AdminUserRoleService roleService;

    private final UUID adminId = UUID.randomUUID();
    private UUID userId;

    @BeforeEach
    void setUp() {
        new DatabaseCleaner(jdbcTemplate).clean();
        userId = createUser(jdbcTemplate);
        jdbcTemplate.update(
                "INSERT INTO admins (id, username, password) VALUES (?, ?, ?)",
                adminId,
                "role-grant-" + adminId,
                "test-password"
        );
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM admin_audit_logs WHERE actor_admin_id = ?", adminId);
        jdbcTemplate.update("DELETE FROM admins WHERE id = ?", adminId);
        new DatabaseCleaner(jdbcTemplate).clean();
    }

    @Test
    @DisplayName("관리자는 일반 회원에게 작가 권한을 부여하고 감사 로그를 남기며 회원은 나중에 직접 동의한다")
    void updateRole_regularUser_recordsGrantAndAllowsMemberConsent() throws Exception {
        // When
        String requestId = updateRole(userId, GRANT)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(userId.toString()))
                .andExpect(jsonPath("$.role").value("ARTIST"))
                .andReturn().getResponse().getHeader("X-Request-Id");

        // Then
        assertThat(findRole()).isEqualTo("ARTIST");
        assertThat(jdbcTemplate.queryForMap("""
                SELECT actor_admin_id, target_type::text, reason, request_id,
                       before_state = '{"role":"USER"}'::jsonb AS started_as_user,
                       after_state = '{"role":"ARTIST"}'::jsonb AS became_artist
                FROM admin_audit_logs WHERE action = 'USER_ROLE_CHANGED' AND target_id = ?
                """, userId))
                .containsEntry("actor_admin_id", adminId)
                .containsEntry("target_type", "USER")
                .containsEntry("reason", "운영팀 추천")
                .containsEntry("request_id", UUID.fromString(requestId))
                .containsEntry("started_as_user", true)
                .containsEntry("became_artist", true);
        mockMvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, memberToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ARTIST"))
                .andExpect(jsonPath("$.artistTermsAgreed").value(false));
        mockMvc.perform(post("/api/v1/users/me/artist")
                .header(HttpHeaders.AUTHORIZATION, memberToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"artistTermsAgreed\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.artistTermsAgreed").value(true));
        assertThat(countAudits()).isEqualTo(1);
    }

    @Test
    @DisplayName("정지 회원에게 작가 역할을 부여해도 이용 정지는 유지된다")
    void updateRole_bannedUser_keepsBan() throws Exception {
        // Given
        jdbcTemplate.update("UPDATE users SET status = 'BANNED' WHERE id = ?", userId);

        // When & Then
        updateRole(userId, GRANT).andExpect(status().isOk());
        assertThat(findRole()).isEqualTo("ARTIST");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status::text FROM users WHERE id = ?",
                String.class,
                userId
        )).isEqualTo("BANNED");
    }

    @Test
    @DisplayName("같은 회원에게 다시 작가 권한을 부여하면 감사 기록을 추가하지 않는다")
    void updateRole_alreadyArtist_keepsFirstAudit() throws Exception {
        // Given
        updateRole(userId, GRANT).andExpect(status().isOk());

        // When & Then
        updateRole(userId, GRANT)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_STATE_CHANGED"));
        assertThat(countAudits()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"role\":\"ARTIST\"}", "{\"role\":\"ARTIST\",\"reason\":\" \"}",
            "{\"role\":\"USER\",\"reason\":\"운영팀 추천\"}", "{\"reason\":\"운영팀 추천\"}"})
    @DisplayName("역할이 ARTIST가 아니거나 사유가 비어 있으면 권한을 부여하지 않는다")
    void updateRole_invalidBody_rejectsWithoutChange(String body) throws Exception {
        // When & Then
        updateRole(userId, body).andExpect(status().isBadRequest());
        assertThat(findRole()).isEqualTo("USER");
        assertThat(countAudits()).isZero();
    }

    @Test
    @DisplayName("500자를 초과하는 사유로는 작가 권한을 부여하지 않는다")
    void updateRole_reasonTooLong_rejectsWithoutChange() throws Exception {
        // Given
        String body = "{\"role\":\"ARTIST\",\"reason\":\"" + "가".repeat(501) + "\"}";

        // When & Then
        updateRole(userId, body).andExpect(status().isBadRequest());
        assertThat(findRole()).isEqualTo("USER");
        assertThat(countAudits()).isZero();
    }

    @Test
    @DisplayName("탈퇴한 회원에게는 작가 권한을 부여할 수 없다")
    void updateRole_withdrawnUser_rejectsWithoutChange() throws Exception {
        // Given
        jdbcTemplate.update("UPDATE users SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?", userId);

        // When & Then
        updateRole(userId, GRANT).andExpect(status().isBadRequest());
        assertThat(findRole()).isEqualTo("USER");
        assertThat(countAudits()).isZero();
    }

    @Test
    @DisplayName("존재하지 않는 회원에게 권한을 부여하면 404를 반환한다")
    void updateRole_unknownUser_returnsNotFound() throws Exception {
        // When & Then
        updateRole(UUID.randomUUID(), GRANT).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("일반 회원 토큰으로는 관리자 역할 변경에 접근할 수 없다")
    void updateRole_memberToken_returnsForbidden() throws Exception {
        // When & Then
        mockMvc.perform(put(PATH, userId)
                .header(HttpHeaders.AUTHORIZATION, memberToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(GRANT))
                .andExpect(status().isForbidden());
        assertThat(findRole()).isEqualTo("USER");
        assertThat(countAudits()).isZero();
    }

    @Test
    @DisplayName("감사 로그 저장이 DB 제약으로 실패하면 작가 권한 부여도 롤백된다")
    void updateRole_auditSaveFails_rollsBackRoleChange() {
        // When & Then
        assertThatThrownBy(() -> roleService.updateRole(
                userId,
                UUID.randomUUID(),
                UserRole.ARTIST,
                "운영팀 추천"
        )).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(findRole()).isEqualTo("USER");
        assertThat(countAudits()).isZero();
    }

    @Test
    @DisplayName("OpenAPI에 작가 역할 부여 요청과 응답이 나온다")
    void userApiDocs_updateRole_exposesContract() throws Exception {
        // Given
        String operation = "$.paths['" + PATH + "'].put";
        String requestSchema = "$.components.schemas.AdminUserRoleUpdateRequest";

        // When & Then
        mockMvc.perform(get("/v3/api-docs/admin-api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        operation + ".requestBody.content['application/json'].schema['$ref']")
                        .value("#/components/schemas/AdminUserRoleUpdateRequest"))
                .andExpect(
                        jsonPath(requestSchema + ".properties.role.enum").value(contains("ARTIST")))
                .andExpect(jsonPath(operation + ".responses['200'].content['*/*'].schema['$ref']")
                        .value("#/components/schemas/AdminUserRoleResponse"));
    }

    private ResultActions updateRole(UUID targetId, String body) throws Exception {
        String token = accessTokenProvider.issue(adminId, AccessTokenScope.ADMIN).value();
        return mockMvc.perform(put(PATH, targetId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private String memberToken() {
        return "Bearer " + accessTokenProvider.issueForSession(userId, UUID.randomUUID()).value();
    }

    private String findRole() {
        return jdbcTemplate.queryForObject(
                "SELECT role FROM users WHERE id = ?",
                String.class,
                userId
        );
    }

    private int countAudits() {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM admin_audit_logs WHERE target_id = ?",
                Integer.class,
                userId
        );
    }
}
