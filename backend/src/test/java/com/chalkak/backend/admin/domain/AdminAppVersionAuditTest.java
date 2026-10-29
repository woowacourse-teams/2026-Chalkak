package com.chalkak.backend.admin.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chalkak.backend.appversion.domain.AppPlatform;
import com.chalkak.backend.appversion.domain.MinimumSupportedVersion;
import com.chalkak.backend.appversion.domain.StoreUrl;
import com.chalkak.backend.exception.BusinessException;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AdminAppVersionAuditTest {
    private static final String URL = "https://apps.apple.com/app/id123";

    @Test
    @DisplayName("타입 검증된 스토어 URL만 감사 원문으로 허용하고 일반 URL은 계속 거절한다")
    void fromAppVersion_validTypedUrl_allowsOnlyStoreSnapshot() {
        // When
        var snapshot = state("1.0.0", 0);
        // Then
        assertThat(snapshot.values()).containsEntry("storeUrl", URL);
        assertThatThrownBy(() -> AdminAuditSnapshot.from(Map.of("storeUrl", URL)))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> snapshot.values().put("storeUrl", "https://evil.example"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> AdminAuditSnapshot.fromAppVersion(
                AppPlatform.ANDROID,
                new MinimumSupportedVersion("1.0.0"),
                new StoreUrl(URL),
                0
        )).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> state("1.0.0", -1)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> AdminAuditSnapshot.fromAppVersion(
                null,
                new MinimumSupportedVersion("1.0.0"),
                new StoreUrl(URL),
                0
        )).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> AdminAuditSnapshot.fromAppVersion(
                AppPlatform.IOS,
                null,
                new StoreUrl(URL),
                0
        )).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> AdminAuditSnapshot.fromAppVersion(
                AppPlatform.IOS,
                new MinimumSupportedVersion("1.0.0"),
                null,
                0
        )).isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("최초 생성과 하향 변경 감사는 허용하고 잘못된 revision·플랫폼·변경 없는 전이·빈 사유는 거절한다")
    void create_appVersionAudit_validatesTransitionsAndReason() {
        // Given
        var empty = AdminAuditSnapshot.from(Map.of());
        var initial = state("2.0.0", 0);
        // When & Then
        assertThat(create(
                AdminAction.APP_VERSION_CREATED,
                empty,
                initial
        ).getAfterState())
                .containsEntry("storeUrl", URL);
        assertThat(create(
                AdminAction.APP_VERSION_UPDATED,
                initial,
                state("1.0.0", 1)
        ).getReason())
                .isEqualTo("출시 확인");
        assertInvalid(
                AdminAction.APP_VERSION_CREATED,
                initial,
                initial
        );
        assertInvalid(
                AdminAction.APP_VERSION_CREATED,
                empty,
                state("1.0.0", 1)
        );
        assertInvalid(
                AdminAction.APP_VERSION_UPDATED,
                empty,
                state("1.0.0", 1)
        );
        assertInvalid(
                AdminAction.APP_VERSION_UPDATED,
                initial,
                state("1.0.0", 0)
        );
        assertInvalid(
                AdminAction.APP_VERSION_UPDATED,
                initial,
                state("1.0.0", 2)
        );
        assertInvalid(
                AdminAction.APP_VERSION_UPDATED,
                initial,
                state("2.0.0", 1)
        );
        assertInvalid(
                AdminAction.APP_VERSION_UPDATED,
                initial,
                AdminAuditSnapshot.fromAppVersion(
                        AppPlatform.ANDROID,
                        new MinimumSupportedVersion("1.0.0"),
                        new StoreUrl(
                                "https://play.google.com/store/apps/details?id=com.example.app"),
                        1
                )
        );
        assertThatThrownBy(() -> AdminAuditLog.create(
                UUID.randomUUID(),
                AdminAction.APP_VERSION_CREATED,
                AdminTargetType.APP_VERSION,
                UUID.randomUUID(),
                " ",
                empty,
                initial,
                Instant.now(),
                UUID.randomUUID()
        )).isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("검증된 스토어 주소라도 민감한 값이 포함되면 감사 스냅샷을 거절한다")
    void fromAppVersion_sensitiveUrl_rejectsValue() {
        // Given
        StoreUrl url = new StoreUrl("https://apps.apple.com/app/token=example-secret/id123");
        // When & Then
        assertThatThrownBy(() -> AdminAuditSnapshot.fromAppVersion(
                AppPlatform.IOS,
                new MinimumSupportedVersion("1.0.0"),
                url,
                0
        )).isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("revision 상한 직전 변경은 허용하고 상한에서 순환하는 전이는 거절한다")
    void create_revisionUpperBoundary_preventsOverflow() {
        // When & Then
        assertThat(create(
                AdminAction.APP_VERSION_UPDATED,
                state("1.0.0", Long.MAX_VALUE - 1),
                state("2.0.0", Long.MAX_VALUE)
        ).getAfterState()).containsEntry("revision", Long.MAX_VALUE);
        assertInvalid(
                AdminAction.APP_VERSION_UPDATED,
                state("1.0.0", Long.MAX_VALUE),
                state("2.0.0", 0)
        );
    }

    private AdminAuditSnapshot state(String version, long revision) {
        return AdminAuditSnapshot.fromAppVersion(
                AppPlatform.IOS,
                new MinimumSupportedVersion(version),
                new StoreUrl(URL),
                revision
        );
    }
    private AdminAuditLog create(
            AdminAction action,
            AdminAuditSnapshot before,
            AdminAuditSnapshot after
    ) {
        return AdminAuditLog.create(
                UUID.randomUUID(),
                action,
                AdminTargetType.APP_VERSION,
                UUID.randomUUID(),
                "출시 확인",
                before,
                after,
                Instant.now(),
                UUID.randomUUID()
        );
    }
    private void assertInvalid(
            AdminAction action,
            AdminAuditSnapshot before,
            AdminAuditSnapshot after
    ) {
        assertThatThrownBy(() -> create(
                action,
                before,
                after
        )).isInstanceOf(BusinessException.class);
    }
}
