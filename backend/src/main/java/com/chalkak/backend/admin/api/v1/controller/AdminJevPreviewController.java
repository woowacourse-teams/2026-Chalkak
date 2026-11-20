package com.chalkak.backend.admin.api.v1.controller;

import com.chalkak.backend.admin.api.support.AuthenticatedAdmin;
import com.chalkak.backend.admin.api.support.CurrentAdmin;
import com.chalkak.backend.admin.api.v1.docs.AdminJevPreviewApiDocs;
import com.chalkak.backend.admin.api.v1.dto.request.JevPreviewCreateRequest;
import com.chalkak.backend.admin.api.v1.dto.response.JevRunResponse;
import com.chalkak.backend.common.util.CanonicalUuidParser;
import com.chalkak.backend.jev.domain.JevModerationRun;
import com.chalkak.backend.jev.service.JevJobService;
import jakarta.validation.Valid;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/jev-previews")
public class AdminJevPreviewController implements AdminJevPreviewApiDocs {

    private final JevJobService service;

    @Override
    @PostMapping
    public ResponseEntity<JevRunResponse> createPreview(
            @CurrentAdmin AuthenticatedAdmin admin,
            @Valid @RequestBody JevPreviewCreateRequest request
    ) {
        JevModerationRun run = service.createPreview(
                admin.adminId(),
                CanonicalUuidParser.parse(request.postId()),
                CanonicalUuidParser.parse(request.topicId()),
                request.draft()
        );
        return ResponseEntity.created(URI.create("/api/v1/admin/jev-previews/" + run.getId()))
                .body(JevRunResponse.from(run, service.getCategory(run.getId())));
    }

    @Override
    @GetMapping("/{jobId}")
    public ResponseEntity<JevRunResponse> getPreview(@PathVariable String jobId) {
        var id = CanonicalUuidParser.parse(jobId);
        return ResponseEntity.ok(JevRunResponse.from(service.getRun(id), service.getCategory(id)));
    }
}
