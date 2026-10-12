package com.chalkak.backend.admin.api.v1.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chalkak.backend.admin.domain.Admin;
import com.chalkak.backend.admin.repository.auth.AdminRepository;
import com.chalkak.backend.auth.domain.AccessTokenScope;
import com.chalkak.backend.auth.infrastructure.infra.access.JwtAccessTokenProvider;
import com.chalkak.backend.notification.domain.TopicPushSchedule;
import com.chalkak.backend.support.IntegrationTestSupport;
import jakarta.persistence.EntityManager;
import java.time.LocalTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
class AdminTopicPushScheduleApiTest extends IntegrationTestSupport {

    private static final String PATH = "/api/v1/admin/topic-push-schedules";

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

    private String adminToken;

    @BeforeEach
    void setUp() {
        Admin admin = adminRepository.save(Admin.create("push-schedule-api-manager",
                passwordEncoder.encode("test-password")));
        entityManager.flush();
        adminToken = accessTokenProvider.issue(admin.getId(), AccessTokenScope.ADMIN).value();
    }

    @ParameterizedTest
    @ValueSource(strings = {"09:00:01", "09:00:00.000000001"})
    @DisplayName("초 또는 소수 초가 있는 생성 요청은 400으로 거절하고 설정·감사 기록을 저장하지 않는다")
    void createSchedule_nonMinuteSendTime_returnsBadRequestWithoutPersisting(String time)
            throws Exception {
        // When & Then
        mockMvc.perform(post(PATH)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sendTime\":\"" + time + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_ERROR"))
                .andExpect(jsonPath("$.message")
                        .value("주제 푸시 발송 시각은 분 단위의 KST 시각이어야 합니다."));
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM topic_push_schedules",
                Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM admin_audit_logs",
                Integer.class)).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"09:00:01", "09:00:00.000000001"})
    @DisplayName("초 또는 소수 초가 있는 수정 요청은 400으로 거절하고 기존 시각·감사 기록을 유지한다")
    void updateSchedule_nonMinuteSendTime_returnsBadRequestPreservingState(String time)
            throws Exception {
        // Given
        TopicPushSchedule schedule = new TopicPushSchedule(LocalTime.of(9, 0));
        entityManager.persist(schedule);
        entityManager.flush();

        // When & Then
        mockMvc.perform(put(PATH + "/" + schedule.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sendTime\":\"" + time + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_ERROR"))
                .andExpect(jsonPath("$.message")
                        .value("주제 푸시 발송 시각은 분 단위의 KST 시각이어야 합니다."));
        entityManager.clear();
        assertThat(entityManager.find(TopicPushSchedule.class, schedule.getId()).getSendTime())
                .isEqualTo(LocalTime.of(9, 0));
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM admin_audit_logs",
                Integer.class)).isZero();
    }
}
