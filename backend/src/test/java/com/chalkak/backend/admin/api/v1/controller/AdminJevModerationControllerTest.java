package com.chalkak.backend.admin.api.v1.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chalkak.backend.admin.api.support.AdminActorResolver;
import com.chalkak.backend.admin.api.support.AdminArgumentResolverWebMvcConfig;
import com.chalkak.backend.admin.api.support.AuthenticatedAdmin;
import com.chalkak.backend.admin.service.jev.AdminJevEvaluationService;
import com.chalkak.backend.admin.service.jev.AdminJevMetricsService;
import com.chalkak.backend.admin.service.jev.AdminJevQueryService;
import com.chalkak.backend.admin.service.post.AdminPostCorrectionService;
import com.chalkak.backend.admin.service.post.AdminPostModerationResult;
import com.chalkak.backend.exception.GlobalExceptionHandler;
import com.chalkak.backend.jev.domain.JevDecision;
import com.chalkak.backend.jev.domain.JevGoldLabel;
import com.chalkak.backend.jev.repository.JevRunFilter;
import com.chalkak.backend.post.domain.ModerationStatus;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest({AdminJevModerationController.class, AdminPostCorrectionController.class})
@AutoConfigureMockMvc(addFilters = false)
@Import({GlobalExceptionHandler.class, AdminArgumentResolverWebMvcConfig.class})
class AdminJevModerationControllerTest {

    @Autowired
    private MockMvc mvc;
    @MockitoBean
    private AdminActorResolver actors;
    @MockitoBean
    private AdminJevQueryService query;
    @MockitoBean
    private AdminJevEvaluationService evaluations;
    @MockitoBean
    private AdminJevMetricsService metrics;
    @MockitoBean
    private AdminPostCorrectionService corrections;

    @Test
    @DisplayName("목록은 페이지 기본값으로 조회하고 원문 없는 요약 배열을 반환한다")
    void list_defaultPage_returnsSummary() throws Exception {
        // Given
        given(query.list(
                any(),
                eq(0),
                eq(20)
        )).willReturn(new AdminJevQueryService.History(List.of(), false));
        // When & Then
        mvc.perform(get("/api/v1/admin/jev-moderation-runs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows").isEmpty())
                .andExpect(jsonPath("$.hasNext").value(false));
        verify(query).list(
                new JevRunFilter(
                        null,
                        null,
                        null,
                        null
                ),
                0,
                20
        );
    }

    @Test
    @DisplayName("허용 페이지 크기를 넘으면 조회하지 않는다")
    void list_oversizedPage_returnsBadRequest() throws Exception {
        // When & Then
        mvc.perform(get("/api/v1/admin/jev-moderation-runs?pageSize=101"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(query);
    }

    @Test
    @DisplayName("평가 근거가 없으면 정답을 저장하지 않는다")
    void evaluate_missingReason_returnsBadRequest() throws Exception {
        // Given
        given(actors.resolve()).willReturn(new AuthenticatedAdmin(UUID.randomUUID()));
        // When & Then
        mvc.perform(put("/api/v1/admin/jev-moderation-runs/" + UUID.randomUUID() + "/evaluation")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"decision":"APPROVE","questions":{},"sampling":"RANDOM"}
                        """))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(evaluations);
    }

    @Test
    @DisplayName("평가 응답은 현재 정답과 평가자/근거/시각을 반환한다")
    void evaluate_validRequest_returnsGoldLabel() throws Exception {
        // Given
        UUID admin = UUID.randomUUID();
        UUID run = UUID.randomUUID();
        given(actors.resolve()).willReturn(new AuthenticatedAdmin(admin));
        given(evaluations.evaluate(
                eq(run),
                eq(admin),
                eq(JevDecision.APPROVE),
                eq(Map.of()),
                eq("사진 확인"),
                eq(JevGoldLabel.Sampling.RANDOM)
        )).willReturn(new JevGoldLabel(
                JevDecision.APPROVE,
                Map.of(),
                "사진 확인",
                JevGoldLabel.Sampling.RANDOM,
                admin,
                Instant.parse("2026-11-20T01:00:00Z")
        ));
        // When & Then
        mvc.perform(put("/api/v1/admin/jev-moderation-runs/" + run + "/evaluation")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"decision":"APPROVE","questions":{},"reason":"사진 확인","sampling":"RANDOM"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.evaluatedBy").value(admin.toString()))
                .andExpect(jsonPath("$.questions").isEmpty())
                .andExpect(jsonPath("$.decision").value("APPROVE"));
    }

    @Test
    @DisplayName("승인 정정도 필수 사유와 관리자 식별자를 전달한다")
    void correct_approval_passesReason() throws Exception {
        // Given
        UUID admin = UUID.randomUUID();
        UUID post = UUID.randomUUID();
        given(actors.resolve()).willReturn(new AuthenticatedAdmin(admin));
        given(corrections.correct(
                post,
                admin,
                ModerationStatus.APPROVED,
                "재확인 완료"
        )).willReturn(new AdminPostModerationResult(
                post,
                ModerationStatus.APPROVED,
                admin,
                Instant.parse("2026-11-20T01:00:00Z"),
                null
        ));
        // When & Then
        mvc.perform(put("/api/v1/admin/posts/" + post + "/moderation-correction")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"status":"APPROVED","reason":"재확인 완료"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.moderationStatus").value("APPROVED"));
    }
}
