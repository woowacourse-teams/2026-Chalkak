package com.chalkak.backend.notification.api.v1.controller;

import com.chalkak.backend.auth.api.support.AuthenticatedUser;
import com.chalkak.backend.auth.api.support.LoginUser;
import com.chalkak.backend.auth.api.support.RequiresExistingUser;
import com.chalkak.backend.notification.api.v1.docs.NotificationSettingsApiDocs;
import com.chalkak.backend.notification.api.v1.dto.response.NotificationSettingsResponse;
import com.chalkak.backend.notification.service.NotificationSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/notification-settings")
public class NotificationSettingsController implements NotificationSettingsApiDocs {

    private final NotificationSettingsService notificationSettingsService;

    @Override
    @RequiresExistingUser
    @GetMapping
    public ResponseEntity<NotificationSettingsResponse> getSettings(
            @LoginUser AuthenticatedUser loginUser
    ) {
        return ResponseEntity.ok(NotificationSettingsResponse.from(
                notificationSettingsService.getSettings(loginUser.userId())));
    }
}
