package com.chalkak.backend.admin.api.v1.controller;

import com.chalkak.backend.admin.api.support.AuthenticatedAdmin;
import com.chalkak.backend.admin.api.support.CurrentAdmin;
import com.chalkak.backend.admin.api.v1.docs.AdminAiPromptVersionApiDocs;
import com.chalkak.backend.admin.api.v1.dto.request.AiPromptPreviewRequest;
import com.chalkak.backend.admin.api.v1.dto.request.AiPromptVersionCreateRequest;
import com.chalkak.backend.admin.api.v1.dto.response.AiPromptVersionResponse;
import com.chalkak.backend.admin.service.aireview.AdminAiPromptVersionService;
import com.chalkak.backend.admin.service.aireview.AdminAiPromptVersionService.CreateCommand;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/ai-prompt-versions")
public class AdminAiPromptVersionController implements AdminAiPromptVersionApiDocs {

    private final AdminAiPromptVersionService versionService;

    @Override
    @PostMapping
    public ResponseEntity<AiPromptVersionResponse> createVersion(
            @CurrentAdmin AuthenticatedAdmin authenticatedAdmin,
            @Valid @RequestBody AiPromptVersionCreateRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(AiPromptVersionResponse.from(
                versionService.createVersion(authenticatedAdmin.adminId(), toCommand(request))));
    }

    private CreateCommand toCommand(AiPromptVersionCreateRequest request) {
        AiPromptPreviewRequest.Parameters parameters = request.parameters();
        if (parameters == null) {
            parameters = new AiPromptPreviewRequest.Parameters(null, null, null, null);
        }
        return new CreateCommand(
                request.type(),
                request.systemPrompt(),
                request.userPromptTemplate(),
                request.model(),
                parameters.imageDetail(),
                parameters.maxOutputTokens(),
                parameters.reasoningEffort(),
                parameters.distinctivePerspectiveThreshold(),
                request.memo());
    }
}
