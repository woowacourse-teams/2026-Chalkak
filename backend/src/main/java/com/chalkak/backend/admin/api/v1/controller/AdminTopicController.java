package com.chalkak.backend.admin.api.v1.controller;

import com.chalkak.backend.admin.api.support.AuthenticatedAdmin;
import com.chalkak.backend.admin.api.support.CurrentAdmin;
import com.chalkak.backend.admin.api.v1.docs.AdminTopicApiDocs;
import com.chalkak.backend.admin.api.v1.dto.request.AdminTopicDeletionRequest;
import com.chalkak.backend.admin.api.v1.dto.request.AdminTopicListRequest;
import com.chalkak.backend.admin.api.v1.dto.request.AdminTopicMutationRequest;
import com.chalkak.backend.admin.api.v1.dto.response.AdminTopicDetailResponse;
import com.chalkak.backend.admin.api.v1.dto.response.AdminTopicListResponse;
import com.chalkak.backend.admin.service.AdminTopicDetail;
import com.chalkak.backend.admin.service.AdminTopicListResult;
import com.chalkak.backend.admin.service.AdminTopicService;
import com.chalkak.backend.common.util.CanonicalUuidParser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/topics")
public class AdminTopicController implements AdminTopicApiDocs {

    private final AdminTopicService adminTopicService;

    @Override
    @GetMapping
    public ResponseEntity<AdminTopicListResponse> getTopics(
            @CurrentAdmin AuthenticatedAdmin authenticatedAdmin,
            @Valid @ModelAttribute AdminTopicListRequest request
    ) {
        AdminTopicListResult result = adminTopicService.getTopics(
                request.phase(),
                request.dateFrom(),
                request.dateTo(),
                request.sort(),
                request.page(),
                request.pageSize()
        );
        return ResponseEntity.ok(AdminTopicListResponse.from(result));
    }

    @Override
    @PostMapping
    public ResponseEntity<AdminTopicDetailResponse> createTopic(
            @CurrentAdmin AuthenticatedAdmin authenticatedAdmin,
            @Valid @RequestBody AdminTopicMutationRequest request
    ) {
        AdminTopicDetail result = adminTopicService.createTopic(
                authenticatedAdmin.adminId(),
                request.title(),
                request.topicDate(),
                request.startsAt(),
                request.endsAt()
        );
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(AdminTopicDetailResponse.from(result));
    }

    @Override
    @GetMapping("/{topicId}")
    public ResponseEntity<AdminTopicDetailResponse> getTopic(
            @CurrentAdmin AuthenticatedAdmin authenticatedAdmin,
            @PathVariable String topicId
    ) {
        AdminTopicDetail result = adminTopicService.getTopic(
                CanonicalUuidParser.parse(topicId)
        );
        return ResponseEntity.ok(AdminTopicDetailResponse.from(result));
    }

    @Override
    @PutMapping("/{topicId}")
    public ResponseEntity<AdminTopicDetailResponse> updateTopic(
            @CurrentAdmin AuthenticatedAdmin authenticatedAdmin,
            @PathVariable String topicId,
            @Valid @RequestBody AdminTopicMutationRequest request
    ) {
        AdminTopicDetail result = adminTopicService.updateTopic(
                CanonicalUuidParser.parse(topicId),
                authenticatedAdmin.adminId(),
                request.title(),
                request.topicDate(),
                request.startsAt(),
                request.endsAt()
        );
        return ResponseEntity.ok(AdminTopicDetailResponse.from(result));
    }

    @Override
    @DeleteMapping("/{topicId}")
    public ResponseEntity<Void> deleteTopic(
            @CurrentAdmin AuthenticatedAdmin authenticatedAdmin,
            @PathVariable String topicId,
            @Valid @RequestBody AdminTopicDeletionRequest request
    ) {
        adminTopicService.deleteTopic(
                CanonicalUuidParser.parse(topicId),
                authenticatedAdmin.adminId(),
                request.reason()
        );
        return ResponseEntity.noContent().build();
    }
}
