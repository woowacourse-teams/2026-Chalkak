package com.chalkak.backend.admin.api.v1.controller;

import com.chalkak.backend.admin.api.support.AuthenticatedAdmin;
import com.chalkak.backend.admin.api.support.CurrentAdmin;
import com.chalkak.backend.admin.api.v1.docs.AdminPostCorrectionApiDocs;
import com.chalkak.backend.admin.api.v1.dto.request.AdminPostCorrectionRequest;
import com.chalkak.backend.admin.api.v1.dto.response.AdminPostModerationResponse;
import com.chalkak.backend.admin.service.post.AdminPostCorrectionService;
import com.chalkak.backend.common.util.CanonicalUuidParser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/posts/{postId}/moderation-correction")
public class AdminPostCorrectionController implements AdminPostCorrectionApiDocs {

    private final AdminPostCorrectionService service;

    @Override
    @PutMapping
    public ResponseEntity<AdminPostModerationResponse> correct(
            @CurrentAdmin AuthenticatedAdmin admin,
            @PathVariable String postId,
            @Valid @RequestBody AdminPostCorrectionRequest request
    ) {
        return ResponseEntity.ok(AdminPostModerationResponse.from(service.correct(
                CanonicalUuidParser.parse(postId),
                admin.adminId(),
                request.status(),
                request.reason()
        )));
    }
}
