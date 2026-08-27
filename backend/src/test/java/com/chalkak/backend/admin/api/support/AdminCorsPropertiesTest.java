package com.chalkak.backend.admin.api.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AdminCorsPropertiesTest {

    @Test
    @DisplayName("관리자 웹 Origin 앞뒤 공백을 제거한다")
    void create_withSurroundingWhitespace_trimsOrigin() {
        AdminCorsProperties properties = new AdminCorsProperties(
                List.of(" https://admin-dev.example.com ")
        );

        assertThat(properties.allowedOrigins())
                .containsExactly("https://admin-dev.example.com");
    }

    @Test
    @DisplayName("관리자 웹 Origin 목록이 비어 있으면 거부한다")
    void create_withEmptyOrigins_throwsException() {
        assertThatThrownBy(() -> new AdminCorsProperties(List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("관리자 웹 허용 Origin이 필요합니다.");
    }

    @Test
    @DisplayName("빈 관리자 웹 Origin을 거부한다")
    void create_withBlankOrigin_throwsException() {
        assertThatThrownBy(() -> new AdminCorsProperties(List.of(" ")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("관리자 웹 허용 Origin은 정확한 주소여야 합니다.");
    }

    @Test
    @DisplayName("와일드카드 관리자 웹 Origin을 거부한다")
    void create_withWildcardOrigin_throwsException() {
        assertThatThrownBy(() -> new AdminCorsProperties(List.of("https://*.example.com")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("관리자 웹 허용 Origin은 정확한 주소여야 합니다.");
    }
}
