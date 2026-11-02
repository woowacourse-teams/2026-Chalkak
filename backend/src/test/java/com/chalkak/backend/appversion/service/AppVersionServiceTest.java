package com.chalkak.backend.appversion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.chalkak.backend.appversion.domain.AppPlatform;
import com.chalkak.backend.appversion.domain.AppVersion;
import com.chalkak.backend.appversion.domain.ChangeReason;
import com.chalkak.backend.appversion.domain.MinimumSupportedVersion;
import com.chalkak.backend.appversion.domain.StoreUrl;
import com.chalkak.backend.appversion.repository.AppVersionRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AppVersionServiceTest {

    private static final UUID ADMIN_ID = UUID.fromString("019a0000-0000-7000-8000-000000000848");
    @Mock
    private AppVersionRepository repository;

    @InjectMocks
    private AppVersionService service;

    @ParameterizedTest
    @CsvSource(textBlock = """
            ANDROID, 1.2.0, https://play.google.com/store/apps/details?id=com.example.chalkak
            IOS, 2.0.0, https://apps.apple.com/app/id1234567890
            """)
    @DisplayName("요청한 플랫폼의 최소 지원 버전과 스토어 주소를 반환한다")
    void getAppVersion_configuredPlatform_returnsSettings(
            AppPlatform platform,
            String version,
            String storeUrl
    ) {
        // Given
        given(repository.findByPlatform(platform)).willReturn(Optional.of(setting(
                platform,
                version,
                storeUrl
        )));

        // When
        AppVersionResult result = service.getAppVersion(platform);

        // Then
        assertThat(result).isEqualTo(new AppVersionResult(
                platform,
                version,
                storeUrl
        ));
    }

    @ParameterizedTest
    @EnumSource(AppPlatform.class)
    @DisplayName("요청한 플랫폼 설정이 없으면 null 설정을 반환하고 기본 데이터를 저장하지 않는다")
    void getAppVersion_unconfiguredPlatform_returnsNoRestrictionWithoutSeeding(
            AppPlatform platform) {
        // Given
        given(repository.findByPlatform(platform)).willReturn(Optional.empty());

        // When
        AppVersionResult result = service.getAppVersion(platform);

        // Then
        assertThat(result).isEqualTo(new AppVersionResult(
                platform,
                null,
                null
        ));
        then(repository).should(never()).saveAndFlush(any(AppVersion.class));
    }

    private AppVersion setting(
            AppPlatform platform,
            String version,
            String storeUrl
    ) {
        return AppVersion.create(
                platform,
                new MinimumSupportedVersion(version),
                new StoreUrl(storeUrl),
                ADMIN_ID,
                new ChangeReason("테스트 설정")
        );
    }
}
