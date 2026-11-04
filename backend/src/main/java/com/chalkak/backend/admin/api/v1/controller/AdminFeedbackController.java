package com.chalkak.backend.admin.api.v1.controller;

import com.chalkak.backend.admin.api.support.AuthenticatedAdmin;
import com.chalkak.backend.admin.api.support.CurrentAdmin;
import com.chalkak.backend.admin.api.v1.docs.AdminFeedbackApiDocs;
import com.chalkak.backend.admin.api.v1.dto.request.AdminFeedbackAnswerRequest;
import com.chalkak.backend.admin.api.v1.dto.request.AdminFeedbackListRequest;
import com.chalkak.backend.admin.api.v1.dto.response.AdminFeedbackListResponse;
import com.chalkak.backend.admin.api.v1.dto.response.AdminFeedbackDetailResponse;
import com.chalkak.backend.admin.service.feedback.AdminFeedbackListResult;
import com.chalkak.backend.admin.service.feedback.AdminFeedbackQueryService;
import com.chalkak.backend.admin.service.feedback.AdminFeedbackCommandService;
import com.chalkak.backend.common.util.CanonicalUuidParser;
import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/feedbacks")
public class AdminFeedbackController implements AdminFeedbackApiDocs {

    private final AdminFeedbackQueryService adminFeedbackQueryService;
    private final AdminFeedbackCommandService adminFeedbackCommandService;

    @Override
    @PostMapping("/{feedbackId}/answer")
    public ResponseEntity<Void> answerFeedback(
            @CurrentAdmin AuthenticatedAdmin authenticatedAdmin,
            @PathVariable String feedbackId,
            @Valid @RequestBody AdminFeedbackAnswerRequest request
    ) {
        adminFeedbackCommandService.answerFeedback(
                CanonicalUuidParser.parse(feedbackId),
                authenticatedAdmin.adminId(),
                request.answerContent(),
                Boolean.TRUE.equals(request.sendPush())
        );
        return ResponseEntity.noContent().build();
    }

    @Override
    @DeleteMapping("/{feedbackId}/answer")
    public ResponseEntity<Void> deleteFeedbackAnswer(
            @CurrentAdmin AuthenticatedAdmin authenticatedAdmin,
            @PathVariable String feedbackId,
            @RequestParam("answerVersion") String answerVersion
    ) {
        if (answerVersion.isBlank()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "답변 버전이 필요합니다.");
        }
        adminFeedbackCommandService.deleteFeedbackAnswer(
                CanonicalUuidParser.parse(feedbackId),
                authenticatedAdmin.adminId(),
                answerVersion
        );
        return ResponseEntity.noContent().build();
    }

    @Override
    @DeleteMapping("/{feedbackId}")
    public ResponseEntity<Void> deleteFeedback(
            @CurrentAdmin AuthenticatedAdmin authenticatedAdmin,
            @PathVariable String feedbackId
    ) {
        adminFeedbackCommandService.deleteFeedback(
                CanonicalUuidParser.parse(feedbackId), authenticatedAdmin.adminId());
        return ResponseEntity.noContent().build();
    }

    @Override
    @GetMapping("/{feedbackId}")
    public ResponseEntity<AdminFeedbackDetailResponse> getFeedback(
            @CurrentAdmin AuthenticatedAdmin authenticatedAdmin,
            @PathVariable String feedbackId
    ) {
        return ResponseEntity.ok(AdminFeedbackDetailResponse.from(
                adminFeedbackQueryService.getFeedback(CanonicalUuidParser.parse(feedbackId))
        ));
    }

    @Override
    @GetMapping
    public ResponseEntity<AdminFeedbackListResponse> getFeedbacks(
            @CurrentAdmin AuthenticatedAdmin authenticatedAdmin,
            @Valid @ModelAttribute AdminFeedbackListRequest request
    ) {
        UUID userId = request.userId() == null ? null : CanonicalUuidParser.parse(request.userId());
        AdminFeedbackListResult result = adminFeedbackQueryService.getFeedbacks(
                userId,
                request.sort(),
                request.page(),
                request.pageSize()
        );
        return ResponseEntity.ok(AdminFeedbackListResponse.from(result));
    }
}
