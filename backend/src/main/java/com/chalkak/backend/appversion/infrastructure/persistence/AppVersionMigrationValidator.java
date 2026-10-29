package com.chalkak.backend.appversion.infrastructure.persistence;

import com.chalkak.backend.admin.domain.AdminAuditSnapshot;
import com.chalkak.backend.appversion.domain.AppPlatform;
import com.chalkak.backend.appversion.domain.MinimumSupportedVersion;
import com.chalkak.backend.appversion.domain.StoreUrl;
import com.chalkak.backend.exception.BusinessException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.callback.BaseCallback;
import org.flywaydb.core.api.callback.Context;
import org.flywaydb.core.api.callback.Event;
import org.springframework.stereotype.Component;

/** 기존 값을 변경하지 않고, 새 도메인으로 조회·감사할 수 있는지 DDL 실행 전에 확인한다. */
@Component
public class AppVersionMigrationValidator extends BaseCallback {

    @Override
    public boolean supports(Event event, Context context) {
        return event == Event.BEFORE_MIGRATE;
    }

    @Override
    public void handle(Event event, Context context) {
        Connection connection = context.getConnection();
        try {
            if (!hasAppVersionsTable(connection)) {
                return;
            }
            validateSettings(connection);
        } catch (SQLException exception) {
            throw new FlywayException("앱 버전 설정 사전 검증을 위한 DB 조회에 실패했습니다.", exception);
        }
    }

    private boolean hasAppVersionsTable(Connection connection) throws SQLException {
        try (var statement = connection.createStatement();
                var result = statement.executeQuery("SELECT to_regclass('app_versions')")) {
            return result.next() && result.getString(1) != null;
        }
    }

    private void validateSettings(Connection connection) throws SQLException {
        try (var statement = connection.createStatement();
                var rows = statement.executeQuery(
                        "SELECT id, platform, minimum_supported_version, store_url FROM app_versions")) {
            while (rows.next()) {
                validateSetting(
                        rows.getObject("id", UUID.class),
                        rows.getString("platform"),
                        rows.getString("minimum_supported_version"),
                        rows.getString("store_url")
                );
            }
        }
    }

    private void validateSetting(
            UUID id,
            String platform,
            String minimumSupportedVersion,
            String storeUrl
    ) {
        try {
            AdminAuditSnapshot.fromAppVersion(
                    AppPlatform.valueOf(platform),
                    new MinimumSupportedVersion(minimumSupportedVersion),
                    new StoreUrl(storeUrl),
                    0
            );
        } catch (BusinessException | IllegalArgumentException exception) {
            throw new FlywayException("앱 버전 설정 사전 검증 실패(id=" + id
                    + "): 버전·플랫폼·스토어 주소와 공백·민감정보를 확인한 뒤 다시 배포해 주세요.");
        }
    }
}
