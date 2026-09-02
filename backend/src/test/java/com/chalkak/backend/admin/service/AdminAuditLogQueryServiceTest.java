package com.chalkak.backend.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.chalkak.backend.admin.domain.AdminAction;
import com.chalkak.backend.admin.domain.AdminTargetType;
import com.chalkak.backend.admin.repository.AdminAuditLogQueryCriteria;
import com.chalkak.backend.admin.repository.AdminAuditLogQueryPage;
import com.chalkak.backend.admin.repository.AdminAuditLogQueryRepository;
import com.chalkak.backend.admin.repository.AdminAuditLogQuerySort;
import com.chalkak.backend.admin.repository.AdminAuditLogSummaryProjection;
import com.chalkak.backend.exception.BusinessException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminAuditLogQueryServiceTest {

    private static final UUID ADMIN_ID = UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6570f6");
    private static final UUID TARGET_ID = UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6570d4");
    private static final Instant FROM = Instant.parse("2026-08-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-08-31T23:59:59Z");

    @Mock
    private AdminAuditLogQueryRepository repository;

    @InjectMocks
    private AdminAuditLogQueryService service;

    @Test
    void getAuditLogs_allFilters_returnsReadOnlyPage() {
        AdminAuditLogQueryCriteria criteria = new AdminAuditLogQueryCriteria(
                ADMIN_ID,
                AdminAction.POST_REJECTED,
                AdminTargetType.POST,
                TARGET_ID,
                FROM,
                TO,
                AdminAuditLogQuerySort.OCCURRED_AT_ASC
        );
        AdminAuditLogSummaryProjection projection = new AdminAuditLogSummaryProjection(
                UUID.randomUUID(), ADMIN_ID, "operator", AdminAction.POST_REJECTED,
                AdminTargetType.POST, TARGET_ID, "정책 위반",
                Map.of("moderationStatus", "PENDING"),
                Map.of("moderationStatus", "REJECTED"),
                TO, UUID.randomUUID()
        );
        given(repository.findAuditLogs(criteria, 2, 50))
                .willReturn(new AdminAuditLogQueryPage(List.of(projection), 2, 50, false));

        AdminAuditLogListResult result = service.getAuditLogs(
                ADMIN_ID, AdminAction.POST_REJECTED, AdminTargetType.POST, TARGET_ID,
                FROM, TO, AdminAuditLogSort.OCCURRED_AT_ASC, 2, 50
        );

        assertThat(result.auditLogs()).singleElement().satisfies(log -> {
            assertThat(log.actorUsername()).isEqualTo("operator");
            assertThat(log.beforeState()).containsEntry("moderationStatus", "PENDING");
            assertThat(log.afterState()).containsEntry("moderationStatus", "REJECTED");
        });
    }

    @Test
    void getAuditLogs_reversedPeriod_throwsBusinessException() {
        assertThatThrownBy(() -> service.getAuditLogs(
                null, null, null, null, TO, FROM,
                AdminAuditLogSort.OCCURRED_AT_DESC, 1, 20
        )).isInstanceOf(BusinessException.class);
    }

    @Test
    void getAuditLogs_excessiveOffset_throwsBusinessException() {
        assertThatThrownBy(() -> service.getAuditLogs(
                null, null, null, null, null, null,
                AdminAuditLogSort.OCCURRED_AT_DESC, Integer.MAX_VALUE, 100
        )).isInstanceOf(BusinessException.class);
    }
}
