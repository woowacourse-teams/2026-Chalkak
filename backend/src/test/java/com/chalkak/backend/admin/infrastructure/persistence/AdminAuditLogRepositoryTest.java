package com.chalkak.backend.admin.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chalkak.backend.admin.domain.Admin;
import com.chalkak.backend.admin.domain.AdminAction;
import com.chalkak.backend.admin.domain.AdminAuditLog;
import com.chalkak.backend.admin.domain.AdminAuditSnapshot;
import com.chalkak.backend.admin.domain.AdminTargetType;
import com.chalkak.backend.admin.repository.AdminAuditLogRepository;
import jakarta.persistence.EntityManager;
import java.lang.reflect.Modifier;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(AdminAuditLogRepositoryImpl.class)
class AdminAuditLogRepositoryTest {

    private static final String PASSWORD_HASH =
            "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";
    private static final Instant OCCURRED_AT = Instant.parse("2026-08-27T11:20:00Z");

    @Autowired
    private AdminAuditLogRepository adminAuditLogRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("JPA 감사 로그 저장소는 같은 패키지에 저장 기능만 노출한다")
    void repository_appendOnlySurface_exposesOnlySaveWithinPackage() {
        // When
        boolean publicRepository = Modifier.isPublic(
                AdminAuditLogJpaRepository.class.getModifiers()
        );
        List<String> exposedMethods = Arrays.stream(
                        AdminAuditLogJpaRepository.class.getMethods()
                )
                .map(method -> method.getName())
                .distinct()
                .toList();

        // Then
        assertThat(publicRepository).isFalse();
        assertThat(exposedMethods).containsExactly("save");
    }

    @Test
    @DisplayName("감사 로그의 UUIDv7과 PostgreSQL enum·JSONB 필드를 저장한다")
    void save_validAuditLog_persistsPostgresTypes() {
        // Given
        UUID actorAdminId = persistAdmin();
        AdminAuditLog auditLog = createAuditLog(
                actorAdminId,
                UUID.randomUUID(),
                UUID.randomUUID()
        );

        // When
        AdminAuditLog savedAuditLog = adminAuditLogRepository.save(auditLog);
        entityManager.flush();
        UUID auditLogId = savedAuditLog.getId();
        entityManager.clear();

        // Then
        AdminAuditLog foundAuditLog = entityManager.find(AdminAuditLog.class, auditLogId);
        assertThat(auditLogId.version()).isEqualTo(7);
        assertThat(foundAuditLog.getActorAdminId()).isEqualTo(actorAdminId);
        assertThat(foundAuditLog.getAction()).isEqualTo(AdminAction.POST_APPROVED);
        assertThat(foundAuditLog.getTargetType()).isEqualTo(AdminTargetType.POST);
        assertThat(foundAuditLog.getBeforeState()).containsEntry("moderationStatus", "PENDING");
        assertThat(foundAuditLog.getAfterState()).containsEntry("moderationStatus", "APPROVED");
        assertThat(foundAuditLog.getOccurredAt()).isEqualTo(OCCURRED_AT);
        assertThat(foundAuditLog.getRequestId()).isEqualTo(auditLog.getRequestId());
    }

    @Test
    @DisplayName("한 요청에서 여러 관리자 작업을 기록할 수 있다")
    void save_sameRequestId_persistsMultipleAuditLogs() {
        // Given
        UUID actorAdminId = persistAdmin();
        UUID requestId = UUID.randomUUID();

        // When
        adminAuditLogRepository.save(createAuditLog(
                actorAdminId,
                UUID.randomUUID(),
                requestId
        ));
        adminAuditLogRepository.save(createAuditLog(
                actorAdminId,
                UUID.randomUUID(),
                requestId
        ));
        entityManager.flush();

        // Then
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM admin_audit_logs WHERE request_id = ?",
                Integer.class,
                requestId
        );
        assertThat(count).isEqualTo(2);
    }

    @Test
    @DisplayName("존재하지 않는 관리자는 감사 로그 작업자가 될 수 없다")
    void save_unknownActorAdminId_throwsDataIntegrityViolationException() {
        // Given
        AdminAuditLog auditLog = createAuditLog(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID()
        );

        // When & Then
        assertThatThrownBy(() -> {
            adminAuditLogRepository.save(auditLog);
            entityManager.flush();
        })
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasRootCauseInstanceOf(SQLException.class);
    }

    @Test
    @DisplayName(
            "감사 로그 목록과 작업자·대상·동작·요청 "
                    + "조회용 인덱스를 제공한다"
    )
    void migration_adminAuditLogs_hasQueryIndexes() {
        // When
        List<String> indexNames = jdbcTemplate.queryForList("""
                SELECT indexname
                FROM pg_indexes
                WHERE schemaname = current_schema()
                  AND tablename = 'admin_audit_logs'
                """, String.class);

        // Then
        assertThat(indexNames).contains(
                "ix_admin_audit_logs_occurred_at_id",
                "ix_admin_audit_logs_actor_occurred_at_id",
                "ix_admin_audit_logs_action_occurred_at_id",
                "ix_admin_audit_logs_target_occurred_at_id",
                "ix_admin_audit_logs_request_id"
        );
    }

    private UUID persistAdmin() {
        Admin admin = Admin.create("audit-admin-" + UUID.randomUUID(), PASSWORD_HASH);
        entityManager.persist(admin);
        entityManager.flush();
        return admin.getId();
    }

    private AdminAuditLog createAuditLog(
            UUID actorAdminId,
            UUID targetId,
            UUID requestId
    ) {
        Map<String, Object> beforeState = new LinkedHashMap<>();
        beforeState.put("moderationStatus", "PENDING");
        beforeState.put("moderatedAt", null);
        return AdminAuditLog.create(
                actorAdminId,
                AdminAction.POST_APPROVED,
                AdminTargetType.POST,
                targetId,
                null,
                AdminAuditSnapshot.from(beforeState),
                AdminAuditSnapshot.from(Map.of(
                        "moderationStatus", "APPROVED",
                        "moderatedAt", OCCURRED_AT,
                        "moderatedBy", actorAdminId
                )),
                OCCURRED_AT,
                requestId
        );
    }
}
