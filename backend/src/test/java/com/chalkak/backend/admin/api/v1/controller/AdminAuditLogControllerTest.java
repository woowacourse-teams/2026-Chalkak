package com.chalkak.backend.admin.api.v1.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chalkak.backend.admin.api.support.AdminActorResolver;
import com.chalkak.backend.admin.api.support.AdminArgumentResolverWebMvcConfig;
import com.chalkak.backend.admin.api.support.AuthenticatedAdmin;
import com.chalkak.backend.admin.api.v1.converter.AdminAuditLogSortConverter;
import com.chalkak.backend.admin.domain.AdminAction;
import com.chalkak.backend.admin.domain.AdminTargetType;
import com.chalkak.backend.admin.service.AdminAuditLogListResult;
import com.chalkak.backend.admin.service.AdminAuditLogQueryService;
import com.chalkak.backend.admin.service.AdminAuditLogSort;
import com.chalkak.backend.exception.GlobalExceptionHandler;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AdminAuditLogController.class)
@Import({
        GlobalExceptionHandler.class,
        AdminArgumentResolverWebMvcConfig.class,
        AdminAuditLogSortConverter.class
})
class AdminAuditLogControllerTest {

    private static final UUID CURRENT_ADMIN_ID = UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6570f6");
    private static final UUID FILTER_ADMIN_ID = UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6570f7");
    private static final UUID TARGET_ID = UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6570d4");
    private static final UUID AUDIT_LOG_ID = UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6570a1");
    private static final UUID REQUEST_ID = UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6570b2");
    private static final Instant FROM = Instant.parse("2026-08-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-08-31T23:59:59Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminAuditLogQueryService service;

    @MockitoBean
    private AdminActorResolver adminActorResolver;

    @BeforeEach
    void setUp() {
        given(adminActorResolver.resolve()).willReturn(new AuthenticatedAdmin(CURRENT_ADMIN_ID));
    }

    @Test
    void getAuditLogs_allFilters_returnsAuditHistory() throws Exception {
        AdminAuditLogListResult result = new AdminAuditLogListResult(
                2, 50, false,
                List.of(new AdminAuditLogListResult.AuditLogSummary(
                        AUDIT_LOG_ID, FILTER_ADMIN_ID, "operator",
                        AdminAction.POST_REJECTED, AdminTargetType.POST, TARGET_ID,
                        "운영 정책 위반", Map.of("moderationStatus", "PENDING"),
                        Map.of("moderationStatus", "REJECTED"), TO, REQUEST_ID
                ))
        );
        given(service.getAuditLogs(
                FILTER_ADMIN_ID, AdminAction.POST_REJECTED, AdminTargetType.POST,
                TARGET_ID, FROM, TO, AdminAuditLogSort.OCCURRED_AT_ASC, 2, 50
        )).willReturn(result);

        mockMvc.perform(get("/api/v1/admin/audit-logs")
                        .queryParam("adminId", FILTER_ADMIN_ID.toString())
                        .queryParam("action", "POST_REJECTED")
                        .queryParam("targetType", "POST")
                        .queryParam("targetId", TARGET_ID.toString())
                        .queryParam("occurredFrom", FROM.toString())
                        .queryParam("occurredTo", TO.toString())
                        .queryParam("sort", "occurredAtAsc")
                        .queryParam("page", "2")
                        .queryParam("pageSize", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentPage").value(2))
                .andExpect(jsonPath("$.auditLogs[0].auditLogId").value(AUDIT_LOG_ID.toString()))
                .andExpect(jsonPath("$.auditLogs[0].actorAdminId").value(FILTER_ADMIN_ID.toString()))
                .andExpect(jsonPath("$.auditLogs[0].actorUsername").value("operator"))
                .andExpect(jsonPath("$.auditLogs[0].beforeState.moderationStatus").value("PENDING"))
                .andExpect(jsonPath("$.auditLogs[0].requestId").value(REQUEST_ID.toString()));

        then(service).should().getAuditLogs(
                FILTER_ADMIN_ID, AdminAction.POST_REJECTED, AdminTargetType.POST,
                TARGET_ID, FROM, TO, AdminAuditLogSort.OCCURRED_AT_ASC, 2, 50
        );
    }

    @Test
    void getAuditLogs_withoutFilters_usesDefaults() throws Exception {
        given(service.getAuditLogs(
                null, null, null, null, null, null,
                AdminAuditLogSort.OCCURRED_AT_DESC, 1, 20
        )).willReturn(new AdminAuditLogListResult(1, 20, false, List.of()));

        mockMvc.perform(get("/api/v1/admin/audit-logs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pageSize").value(20))
                .andExpect(jsonPath("$.auditLogs").isEmpty());
    }

    @Test
    void getAuditLogs_invalidPage_returnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/admin/audit-logs").queryParam("pageSize", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_ERROR"));
        then(service).shouldHaveNoInteractions();
    }

    @Test
    void getAuditLogs_unknownEnum_returnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/admin/audit-logs").queryParam("action", "PASSWORD_VIEWED"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_ERROR"));
        then(service).shouldHaveNoInteractions();
    }
}
