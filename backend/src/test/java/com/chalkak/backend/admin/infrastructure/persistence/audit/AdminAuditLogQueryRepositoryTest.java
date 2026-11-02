package com.chalkak.backend.admin.infrastructure.persistence.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chalkak.backend.admin.domain.Admin;
import com.chalkak.backend.admin.domain.AdminAction;
import com.chalkak.backend.admin.domain.AdminAuditLog;
import com.chalkak.backend.admin.domain.AdminAuditSnapshot;
import com.chalkak.backend.admin.domain.AdminTargetType;
import com.chalkak.backend.admin.repository.AdminPage;
import com.chalkak.backend.admin.repository.audit.AdminAuditLogQueryCriteria;
import com.chalkak.backend.admin.repository.audit.AdminAuditLogRepository;
import com.chalkak.backend.admin.repository.audit.AdminAuditLogSort;
import com.chalkak.backend.admin.repository.audit.AdminAuditLogSummaryProjection;
import com.chalkak.backend.support.PostgresRepositoryTest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@PostgresRepositoryTest
class AdminAuditLogQueryRepositoryTest {

    private static final String PASSWORD_HASH = "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";
    private static final Instant OCCURRED_AT = Instant.parse("2026-08-27T11:20:00Z");

    @Autowired
    private AdminAuditLogRepository repository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("복합 필터는 기간 양 끝을 포함하고 삭제된 대상의 감사 이력도 조회한다")
    void findAuditLogs_combinedFiltersAndInclusiveBoundary_returnsMatchingDeletedTargetHistory() {
        Admin actor = persistAdmin("audit-reader");
        Admin otherActor = persistAdmin("other-reader");
        UUID deletedTargetId = UUID.randomUUID();
        persistApproved(
                actor.getId(),
                deletedTargetId,
                OCCURRED_AT
        );
        persistApproved(
                otherActor.getId(),
                deletedTargetId,
                OCCURRED_AT
        );
        persistApproved(
                actor.getId(),
                UUID.randomUUID(),
                OCCURRED_AT.minusSeconds(1)
        );

        AdminPage<AdminAuditLogSummaryProjection> result = repository.findAuditLogs(
                new AdminAuditLogQueryCriteria(
                        actor.getId(),
                        AdminAction.POST_APPROVED,
                        AdminTargetType.POST,
                        deletedTargetId,
                        OCCURRED_AT,
                        OCCURRED_AT,
                        AdminAuditLogSort.OCCURRED_AT_DESC
                ),
                1,
                20
        );

        assertThat(result.items()).singleElement().satisfies(log -> {
            assertThat(log.actorAdminId()).isEqualTo(actor.getId());
            assertThat(log.actorUsername()).isEqualTo("audit-reader");
            assertThat(log.targetId()).isEqualTo(deletedTargetId);
            assertThat(log.beforeState()).containsEntry("moderationStatus", "PENDING");
            assertThat(log.requestId()).isNotNull();
        });
    }

    @Test
    @DisplayName("발생 시각이 같은 이력은 ID 정렬로 페이지 경계를 유지한다")
    void findAuditLogs_sameOccurredAt_usesIdForStablePagination() {
        Admin actor = persistAdmin("stable-reader");
        List<UUID> ids = List.of(
                persistApproved(
                        actor.getId(),
                        UUID.randomUUID(),
                        OCCURRED_AT
                ).getId(),
                persistApproved(
                        actor.getId(),
                        UUID.randomUUID(),
                        OCCURRED_AT
                ).getId(),
                persistApproved(
                        actor.getId(),
                        UUID.randomUUID(),
                        OCCURRED_AT
                ).getId()
        ).stream()
                .sorted(Comparator.reverseOrder())
                .toList();
        AdminAuditLogQueryCriteria criteria = new AdminAuditLogQueryCriteria(
                null,
                null,
                null,
                null,
                null,
                null,
                AdminAuditLogSort.OCCURRED_AT_DESC
        );

        AdminPage<AdminAuditLogSummaryProjection> first = repository.findAuditLogs(
                criteria,
                1,
                2
        );
        AdminPage<AdminAuditLogSummaryProjection> second = repository.findAuditLogs(
                criteria,
                2,
                2
        );

        assertThat(first.items()).extracting(log -> log.auditLogId())
                .containsExactly(ids.get(0), ids.get(1));
        assertThat(first.hasNext()).isTrue();
        assertThat(second.items()).extracting(log -> log.auditLogId())
                .containsExactly(ids.get(2));
    }

    @Test
    @DisplayName("감사 이력이 있는 관리자는 외래키 제약으로 삭제할 수 없다")
    void deleteActor_withAuditHistory_isRejectedToPreserveActorIdentity() {
        Admin actor = persistAdmin("protected-reader");
        persistApproved(
                actor.getId(),
                UUID.randomUUID(),
                OCCURRED_AT
        );

        entityManager.remove(actor);

        assertThatThrownBy(entityManager::flush)
                .isInstanceOf(PersistenceException.class);
    }

    private Admin persistAdmin(String username) {
        Admin admin = Admin.create(username, PASSWORD_HASH);
        entityManager.persist(admin);
        entityManager.flush();
        return admin;
    }

    private AdminAuditLog persistApproved(
            UUID actorAdminId,
            UUID targetId,
            Instant occurredAt
    ) {
        Map<String, Object> beforeState = new LinkedHashMap<>();
        beforeState.put("moderationStatus", "PENDING");
        beforeState.put("moderatedAt", null);
        AdminAuditLog log = AdminAuditLog.create(
                actorAdminId,
                AdminAction.POST_APPROVED,
                AdminTargetType.POST,
                targetId,
                null,
                AdminAuditSnapshot.from(beforeState),
                AdminAuditSnapshot.from(Map.of(
                        "moderationStatus",
                        "APPROVED",
                        "moderatedAt",
                        occurredAt,
                        "moderatedBy",
                        actorAdminId
                )),
                occurredAt,
                UUID.randomUUID()
        );
        AdminAuditLog saved = repository.save(log);
        entityManager.flush();
        return saved;
    }
}
