package com.chalkak.backend.appversion.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chalkak.backend.exception.BusinessException;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AppVersionTest {
    @ParameterizedTest
    @ValueSource(ints = {1, 499, 500})
    @DisplayName("변경 사유는 공백을 제거하고 Unicode code point 기준 상한까지 허용한다")
    void normalizeChangeReason_validLengths_stripsWhitespace(int count) {
        // When & Then
        assertThat(AppVersion.normalizeChangeReason("  " + "😀".repeat(count) + "  "))
                .isEqualTo("😀".repeat(count));
    }

    @Test
    @DisplayName("필수 설정과 사유가 없거나 사유 길이를 넘으면 생성·수정을 거절한다")
    void create_invalidRequiredFields_throwsBusinessException() {
        // Given
        MinimumSupportedVersion version = new MinimumSupportedVersion("1.0.0");
        StoreUrl url = new StoreUrl("https://apps.apple.com/app/id123");
        // When & Then
        assertThatThrownBy(() -> AppVersion.create(
                null,
                version,
                url,
                UUID.randomUUID(),
                "사유"
        ))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(
                () -> AppVersion.create(
                        AppPlatform.IOS,
                        null,
                        url,
                        UUID.randomUUID(),
                        "사유"
                ))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(
                () -> AppVersion.create(
                        AppPlatform.IOS,
                        version,
                        null,
                        UUID.randomUUID(),
                        "사유"
                ))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> AppVersion.create(
                AppPlatform.IOS,
                version,
                url,
                null,
                "사유"
        ))
                .isInstanceOf(BusinessException.class);
        for (String reason : new String[]{null, " ", "a".repeat(501)}) {
            assertThatThrownBy(() -> AppVersion.normalizeChangeReason(reason))
                    .isInstanceOf(BusinessException.class);
        }
    }

    @Test
    @DisplayName("잘못된 수정 사유를 거절하면 기존 설정과 변경자도 유지한다")
    void update_invalidReason_preservesCurrentState() {
        // Given
        UUID admin = UUID.randomUUID();
        var version = new MinimumSupportedVersion("1.0.0");
        var url = new StoreUrl("https://apps.apple.com/app/id123");
        var setting = AppVersion.create(
                AppPlatform.IOS,
                version,
                url,
                admin,
                "최초 입력"
        );
        // When & Then
        assertThatThrownBy(() -> setting.update(
                new MinimumSupportedVersion("2.0.0"),
                new StoreUrl("https://apps.apple.com/app/id456"),
                UUID.randomUUID(),
                " "
        )).isInstanceOf(BusinessException.class);
        assertThat(setting.getMinimumSupportedVersion()).isEqualTo(version);
        assertThat(setting.getStoreUrl()).isEqualTo(url);
        assertThat(setting.getLastChangedBy()).isEqualTo(admin);
        assertThat(setting.getChangeReason()).isEqualTo("최초 입력");
    }
}
