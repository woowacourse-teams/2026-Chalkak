package com.chalkak.backend.admin.api.v1.controller;

import com.chalkak.backend.admin.api.support.AuthenticatedAdmin;
import com.chalkak.backend.admin.api.support.CurrentAdmin;
import com.chalkak.backend.admin.api.v1.docs.AdminTopicPushScheduleApiDocs;
import com.chalkak.backend.admin.api.v1.dto.request.AdminTopicPushScheduleCreationRequest;
import com.chalkak.backend.admin.api.v1.dto.response.AdminTopicPushScheduleResponse;
import com.chalkak.backend.admin.service.notification.AdminTopicPushScheduleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/topic-push-schedules")
public class AdminTopicPushScheduleController implements AdminTopicPushScheduleApiDocs {

    private final AdminTopicPushScheduleService scheduleService;

    @Override
    @PostMapping
    public ResponseEntity<AdminTopicPushScheduleResponse> createSchedule(
            @CurrentAdmin AuthenticatedAdmin authenticatedAdmin,
            @Valid @RequestBody AdminTopicPushScheduleCreationRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(AdminTopicPushScheduleResponse.from(
                scheduleService.createSchedule(request.sendTime())));
    }
}
