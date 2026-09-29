package com.chalkak.backend.notification.api.v1.controller;

import com.chalkak.backend.auth.api.support.AuthenticatedUser;
import com.chalkak.backend.auth.api.support.LoginUser;
import com.chalkak.backend.auth.api.support.RequiresExistingUser;
import com.chalkak.backend.common.util.CanonicalUuidParser;
import com.chalkak.backend.notification.api.v1.docs.NotificationApiDocs;
import com.chalkak.backend.notification.api.v1.dto.request.NotificationListRequest;
import com.chalkak.backend.notification.api.v1.dto.response.NotificationListResponse;
import com.chalkak.backend.notification.api.v1.dto.response.NotificationDetailResponse;
import com.chalkak.backend.notification.api.v1.dto.response.NotificationUnreadStatusResponse;
import com.chalkak.backend.notification.service.NotificationInboxService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/notifications")
public class NotificationController implements NotificationApiDocs {

    private final NotificationInboxService notificationInboxService;

    @Override
    @RequiresExistingUser
    @GetMapping
    public ResponseEntity<NotificationListResponse> getNotifications(
            @Valid @ModelAttribute NotificationListRequest request,
            @LoginUser AuthenticatedUser loginUser
    ) {
        return ResponseEntity.ok(NotificationListResponse.from(
                notificationInboxService.getNotifications(
                        loginUser.userId(), request.page(), request.pageSize())));
    }

    @Override
    @RequiresExistingUser
    @GetMapping("/{notificationId}")
    public ResponseEntity<NotificationDetailResponse> getNotification(
            @PathVariable String notificationId,
            @LoginUser AuthenticatedUser loginUser
    ) {
        return ResponseEntity.ok(NotificationDetailResponse.from(
                notificationInboxService.getNotification(
                        loginUser.userId(), CanonicalUuidParser.parse(notificationId))));
    }

    @Override
    @RequiresExistingUser
    @GetMapping("/unread-status")
    public ResponseEntity<NotificationUnreadStatusResponse> getUnreadStatus(
            @LoginUser AuthenticatedUser loginUser
    ) {
        return ResponseEntity.ok(new NotificationUnreadStatusResponse(
                notificationInboxService.hasUnreadNotification(loginUser.userId())));
    }

    @Override
    @RequiresExistingUser
    @PatchMapping("/{notificationId}/read")
    public ResponseEntity<Void> markRead(
            @PathVariable String notificationId,
            @LoginUser AuthenticatedUser loginUser
    ) {
        notificationInboxService.markRead(
                loginUser.userId(), CanonicalUuidParser.parse(notificationId));
        return ResponseEntity.noContent().build();
    }

    @Override
    @RequiresExistingUser
    @PatchMapping("/read-all")
    public ResponseEntity<Void> markAllRead(@LoginUser AuthenticatedUser loginUser) {
        notificationInboxService.markAllRead(loginUser.userId());
        return ResponseEntity.noContent().build();
    }
}
