package com.chalkak.backend.notification.infrastructure.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NotificationPropertiesTest {

    private static final URI ADMIN_WEB_BASE_URL =
            URI.create("https://admin.example.com");

    @Test
    @DisplayName("경로 없는 HTTPS 관리자 웹 Origin을 사용한다")
    void create_exactHttpsOrigin_succeeds() {
        // When
        NotificationProperties properties = new NotificationProperties(ADMIN_WEB_BASE_URL);

        // Then
        assertThat(properties.adminWebBaseUrl()).isEqualTo(ADMIN_WEB_BASE_URL);
    }

    @Test
    @DisplayName("경로가 붙은 관리자 웹 주소를 거부한다")
    void create_adminWebPath_throwsException() {
        assertThatThrownBy(() -> new NotificationProperties(
                URI.create("https://admin.example.com/posts")
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("관리자 웹 기본 주소는 경로 없는 HTTPS Origin이어야 합니다.");
    }
}
