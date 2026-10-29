package com.chalkak.backend.appversion.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.chalkak.backend.appversion.domain.AppPlatform;
import com.chalkak.backend.support.IntegrationTestSupport;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class AppVersionServiceTest extends IntegrationTestSupport {

    @Autowired
    private AppVersionService service;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("플랫폼별로 저장된 최소 지원 버전과 주소를 독립적으로 조회한다")
    void getAppVersion_configuredPlatforms_returnsPlatformSettings() {
        // Given
        insert(
                "ANDROID",
                "1.2.0",
                "https://play.google.com/store/apps/details?id=com.example.chalkak"
        );
        insert(
                "IOS",
                "2.0.0",
                "https://apps.apple.com/app/id1234567890"
        );
        entityManager.flush();
        entityManager.clear();

        // When
        AppVersionResult android = service.getAppVersion(AppPlatform.ANDROID);
        AppVersionResult ios = service.getAppVersion(AppPlatform.IOS);

        // Then
        assertThat(android).isEqualTo(new AppVersionResult(
                AppPlatform.ANDROID,
                "1.2.0",
                "https://play.google.com/store/apps/details?id=com.example.chalkak"
        ));
        assertThat(ios).isEqualTo(new AppVersionResult(
                AppPlatform.IOS,
                "2.0.0",
                "https://apps.apple.com/app/id1234567890"
        ));
    }

    @Test
    @DisplayName("아직 설정이 없으면 두 플랫폼 모두 null을 반환하고 기본 데이터를 생성하지 않는다")
    void getAppVersion_emptyDatabase_returnsNoRestrictionWithoutSeeding() {
        // When
        AppVersionResult android = service.getAppVersion(AppPlatform.ANDROID);
        AppVersionResult ios = service.getAppVersion(AppPlatform.IOS);

        // Then
        assertThat(android.minimumSupportedVersion()).isNull();
        assertThat(android.storeUrl()).isNull();
        assertThat(ios.minimumSupportedVersion()).isNull();
        assertThat(ios.storeUrl()).isNull();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM app_versions", Integer.class))
                .isZero();
    }

    @Test
    @DisplayName("다른 플랫폼만 설정돼 있으면 요청한 플랫폼은 버전과 주소가 모두 null이다")
    void getAppVersion_unconfiguredPlatform_returnsNoRestriction() {
        // Given
        insert(
                "ANDROID",
                "1.2.0",
                "https://play.google.com/store/apps/details?id=com.example.chalkak"
        );
        entityManager.flush();
        entityManager.clear();

        // When
        AppVersionResult result = service.getAppVersion(AppPlatform.IOS);

        // Then
        assertThat(result).isEqualTo(new AppVersionResult(
                AppPlatform.IOS,
                null,
                null
        ));
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM app_versions", Integer.class))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("DB 값이 바뀌면 다음 조회에 새 설정을 반환한다")
    void getAppVersion_changedDatabase_returnsCurrentSettings() {
        // Given
        insert(
                "ANDROID",
                "1.2.0",
                "https://play.google.com/store/apps/details?id=com.example.chalkak"
        );
        service.getAppVersion(AppPlatform.ANDROID);
        jdbcTemplate
                .update("""
                        UPDATE app_versions SET minimum_supported_version = '1.3.0',
                            store_url = 'https://play.google.com/store/apps/details?id=com.example.chalkak&hl=ko'
                        WHERE platform = 'ANDROID'
                        """);
        entityManager.flush();
        entityManager.clear();

        // When
        AppVersionResult result = service.getAppVersion(AppPlatform.ANDROID);

        // Then
        assertThat(result.minimumSupportedVersion()).isEqualTo("1.3.0");
        assertThat(result.storeUrl()).endsWith("&hl=ko");
    }

    private void insert(
            String platform,
            String minimumSupportedVersion,
            String storeUrl
    ) {
        jdbcTemplate.update(
                "INSERT INTO app_versions (platform, minimum_supported_version, store_url) VALUES (?, ?, ?)",
                platform,
                minimumSupportedVersion,
                storeUrl
        );
    }
}
