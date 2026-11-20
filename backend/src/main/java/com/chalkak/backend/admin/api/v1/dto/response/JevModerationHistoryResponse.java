package com.chalkak.backend.admin.api.v1.dto.response;

import com.chalkak.backend.admin.service.jev.AdminJevQueryService;
import com.chalkak.backend.jev.domain.JevDecision;
import com.chalkak.backend.jev.domain.JevDisposition;
import com.chalkak.backend.post.domain.ModerationStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record JevModerationHistoryResponse(List<Item> rows, boolean hasNext) {

    public static JevModerationHistoryResponse from(AdminJevQueryService.History history) {
        return new JevModerationHistoryResponse(history.rows().stream().map(Item::from).toList(),
                history.hasNext());
    }

    public record Item(
            UUID jobId,
            UUID postId,
            UUID topicId,
            String topic,
            boolean preview,
            JevDisposition disposition,
            JevDecision recommendation,
            ModerationStatus postStatus,
            JevDecision goldDecision,
            String slackStatus,
            Instant createdAt,
            Instant finishedAt
    ) {
        private static Item from(AdminJevQueryService.Detail detail) {
            var run = detail.run();
            return new Item(
                    run.getId(),
                    run.getPostId(),
                    run.getTopicId(),
                    run.getTopicTitle(),
                    run.isPreview(),
                    run.getDisposition(),
                    run.getEvaluation() == null ? null : run.getEvaluation().recommendation(),
                    detail.postStatus(),
                    run.getGoldLabel() == null ? null : run.getGoldLabel().decision(),
                    run.getSlackDelivery() == null ? null : run.getSlackDelivery().status(),
                    run.getCreatedAt(),
                    run.getFinishedAt()
            );
        }
    }
}
