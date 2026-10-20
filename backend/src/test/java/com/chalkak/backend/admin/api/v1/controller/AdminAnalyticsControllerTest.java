package com.chalkak.backend.admin.api.v1.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chalkak.backend.admin.api.support.AdminActorResolver;
import com.chalkak.backend.admin.api.support.AdminArgumentResolverWebMvcConfig;
import com.chalkak.backend.admin.api.support.AuthenticatedAdmin;
import com.chalkak.backend.analytics.service.UserMetricsAggregationRunner;
import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.exception.GlobalExceptionHandler;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AdminAnalyticsController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({
        GlobalExceptionHandler.class,
        AdminArgumentResolverWebMvcConfig.class
})
class AdminAnalyticsControllerTest {

    private static final UUID ADMIN_ID = UUID.fromString("0198fd21-0000-7000-8000-000000000001");
    private static final String PATH = "/api/v1/admin/analytics/aggregations";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserMetricsAggregationRunner aggregationRunner;

    @MockitoBean
    private AdminActorResolver adminActorResolver;

    @BeforeEach
    void setUp() {
        given(adminActorResolver.resolve()).willReturn(new AuthenticatedAdmin(ADMIN_ID));
    }

    @Test
    @DisplayName("관리자가 날짜를 보내면 본문 없는 202로 응답하고 러너를 시작한다")
    void startUserMetricsAggregation_validDate_returnsAcceptedWithoutBody() throws Exception {
        // When & Then
        mockMvc.perform(post(PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"metricDate":"2030-01-02"}
                        """))
                .andExpect(status().isAccepted())
                .andExpect(content().string(""));

        then(aggregationRunner).should().start(ADMIN_ID, LocalDate.of(
                2030,
                1,
                2
        ));
    }

    @Test
    @DisplayName("날짜가 없거나 형식이 틀리면 400으로 거절하고 러너를 시작하지 않는다")
    void startUserMetricsAggregation_missingOrMalformedDate_returnsBadRequest() throws Exception {
        // When & Then
        mockMvc.perform(post(PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_ERROR"));
        mockMvc.perform(post(PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"metricDate":"2030/01/02"}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_ERROR"));

        then(aggregationRunner).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("러너가 아직 끝나지 않은 날짜로 거절하면 BUSINESS_ERROR 400으로 변환한다")
    void startUserMetricsAggregation_unfinishedDay_returnsBadRequest() throws Exception {
        // Given
        doThrow(new BusinessException(
                ErrorCode.BUSINESS_ERROR, "아직 끝나지 않은 날짜는 집계할 수 없습니다."))
                .when(aggregationRunner).start(ADMIN_ID, LocalDate.of(
                        2030,
                        1,
                        3
                ));

        // When & Then
        mockMvc.perform(post(PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"metricDate":"2030-01-03"}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_ERROR"))
                .andExpect(jsonPath("$.message").value("아직 끝나지 않은 날짜는 집계할 수 없습니다."));
    }

    @Test
    @DisplayName("러너가 진행 중이라고 거절하면 USER_METRICS_AGGREGATION_IN_PROGRESS 400으로 변환한다")
    void startUserMetricsAggregation_inProgress_returnsInProgressErrorCode() throws Exception {
        // Given
        doThrow(new BusinessException(
                ErrorCode.USER_METRICS_AGGREGATION_IN_PROGRESS,
                "이미 사용자 지표 집계가 진행 중입니다."))
                .when(aggregationRunner).start(ADMIN_ID, LocalDate.of(
                        2030,
                        1,
                        2
                ));

        // When & Then
        mockMvc.perform(post(PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"metricDate":"2030-01-02"}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("USER_METRICS_AGGREGATION_IN_PROGRESS"))
                .andExpect(jsonPath("$.message").value("이미 사용자 지표 집계가 진행 중입니다."));
    }
}
