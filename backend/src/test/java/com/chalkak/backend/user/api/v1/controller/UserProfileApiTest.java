package com.chalkak.backend.user.api.v1.controller;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chalkak.backend.auth.infrastructure.infra.token.access.JwtAccessTokenProvider;
import com.chalkak.backend.support.IntegrationTestSupport;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@AutoConfigureMockMvc
class UserProfileApiTest extends IntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtAccessTokenProvider accessTokenProvider;

    @Test
    @DisplayName("역할과 작가 동의가 입력되지 않은 회원은 일반 회원과 미동의로 조회된다")
    void getMyProfile_defaultRole_returnsUserWithoutAgreement() throws Exception {
        // Given
        UUID userId = insertUser();

        // When & Then
        mockMvc.perform(
                get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, bearerToken(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.artistTermsAgreed").value(false));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @DisplayName("작가의 동의 여부는 저장된 동의 시각을 반영한다")
    void getMyProfile_artist_returnsStoredAgreement(boolean agreed) throws Exception {
        // Given
        UUID userId = insertUser();
        jdbcTemplate.update(
                """
                        UPDATE users
                        SET role = 'ARTIST', artist_agreed_at = CASE WHEN ? THEN CURRENT_TIMESTAMP END
                        WHERE id = ?
                        """,
                agreed,
                userId
        );

        // When & Then
        mockMvc.perform(
                get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, bearerToken(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ARTIST"))
                .andExpect(jsonPath("$.artistTermsAgreed").value(agreed));
    }

    @Test
    @DisplayName("정지 회원은 본인의 역할과 동의 여부를 조회할 수 있다")
    void getMyProfile_suspendedUser_returnsProfile() throws Exception {
        // Given
        UUID userId = insertUser();
        jdbcTemplate.update("UPDATE users SET status = 'BANNED' WHERE id = ?", userId);

        // When & Then
        mockMvc.perform(
                get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, bearerToken(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.artistTermsAgreed").value(false));
    }

    @Test
    @DisplayName("탈퇴 회원은 남은 토큰으로 본인 정보를 조회할 수 없다")
    void getMyProfile_withdrawnUser_returnsUnauthorized() throws Exception {
        // Given
        UUID userId = insertUser();
        String token = bearerToken(userId);
        jdbcTemplate.update("UPDATE users SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?", userId);

        // When & Then
        mockMvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("미인증 요청은 본인 정보를 조회할 수 없다")
    void getMyProfile_unauthenticated_returnsUnauthorized() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("사용자 OpenAPI 문서에 인증과 회원 역할 및 동의 응답이 나온다")
    void userApiDocs_getMyProfile_exposesProfileContract() throws Exception {
        // Given
        String operation = "$.paths['/api/v1/users/me'].get";
        String properties = "$.components.schemas.UserProfileResponse.properties";

        // When & Then
        mockMvc.perform(get("/v3/api-docs/user-api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(operation + ".security[0].accessToken").exists())
                .andExpect(jsonPath(operation + ".responses['200'].content['*/*'].schema['$ref']")
                        .value("#/components/schemas/UserProfileResponse"))
                .andExpect(jsonPath(properties + ".role.enum")
                        .value(containsInAnyOrder("USER", "ARTIST")))
                .andExpect(jsonPath(properties + ".artistTermsAgreed.type").value("boolean"));
    }

    private UUID insertUser() {
        UUID userId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                        INSERT INTO users (id, email, status, signature_original_storage_key)
                        VALUES (?, ?, 'ACTIVE', 'chalkak/signatures/original/user-profile.png')
                        """,
                userId,
                userId + "@chalkak.test"
        );
        return userId;
    }

    private String bearerToken(UUID userId) {
        return "Bearer " + accessTokenProvider.issueForSession(userId, UUID.randomUUID()).value();
    }
}
