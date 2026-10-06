package com.chalkak.backend.notification.api.v1.dto.request;

import jakarta.validation.constraints.NotBlank;

public record PushDeviceRegistrationRequest(
        @NotBlank(message = "FCM 토큰이 필요합니다.")
        String fcmToken) {
}
