package com.chalkak.backend.admin.api.v1.controller;

import com.chalkak.backend.admin.api.support.AuthenticatedAdmin;
import com.chalkak.backend.admin.api.support.CurrentAdmin;
import com.chalkak.backend.admin.api.v1.docs.AdminJevModerationApiDocs;
import com.chalkak.backend.admin.api.v1.dto.request.JevEvaluationRequest;
import com.chalkak.backend.admin.api.v1.dto.request.JevHistoryRequest;
import com.chalkak.backend.admin.api.v1.dto.response.JevModerationDetailResponse;
import com.chalkak.backend.admin.api.v1.dto.response.JevModerationHistoryResponse;
import com.chalkak.backend.admin.service.jev.AdminJevEvaluationService;
import com.chalkak.backend.admin.service.jev.AdminJevMetricsService;
import com.chalkak.backend.admin.service.jev.AdminJevQueryService;
import com.chalkak.backend.common.util.CanonicalUuidParser;
import com.chalkak.backend.jev.domain.JevGoldLabel;
import com.chalkak.backend.jev.domain.JevMetrics;
import com.chalkak.backend.jev.repository.JevRunFilter;
import jakarta.validation.Valid;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/jev-moderation-runs")
public class AdminJevModerationController implements AdminJevModerationApiDocs {

    private final AdminJevQueryService query;
    private final AdminJevEvaluationService evaluations;
    private final AdminJevMetricsService metrics;

    @Override
    @GetMapping
    public ResponseEntity<JevModerationHistoryResponse> list(
            @Valid @ModelAttribute JevHistoryRequest request) {
        return ResponseEntity.ok(JevModerationHistoryResponse.from(query.list(
                new JevRunFilter(
                        request.preview(),
                        request.postId(),
                        request.topicId(),
                        request.disposition()
                ),
                request.page(),
                request.pageSize()
        )));
    }

    @Override
    @GetMapping("/{jobId}")
    public ResponseEntity<JevModerationDetailResponse> get(@PathVariable String jobId) {
        return ResponseEntity
                .ok(JevModerationDetailResponse.from(query.get(CanonicalUuidParser.parse(jobId))));
    }

    @Override
    @PutMapping("/{jobId}/evaluation")
    public ResponseEntity<JevGoldLabel> evaluate(
            @CurrentAdmin AuthenticatedAdmin admin,
            @PathVariable String jobId,
            @Valid @RequestBody JevEvaluationRequest request
    ) {
        return ResponseEntity.ok(evaluations.evaluate(
                CanonicalUuidParser.parse(jobId),
                admin.adminId(),
                request.decision(),
                request.questions(),
                request.reason(),
                request.sampling()
        ));
    }

    @Override
    @GetMapping("/metrics")
    public ResponseEntity<JevMetrics> metrics(
            @RequestParam Instant from,
            @RequestParam Instant to,
            @RequestParam(defaultValue = "false") Boolean preview
    ) {
        return ResponseEntity.ok(metrics.get(
                from,
                to,
                preview
        ));
    }
}
