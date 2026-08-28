package com.chalkak.backend.admin.api.v1.controller;

import com.chalkak.backend.admin.api.support.AuthenticatedAdmin;
import com.chalkak.backend.admin.api.support.CurrentAdmin;
import com.chalkak.backend.admin.api.v1.docs.AdminPostApiDocs;
import com.chalkak.backend.admin.api.v1.dto.request.AdminPostDeletionRequest;
import com.chalkak.backend.admin.api.v1.dto.request.AdminPostListRequest;
import com.chalkak.backend.admin.api.v1.dto.request.AdminPostModerationRequest;
import com.chalkak.backend.admin.api.v1.dto.response.AdminPostDetailResponse;
import com.chalkak.backend.admin.api.v1.dto.response.AdminPostListResponse;
import com.chalkak.backend.admin.api.v1.dto.response.AdminPostModerationResponse;
import com.chalkak.backend.admin.service.AdminPostDetail;
import com.chalkak.backend.admin.service.AdminPostDeletionService;
import com.chalkak.backend.admin.service.AdminPostListResult;
import com.chalkak.backend.admin.service.AdminPostModerationResult;
import com.chalkak.backend.admin.service.AdminPostModerationService;
import com.chalkak.backend.admin.service.AdminPostQueryService;
import com.chalkak.backend.common.util.CanonicalUuidParser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/posts")
public class AdminPostController implements AdminPostApiDocs {

    private final AdminPostQueryService adminPostQueryService;
    private final AdminPostModerationService adminPostModerationService;
    private final AdminPostDeletionService adminPostDeletionService;

    @Override
    @GetMapping
    public ResponseEntity<AdminPostListResponse> getPosts(
            @CurrentAdmin AuthenticatedAdmin authenticatedAdmin,
            @Valid @ModelAttribute AdminPostListRequest request
    ) {
        AdminPostListResult result = adminPostQueryService.getPosts(
                request.status(),
                request.topicId(),
                request.topicDate(),
                request.userId(),
                request.createdAtFrom(),
                request.createdAtTo(),
                request.sort(),
                request.page(),
                request.pageSize()
        );

        return ResponseEntity.ok(AdminPostListResponse.from(result));
    }

    @Override
    @GetMapping("/{postId}")
    public ResponseEntity<AdminPostDetailResponse> getPost(
            @CurrentAdmin AuthenticatedAdmin authenticatedAdmin,
            @PathVariable String postId
    ) {
        AdminPostDetail detail = adminPostQueryService.getPost(
                CanonicalUuidParser.parse(postId)
        );

        return ResponseEntity.ok(AdminPostDetailResponse.from(detail));
    }

    @Override
    @PutMapping("/{postId}/moderation")
    public ResponseEntity<AdminPostModerationResponse> moderatePost(
            @CurrentAdmin AuthenticatedAdmin authenticatedAdmin,
            @PathVariable String postId,
            @Valid @RequestBody AdminPostModerationRequest request
    ) {
        AdminPostModerationResult result = adminPostModerationService.moderate(
                CanonicalUuidParser.parse(postId),
                authenticatedAdmin.adminId(),
                request.status(),
                request.rejectionReason()
        );

        return ResponseEntity.ok(AdminPostModerationResponse.from(result));
    }

    @Override
    @DeleteMapping("/{postId}")
    public ResponseEntity<Void> deletePost(
            @CurrentAdmin AuthenticatedAdmin authenticatedAdmin,
            @PathVariable String postId,
            @Valid @RequestBody AdminPostDeletionRequest request
    ) {
        adminPostDeletionService.deletePost(
                CanonicalUuidParser.parse(postId),
                authenticatedAdmin.adminId(),
                request.reason()
        );

        return ResponseEntity.noContent().build();
    }
}
