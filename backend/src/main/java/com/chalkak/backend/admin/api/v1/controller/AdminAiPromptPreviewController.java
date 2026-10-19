package com.chalkak.backend.admin.api.v1.controller;

import com.chalkak.backend.admin.api.support.AuthenticatedAdmin;
import com.chalkak.backend.admin.api.support.CurrentAdmin;
import com.chalkak.backend.admin.api.v1.docs.AdminAiPromptPreviewApiDocs;
import com.chalkak.backend.admin.api.v1.dto.request.AiPromptPreviewRequest;
import com.chalkak.backend.admin.api.v1.dto.response.AiPromptPreviewResponse;
import com.chalkak.backend.admin.service.aireview.AdminAiPromptPreviewService;
import com.chalkak.backend.admin.service.aireview.AdminAiPromptPreviewService.PreviewCommand;
import com.chalkak.backend.common.util.CanonicalUuidParser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/ai-prompt-previews")
public class AdminAiPromptPreviewController implements AdminAiPromptPreviewApiDocs {

    private final AdminAiPromptPreviewService previewService;

    @Override
    @PostMapping
    public ResponseEntity<AiPromptPreviewResponse> preview(
            @CurrentAdmin AuthenticatedAdmin authenticatedAdmin,
            @Valid @RequestBody AiPromptPreviewRequest request
    ) {
        return ResponseEntity.ok(AiPromptPreviewResponse.from(previewService.preview(
                authenticatedAdmin.adminId(),
                toCommand(request))));
    }

    private PreviewCommand toCommand(AiPromptPreviewRequest request) {
        AiPromptPreviewRequest.Parameters parameters = request.parameters();
        if (parameters == null) {
            parameters = new AiPromptPreviewRequest.Parameters(null, null, null, null);
        }
        return new PreviewCommand(
                request.type(),
                request.systemPrompt(),
                request.userPromptTemplate(),
                request.model(),
                parameters.imageDetail(),
                parameters.maxOutputTokens(),
                parameters.reasoningEffort(),
                parameters.distinctivePerspectiveThreshold(),
                CanonicalUuidParser.parse(request.postId()));
    }
}
