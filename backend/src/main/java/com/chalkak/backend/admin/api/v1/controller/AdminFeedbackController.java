package com.chalkak.backend.admin.api.v1.controller;

import com.chalkak.backend.admin.api.support.AuthenticatedAdmin;
import com.chalkak.backend.admin.api.support.CurrentAdmin;
import com.chalkak.backend.admin.api.v1.docs.AdminFeedbackApiDocs;
import com.chalkak.backend.admin.api.v1.dto.request.AdminFeedbackListRequest;
import com.chalkak.backend.admin.api.v1.dto.response.AdminFeedbackListResponse;
import com.chalkak.backend.admin.service.AdminFeedbackListResult;
import com.chalkak.backend.admin.service.AdminFeedbackQueryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/feedbacks")
public class AdminFeedbackController implements AdminFeedbackApiDocs {

    private final AdminFeedbackQueryService adminFeedbackQueryService;

    @Override
    @GetMapping
    public ResponseEntity<AdminFeedbackListResponse> getFeedbacks(
            @CurrentAdmin AuthenticatedAdmin authenticatedAdmin,
            @Valid @ModelAttribute AdminFeedbackListRequest request
    ) {
        AdminFeedbackListResult result = adminFeedbackQueryService.getFeedbacks(
                request.sort(),
                request.page(),
                request.pageSize());
        return ResponseEntity.ok(AdminFeedbackListResponse.from(result));
    }
}
