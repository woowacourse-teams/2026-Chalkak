package com.chalkak.backend.appversion.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chalkak.backend.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class StoreUrlTest {

    @ParameterizedTest
    @CsvSource({"ANDROID, https://play.google.com/store/apps/details?id=com.example.chalkak",
            "ANDROID, https://play.google.com/store/apps/details?id=com.example.chalkak&hl=ko&gl=KR",
            "IOS, https://apps.apple.com/app/id1234567890",
            "IOS, https://apps.apple.com/kr/app/chalkak/id1234567890?l=ko&mt=8",
            "ANDROID, HTTPS://PLAY.GOOGLE.COM:443/store/apps/details?id=com.a.b",
            "IOS, https://APPS.APPLE.COM:443/app/id123"})
    @DisplayName("공식 스토어 상세 주소의 플랫폼·앱 ID·언어 형식을 허용한다")
    void create_validStoreUrl_allowsMatchingPlatform(AppPlatform platform, String value) {
        // Given
        StoreUrl url = StoreUrl.from("  " + value + "  ");
        // When
        url.validatePlatform(platform);
        // Then
        assertThat(url.value()).isEqualTo(value);
        assertThat(url.getPlatform()).isEqualTo(platform);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "https:chalkak", "https://play.google.com",
            "http://apps.apple.com/app/id123", "https://evil.example/app/id123",
            "https://play.google.com.evil.example/store/apps/details?id=com.example.app",
            "https://user@play.google.com/store/apps/details?id=com.example.app",
            "https://apps.apple.com:444/app/id123",
            "https://play.google.com:/store/apps/details?id=com.a.b",
            "https://play.google.com:0443/store/apps/details?id=com.a.b",
            "https://apps.apple.com:0443/app/id123", "https://apps.apple.com/app/id123#update",
            "https://apps.apple.com/app/../id123", "https://play.google.com/store/apps/details",
            "https://play.google.com/store/apps/details?id=",
            "https://play.google.com/store/apps/details?id=bad",
            "https://play.google.com/store/apps/details?id=com.example.app&id=com.other.app",
            "https://play.google.com/store/apps/details?id=com.example.app&token=secret",
            "https://play.google.com/store/apps/details?id=com.example.app&hl=",
            "https://play.google.com/store/apps/details?id=com.example.app&hl",
            "https://apps.apple.com/app/id0", "https://apps.apple.com/app/idabc",
            "https://apps.apple.com/app/id123?mt=7", "https://apps.apple.com/app/id123?secret=abc",
            "https://apps.apple.com/app/%ZZ/id123",
            "https://apps.apple.com/app/%2e%2e/id123",
            "https://apps.apple.com/app/name%2fid123",
            "https://apps.apple.com/app/name%5c/id123"})
    @DisplayName("잘못된 스킴·호스트·권한정보·포트·경로·앱 ID·쿼리·인코딩을 거절한다")
    void create_invalidStoreUrl_throwsBusinessException(String value) {
        // When & Then
        assertThatThrownBy(() -> new StoreUrl(value)).isInstanceOf(BusinessException.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {1999, 2000})
    @DisplayName("스토어 주소 길이 상한 직전과 상한을 허용한다")
    void create_validLength_acceptsBoundary(int length) {
        // Given
        String prefix = "https://apps.apple.com/app/";
        String value = prefix + "a".repeat(length - prefix.length() - 4) + "/id1";
        // When & Then
        assertThat(new StoreUrl(value).value()).hasSize(length);
    }

    @Test
    @DisplayName("스토어 주소 길이 상한 초과와 플랫폼 불일치를 거절한다")
    void validatePlatform_invalidBoundaryOrMismatch_throwsBusinessException() {
        // When & Then
        assertThatThrownBy(
                () -> new StoreUrl("https://apps.apple.com/app/" + "a".repeat(2000) + "/id1"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> new StoreUrl("https://apps.apple.com/app/id123")
                .validatePlatform(AppPlatform.ANDROID))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(
                () -> new StoreUrl("https://play.google.com/store/apps/details?id=com.example.app")
                        .validatePlatform(AppPlatform.IOS))
                .isInstanceOf(BusinessException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://play.google.com/store/apps/%64etails?id=com.a.b",
            "https://play.google.com/store/%61pps/details?id=com.a.b",
            "https://apps.apple.com/%61pp/id123",
            "https://apps.apple.com/app/%69d123",
            " https://apps.apple.com/app/id123",
            "https://apps.apple.com/app/id123\n",
            "\u2000https://apps.apple.com/app/id123\u2000"
    })
    @DisplayName("DB 원문 생성자는 고정 경로 인코딩과 비정규 공백을 거절한다")
    void create_noncanonicalStoredUrl_rejectsWithoutChangingValue(String value) {
        // When & Then
        assertThatThrownBy(() -> new StoreUrl(value)).isInstanceOf(BusinessException.class);
    }
    @Test
    @DisplayName("요청 입력 팩토리는 Unicode 공백과 개행을 제거한 주소를 생성한다")
    void from_requestWhitespace_normalizesBeforeConstruction() {
        // When & Then
        assertThat(StoreUrl.from("\u2000\thttps://apps.apple.com/app/id123\n").value())
                .isEqualTo("https://apps.apple.com/app/id123");
    }

}
