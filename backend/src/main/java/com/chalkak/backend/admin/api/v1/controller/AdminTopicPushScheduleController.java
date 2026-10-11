package com.chalkak.backend.admin.api.v1.controller;

import com.chalkak.backend.admin.api.support.AuthenticatedAdmin;
import com.chalkak.backend.admin.api.support.CurrentAdmin;
import com.chalkak.backend.admin.api.v1.docs.AdminTopicPushScheduleApiDocs;
import com.chalkak.backend.admin.api.v1.dto.request.TopicPushScheduleRequest;
import com.chalkak.backend.common.util.CanonicalUuidParser;
import com.chalkak.backend.admin.api.v1.dto.response.TopicPushScheduleListResponse;
import com.chalkak.backend.admin.api.v1.dto.response.TopicPushScheduleResponse;
import com.chalkak.backend.admin.service.notification.AdminTopicPushScheduleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/topic-push-schedules")
public class AdminTopicPushScheduleController implements AdminTopicPushScheduleApiDocs {

    private final AdminTopicPushScheduleService scheduleService;

    @Override
    @GetMapping
    public ResponseEntity<TopicPushScheduleListResponse> getSchedules(
            @CurrentAdmin AuthenticatedAdmin authenticatedAdmin
    ) {
        return ResponseEntity.ok(TopicPushScheduleListResponse.from(
                scheduleService.getSchedules()));
    }

    @Override
    @PostMapping
    public ResponseEntity<TopicPushScheduleResponse> createSchedule(
            @CurrentAdmin AuthenticatedAdmin authenticatedAdmin,
            @Valid @RequestBody TopicPushScheduleRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(TopicPushScheduleResponse.from(
                scheduleService.createSchedule(authenticatedAdmin.adminId(), request.sendTime())));
    }

    @Override
    @PutMapping("/{scheduleId}")
    public ResponseEntity<TopicPushScheduleResponse> updateSchedule(
            @CurrentAdmin AuthenticatedAdmin authenticatedAdmin,
            @PathVariable String scheduleId,
            @Valid @RequestBody TopicPushScheduleRequest request
    ) {
        return ResponseEntity.ok(TopicPushScheduleResponse.from(
                scheduleService.updateSchedule(CanonicalUuidParser.parse(scheduleId),
                        authenticatedAdmin.adminId(), request.sendTime())));
    }

    @Override
    @DeleteMapping("/{scheduleId}")
    public ResponseEntity<Void> deleteSchedule(
            @CurrentAdmin AuthenticatedAdmin authenticatedAdmin,
            @PathVariable String scheduleId
    ) {
        scheduleService.deleteSchedule(CanonicalUuidParser.parse(scheduleId),
                authenticatedAdmin.adminId());
        return ResponseEntity.noContent().build();
    }
}
