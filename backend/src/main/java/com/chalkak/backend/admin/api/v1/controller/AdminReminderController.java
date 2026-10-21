package com.chalkak.backend.admin.api.v1.controller;

import com.chalkak.backend.admin.api.support.AuthenticatedAdmin;
import com.chalkak.backend.admin.api.support.CurrentAdmin;
import com.chalkak.backend.admin.api.v1.docs.AdminReminderApiDocs;
import com.chalkak.backend.admin.api.v1.dto.request.AdminReminderListRequest;
import com.chalkak.backend.admin.api.v1.dto.request.AdminReminderRequest;
import com.chalkak.backend.admin.api.v1.dto.response.AdminReminderListResponse;
import com.chalkak.backend.admin.api.v1.dto.response.AdminReminderResponse;
import com.chalkak.backend.admin.service.notification.AdminReminderListResult;
import com.chalkak.backend.admin.service.notification.AdminReminderResult;
import com.chalkak.backend.admin.service.notification.AdminReminderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/reminders")
public class AdminReminderController implements AdminReminderApiDocs {

    private final AdminReminderService reminderService;

    @Override
    @GetMapping
    public ResponseEntity<AdminReminderListResponse> getReminders(
            @CurrentAdmin AuthenticatedAdmin authenticatedAdmin,
            @Valid @ModelAttribute AdminReminderListRequest request
    ) {
        AdminReminderListResult result = reminderService.getReminders(request.page(),
                request.pageSize());
        return ResponseEntity.ok(AdminReminderListResponse.from(result));
    }

    @Override
    @PostMapping
    public ResponseEntity<AdminReminderResponse> createReminder(
            @CurrentAdmin AuthenticatedAdmin authenticatedAdmin,
            @Valid @RequestBody AdminReminderRequest request
    ) {
        AdminReminderResult result = reminderService.createReminder(
                authenticatedAdmin.adminId(),
                request.title(),
                request.body(),
                request.repeat(),
                request.target(),
                request.scheduledAt()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(AdminReminderResponse.from(result));
    }
}
