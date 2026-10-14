package com.chalkak.backend.admin.api.v1.controller;

import com.chalkak.backend.admin.api.support.AuthenticatedAdmin;
import com.chalkak.backend.admin.api.support.CurrentAdmin;
import com.chalkak.backend.admin.api.v1.docs.AdminAnalyticsApiDocs;
import com.chalkak.backend.admin.api.v1.dto.request.AdminUserMetricsAggregationRequest;
import com.chalkak.backend.analytics.service.UserMetricsAggregationRunner;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/analytics")
public class AdminAnalyticsController implements AdminAnalyticsApiDocs {

    private final UserMetricsAggregationRunner aggregationRunner;

    @Override
    @PostMapping("/aggregations")
    public ResponseEntity<Void> startUserMetricsAggregation(
            @CurrentAdmin AuthenticatedAdmin authenticatedAdmin,
            @Valid @RequestBody AdminUserMetricsAggregationRequest request
    ) {
        aggregationRunner.start(authenticatedAdmin.adminId(), request.metricDate());
        return ResponseEntity.accepted().build();
    }
}
