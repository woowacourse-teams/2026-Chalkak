package com.chalkak.backend.admin.api.v1.controller;

import com.chalkak.backend.admin.api.support.AuthenticatedAdmin;
import com.chalkak.backend.admin.api.support.CurrentAdmin;
import com.chalkak.backend.admin.api.v1.docs.AdminAiReviewApiDocs;
import com.chalkak.backend.admin.api.v1.dto.request.AdminAiReviewListRequest;
import com.chalkak.backend.admin.api.v1.dto.response.AdminAiReviewDetailResponse;
import com.chalkak.backend.admin.api.v1.dto.response.AdminAiReviewListResponse;
import com.chalkak.backend.admin.service.aireview.AdminAiReviewQueryService;
import com.chalkak.backend.common.util.CanonicalUuidParser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/ai-reviews")
public class AdminAiReviewController implements AdminAiReviewApiDocs {

    private final AdminAiReviewQueryService queryService;

    @Override
    @GetMapping
    public ResponseEntity<AdminAiReviewListResponse> getAiReviews(
            @CurrentAdmin AuthenticatedAdmin authenticatedAdmin,
            @Valid @ModelAttribute AdminAiReviewListRequest request
    ) {
        return ResponseEntity.ok(AdminAiReviewListResponse.from(
                queryService.getAiReviews(
                        request.status(),
                        request.promptVersionId(),
                        request.page(),
                        request.pageSize()
                )));
    }

    @Override
    @GetMapping("/{aiReviewId}")
    public ResponseEntity<AdminAiReviewDetailResponse> getAiReview(
            @CurrentAdmin AuthenticatedAdmin authenticatedAdmin,
            @PathVariable String aiReviewId
    ) {
        return ResponseEntity.ok(AdminAiReviewDetailResponse.from(
                queryService.getAiReview(CanonicalUuidParser.parse(aiReviewId))));
    }
}
