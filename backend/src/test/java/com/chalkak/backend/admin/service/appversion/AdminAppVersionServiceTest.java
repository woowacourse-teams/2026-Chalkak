package com.chalkak.backend.admin.service.appversion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chalkak.backend.admin.domain.AdminAction;
import com.chalkak.backend.admin.domain.AdminTargetType;
import com.chalkak.backend.admin.service.audit.AdminAuditLogQueryService;
import com.chalkak.backend.appversion.domain.AppPlatform;
import com.chalkak.backend.appversion.service.AppVersionService;
import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.support.IntegrationTestSupport;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class AdminAppVersionServiceTest extends IntegrationTestSupport {
    private static final UUID ADMIN = UUID.fromString("019a0000-0000-7000-8000-000000000848");
    private static final String URL = "https://play.google.com/store/apps/details?id=com.example.chalkak";
    @Autowired
    private AdminAppVersionService service;
    @Autowired
    private AppVersionService publicService;
    @Autowired
    private AdminAuditLogQueryService auditQuery;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private EntityManager em;

    @BeforeEach
    void setUp() {
        jdbc.update(
                "INSERT INTO admins (id, username, password, created_at, updated_at) VALUES (?, 'app-version-manager', 'password', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                ADMIN);
    }

    @Test
    @DisplayName("미설정 두 플랫폼은 null 설정으로 조회하고 앱의 이용을 허용한다")
    void getAppVersions_unconfigured_returnsBothPlatforms() {
        // When
        var settings = service.getAppVersions();
        // Then
        assertThat(settings).extracting(AdminAppVersionService.SettingResult::platform)
                .containsExactly(AppPlatform.ANDROID, AppPlatform.IOS);
        assertThat(settings).allSatisfy(setting -> {
            assertThat(setting.minimumSupportedVersion()).isNull();
            assertThat(setting.storeUrl()).isNull();
            assertThat(setting.revision()).isNull();
            assertThat(setting.lastChangedBy()).isNull();
            assertThat(setting.changeReason()).isNull();
            assertThat(setting.createdAt()).isNull();
            assertThat(setting.updatedAt()).isNull();
        });
        assertThat(publicService.getAppVersion(AppPlatform.ANDROID).minimumSupportedVersion())
                .isNull();
    }

    @Test
    @DisplayName("최초 저장·버전 하향·주소 변경을 즉시 공개 조회에 반영하고 전후 값과 관리자를 감사 기록으로 조회한다")
    void saveAppVersion_createAndUpdate_persistsSettingsAndAudits() {
        // Given
        var created = save(
                null,
                "2.0.0",
                URL
        );
        em.flush();
        em.clear();
        var lowered = save(
                created.revision(),
                "1.10.0",
                URL
        );
        em.flush();
        em.clear();
        // When
        var changedUrl = save(
                lowered.revision(),
                "1.10.0",
                URL + "&hl=ko"
        );
        em.flush();
        em.clear();
        // Then
        assertThat(changedUrl.revision()).isEqualTo(2L);
        assertThat(changedUrl.createdAt()).isEqualTo(created.createdAt());
        assertThat(changedUrl.updatedAt()).isAfter(created.updatedAt());
        assertThat(changedUrl.lastChangedBy()).isEqualTo(ADMIN);
        assertThat(changedUrl.changeReason()).isEqualTo("출시 확인");
        assertThat(publicService.getAppVersion(AppPlatform.ANDROID).minimumSupportedVersion())
                .isEqualTo("1.10.0");
        assertThat(publicService.getAppVersion(AppPlatform.ANDROID).storeUrl())
                .isEqualTo(URL + "&hl=ko");
        assertThat(publicService.getAppVersion(AppPlatform.IOS).minimumSupportedVersion()).isNull();
        var logs = auditQuery.getAuditLogs(
                ADMIN,
                null,
                AdminTargetType.APP_VERSION,
                null,
                null,
                null,
                null,
                1,
                20
        ).auditLogs();
        assertThat(logs).hasSize(3);
        assertThat(logs.get(0).action()).isEqualTo(AdminAction.APP_VERSION_UPDATED);
        assertThat(logs.get(0).beforeState()).containsEntry("storeUrl", URL)
                .containsEntry("revision", 1);
        assertThat(logs.get(0).afterState()).containsEntry("storeUrl", URL + "&hl=ko")
                .containsEntry("revision", 2);
        assertThat(logs.get(2).action()).isEqualTo(AdminAction.APP_VERSION_CREATED);
        assertThat(logs.get(2).beforeState()).isEmpty();
        assertThat(logs).allSatisfy(log -> {
            assertThat(log.actorAdminId()).isEqualTo(ADMIN);
            assertThat(log.reason()).isEqualTo("출시 확인");
            assertThat(log.occurredAt()).isNotNull();
            assertThat(log.requestId()).isNotNull();
        });
    }

    @Test
    @DisplayName("같은 값의 오래된 재시도는 이력·시각·사유·revision을 바꾸지 않는다")
    void saveAppVersion_identicalRetry_preservesMetadata() {
        // Given
        var first = save(
                null,
                "1.0.0",
                URL
        );
        em.flush();
        em.clear();
        // When
        var retry = service.saveAppVersion(
                ADMIN,
                AppPlatform.ANDROID,
                null,
                "1.0.0",
                " " + URL + " ",
                "다른 사유"
        );
        em.flush();
        em.clear();
        // Then
        assertThat(retry).isEqualTo(first);
        assertThat(service.getAppVersions().get(0)).isEqualTo(first);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM admin_audit_logs WHERE target_type='APP_VERSION'",
                Integer.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("최초 저장의 잘못된 revision과 수정의 오래된 revision은 상태 충돌로 거절한다")
    void saveAppVersion_wrongRevision_rejectsWithoutChanges() {
        // When & Then
        assertConflict(() -> save(
                0L,
                "1.0.0",
                URL
        ));
        var first = save(
                null,
                "1.0.0",
                URL
        );
        assertConflict(() -> save(
                9L,
                "2.0.0",
                URL
        ));
        assertThat(service.getAppVersions().get(0)).isEqualTo(first);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM admin_audit_logs WHERE target_type='APP_VERSION'",
                Integer.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("관리자와 플랫폼이 없으면 저장을 거절한다")
    void saveAppVersion_missingActorOrPlatform_rejects() {
        // When & Then
        assertThatThrownBy(() -> service.saveAppVersion(
                null,
                AppPlatform.ANDROID,
                null,
                "1.0.0",
                URL,
                "사유"
        )).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.saveAppVersion(
                ADMIN,
                null,
                null,
                "1.0.0",
                URL,
                "사유"
        )).isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("보조 문자 500자의 사유를 저장하고 같은 code point 길이로 조회한다")
    void saveAppVersion_codePointReason_persistsBoundary() {
        // Given
        String reason = "😀".repeat(500);
        // When
        var result = service.saveAppVersion(
                ADMIN,
                AppPlatform.ANDROID,
                null,
                "1.0.0",
                URL,
                "  " + reason + "  "
        );
        em.flush();
        em.clear();
        // Then
        assertThat(result.changeReason()).isEqualTo(reason);
        assertThat(service.getAppVersions().get(0).changeReason()).isEqualTo(reason);
        assertThat(jdbc.queryForObject("SELECT char_length(change_reason) FROM app_versions",
                Integer.class))
                .isEqualTo(500);
    }

    private AdminAppVersionService.SettingResult save(
            Long revision,
            String version,
            String url
    ) {
        return service.saveAppVersion(
                ADMIN,
                AppPlatform.ANDROID,
                revision,
                version,
                url,
                "  출시 확인  "
        );
    }

    private void assertConflict(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOfSatisfying(BusinessException.class,
                error -> assertThat(error.getErrorCode())
                        .isEqualTo(ErrorCode.RESOURCE_STATE_CHANGED));
    }
}
