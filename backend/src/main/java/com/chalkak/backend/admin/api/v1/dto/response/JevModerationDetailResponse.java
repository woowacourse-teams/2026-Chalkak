package com.chalkak.backend.admin.api.v1.dto.response;

import com.chalkak.backend.admin.service.jev.AdminJevQueryService;
import com.chalkak.backend.jev.domain.JevDisposition;
import com.chalkak.backend.jev.domain.JevGoldLabel;
import com.chalkak.backend.jev.domain.JevSlackDelivery;
import com.chalkak.backend.post.domain.ModerationStatus;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record JevModerationDetailResponse(
        JevRunResponse result,
        String model,
        UUID classificationPromptId,
        UUID photoPromptId,
        JevDisposition disposition,
        String applicationReason,
        Instant appliedAt,
        ModerationStatus postStatus,
        Instant postDeletedAt,
        String originalImageUrl,
        JevSlackDelivery slackDelivery,
        JevGoldLabel goldLabel,
        Map<String, String> rejectionSuggestions
) {
    public static JevModerationDetailResponse from(AdminJevQueryService.Detail detail) {
        var run = detail.run();
        return new JevModerationDetailResponse(
                JevRunResponse.from(run, detail.category()),
                run.isPreview() ? run.getPreviewDraft().model() : "jev-1.14.0",
                run.getClassificationPromptId(),
                run.getPhotoPromptId(),
                run.getDisposition(),
                run.getApplicationReason(),
                run.getAppliedAt(),
                detail.postStatus(),
                detail.postDeletedAt(),
                detail.originalImageUrl(),
                run.getSlackDelivery(),
                run.getGoldLabel(),
                detail.rejectionSuggestions()
        );
    }
}
