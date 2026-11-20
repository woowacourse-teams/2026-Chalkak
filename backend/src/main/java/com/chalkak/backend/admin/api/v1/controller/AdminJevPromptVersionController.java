package com.chalkak.backend.admin.api.v1.controller;

import com.chalkak.backend.admin.api.support.AuthenticatedAdmin;
import com.chalkak.backend.admin.api.support.CurrentAdmin;
import com.chalkak.backend.admin.api.v1.docs.AdminJevPromptVersionApiDocs;
import com.chalkak.backend.admin.api.v1.dto.request.JevPromptActivationRequest;
import com.chalkak.backend.admin.api.v1.dto.request.JevPromptVersionCreateRequest;
import com.chalkak.backend.admin.api.v1.dto.response.JevPromptVersionListResponse;
import com.chalkak.backend.admin.api.v1.dto.response.JevPromptVersionResponse;
import com.chalkak.backend.admin.service.jev.AdminJevPromptVersionService;
import com.chalkak.backend.admin.service.jev.AdminJevPromptVersionService.CreateCommand;
import com.chalkak.backend.common.util.CanonicalUuidParser;
import com.chalkak.backend.jev.domain.JevPromptRole;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/jev-prompt-versions")
public class AdminJevPromptVersionController implements AdminJevPromptVersionApiDocs {

    private final AdminJevPromptVersionService service;

    @Override
    @PostMapping
    public ResponseEntity<JevPromptVersionResponse> createVersion(
            @CurrentAdmin AuthenticatedAdmin admin,
            @Valid @RequestBody JevPromptVersionCreateRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(JevPromptVersionResponse.from(
                service.createVersion(admin.adminId(), new CreateCommand(
                        request.role(),
                        request.model(),
                        request.configuration(),
                        request.memo()
                ))));
    }

    @Override
    @GetMapping
    public ResponseEntity<JevPromptVersionListResponse> getVersions(
            @CurrentAdmin AuthenticatedAdmin admin,
            @RequestParam JevPromptRole role,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize
    ) {
        return ResponseEntity.ok(JevPromptVersionListResponse.from(service.getVersions(
                role,
                page,
                pageSize
        )));
    }

    @Override
    @GetMapping("/{promptVersionId}")
    public ResponseEntity<JevPromptVersionResponse> getVersion(
            @CurrentAdmin AuthenticatedAdmin admin,
            @PathVariable String promptVersionId
    ) {
        return ResponseEntity.ok(JevPromptVersionResponse.from(
                service.getVersion(CanonicalUuidParser.parse(promptVersionId))));
    }

    @Override
    @PutMapping("/{promptVersionId}/activation")
    public ResponseEntity<JevPromptVersionResponse> updateActivation(
            @CurrentAdmin AuthenticatedAdmin admin,
            @PathVariable String promptVersionId,
            @Valid @RequestBody JevPromptActivationRequest request
    ) {
        return ResponseEntity.ok(JevPromptVersionResponse.from(service.updateActivation(
                admin.adminId(),
                CanonicalUuidParser.parse(promptVersionId),
                request.active()
        )));
    }
}
