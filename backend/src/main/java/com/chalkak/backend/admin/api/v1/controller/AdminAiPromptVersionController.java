package com.chalkak.backend.admin.api.v1.controller;

import com.chalkak.backend.admin.api.support.AuthenticatedAdmin;
import com.chalkak.backend.admin.api.support.CurrentAdmin;
import com.chalkak.backend.admin.api.v1.docs.AdminAiPromptVersionApiDocs;
import com.chalkak.backend.admin.api.v1.dto.request.AiPromptParametersRequest;
import com.chalkak.backend.admin.api.v1.dto.request.AiPromptVersionCreateRequest;
import com.chalkak.backend.admin.api.v1.dto.request.AiPromptVersionListRequest;
import com.chalkak.backend.admin.api.v1.dto.response.AiPromptVersionListResponse;
import com.chalkak.backend.admin.api.v1.dto.response.AiPromptVersionResponse;
import com.chalkak.backend.admin.service.aireview.AdminAiPromptVersionService;
import com.chalkak.backend.admin.service.aireview.AdminAiPromptVersionService.CreateCommand;
import com.chalkak.backend.common.util.CanonicalUuidParser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
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

    @Override
    @GetMapping
    public ResponseEntity<AiPromptVersionListResponse> getVersions(
            @CurrentAdmin AuthenticatedAdmin authenticatedAdmin,
            @Valid @ModelAttribute AiPromptVersionListRequest request
    ) {
        return ResponseEntity.ok(AiPromptVersionListResponse.from(
                versionService.getVersions(
                        request.type(),
                        request.page(),
                        request.pageSize()
                )));
    }

    @Override
    @GetMapping("/{promptVersionId}")
    public ResponseEntity<AiPromptVersionResponse> getVersion(
            @CurrentAdmin AuthenticatedAdmin authenticatedAdmin,
            @PathVariable String promptVersionId
    ) {
        return ResponseEntity.ok(AiPromptVersionResponse.from(
                versionService.getVersion(CanonicalUuidParser.parse(promptVersionId))));
    }

    private CreateCommand toCommand(AiPromptVersionCreateRequest request) {
        AiPromptParametersRequest parameters = AiPromptParametersRequest
                .orDefaults(request.parameters());
        return new CreateCommand(
                request.type(),
                request.systemPrompt(),
                request.userPromptTemplate(),
                request.model(),
                parameters.imageDetail(),
                parameters.maxOutputTokens(),
                parameters.reasoningEffort(),
                parameters.distinctivePerspectiveThreshold(),
                request.memo()
        );
    }
}
