package com.chalkak.backend.notification.api.v1.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chalkak.backend.auth.domain.AccessTokenScope;
import com.chalkak.backend.auth.infrastructure.infra.access.JwtAccessTokenProvider;
import com.chalkak.backend.support.IntegrationTestSupport;
import com.chalkak.backend.user.domain.User;
import com.chalkak.backend.user.domain.UserFixture;
import com.chalkak.backend.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@AutoConfigureMockMvc
class NotificationSettingsAccessTest extends IntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtAccessTokenProvider accessTokenProvider;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    @DisplayName("인증 없는 수신 설정 조회는 필터에서 401로 차단한다")
    void getSettings_unauthenticated_returnsUnauthorized() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/v1/notification-settings"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("관리자 JWT로는 회원 수신 설정을 조회할 수 없다")
    void getSettings_adminToken_returnsForbidden() throws Exception {
        // Given
        String authorization = "Bearer " + accessTokenProvider
                .issue(UUID.randomUUID(), AccessTokenScope.ADMIN).value();

        // When & Then
        mockMvc.perform(get("/api/v1/notification-settings")
                .header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("로그인 ID 없는 JWT도 본인 회원의 설정만 조회한다")
    void getSettings_legacyTokens_returnsEachUsersOwnSettings() throws Exception {
        // Given
        User first = userRepository.save(UserFixture.create());
        first.updatePushPreferences(false, true);
        User second = userRepository.save(UserFixture.create());
        second.updatePushPreferences(true, false);

        // When & Then
        mockMvc.perform(get("/api/v1/notification-settings")
                .header(HttpHeaders.AUTHORIZATION, authorization(first)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.topicPushEnabled").value(false))
                .andExpect(jsonPath("$.moderationPushEnabled").value(true));
        mockMvc.perform(get("/api/v1/notification-settings")
                .header(HttpHeaders.AUTHORIZATION, authorization(second)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.topicPushEnabled").value(true))
                .andExpect(jsonPath("$.moderationPushEnabled").value(false));
    }

    @Test
    @DisplayName("인증 없는 수신 설정 수정은 401로 차단한다")
    void updateSettings_unauthenticated_returnsUnauthorized() throws Exception {
        // When & Then
        mockMvc.perform(patch("/api/v1/notification-settings")
                .contentType(MediaType.APPLICATION_JSON).content("{\"topicPushEnabled\":false}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("관리자 JWT로는 회원 수신 설정을 수정할 수 없다")
    void updateSettings_adminToken_returnsForbidden() throws Exception {
        // Given
        String authorization = "Bearer " + accessTokenProvider
                .issue(UUID.randomUUID(), AccessTokenScope.ADMIN).value();

        // When & Then
        mockMvc.perform(patch("/api/v1/notification-settings")
                .header(HttpHeaders.AUTHORIZATION, authorization)
                .contentType(MediaType.APPLICATION_JSON).content("{\"topicPushEnabled\":false}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("로그인 ID 없는 JWT도 본인 설정만 수정하고 다른 회원 설정은 유지한다")
    void updateSettings_legacyToken_changesOnlyOwnAccount() throws Exception {
        // Given
        User first = userRepository.save(UserFixture.create());
        User second = userRepository.save(UserFixture.create());
        entityManager.flush();
        entityManager.clear();

        // When
        mockMvc.perform(patch("/api/v1/notification-settings")
                .header(HttpHeaders.AUTHORIZATION, authorization(first))
                .contentType(MediaType.APPLICATION_JSON).content("{\"topicPushEnabled\":false}"))
                .andExpect(status().isNoContent());
        entityManager.flush();
        entityManager.clear();

        // Then
        mockMvc.perform(get("/api/v1/notification-settings")
                .header(HttpHeaders.AUTHORIZATION, authorization(first)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.topicPushEnabled").value(false))
                .andExpect(jsonPath("$.moderationPushEnabled").value(true));
        User unchanged = userRepository.findById(second.getId()).orElseThrow();
        assertThat(unchanged.isTopicPushEnabled()).isTrue();
        assertThat(unchanged.isModerationPushEnabled()).isTrue();
    }

    private String authorization(User user) {
        return "Bearer " + accessTokenProvider.issue(user.getId(), AccessTokenScope.USER).value();
    }
}
