package com.chalkak.backend.jev.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import com.chalkak.backend.post.domain.Post;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "jev_moderation_runs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class JevModerationRun {

    private static final int MAX_UNCLAIMED_DISPATCH_ATTEMPTS = 4;

    @Id
    private UUID id;

    @Column(name = "post_id", nullable = false, updatable = false)
    private UUID postId;

    @Column(name = "topic_id", nullable = false, updatable = false)
    private UUID topicId;

    @Column(name = "topic_title", nullable = false, updatable = false)
    private String topicTitle;

    @Column(name = "storage_key", nullable = false, updatable = false, length = 1024)
    private String storageKey;

    @Column(nullable = false, updatable = false)
    private boolean preview;

    @Column(name = "created_by_admin_id", updatable = false)
    private UUID createdByAdminId;

    @Column(name = "classification_id", updatable = false)
    private UUID classificationId;

    @Column(name = "classification_prompt_id", updatable = false)
    private UUID classificationPromptId;

    @Column(name = "photo_prompt_id", updatable = false)
    private UUID photoPromptId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "preview_draft", updatable = false, columnDefinition = "jsonb")
    private JevPromptDraft previewDraft;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private JevPhase phase;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private JevEvaluation evaluation;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "gold_label", columnDefinition = "jsonb")
    private JevGoldLabel goldLabel;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private JevDisposition disposition;

    @Column(name = "application_reason", length = 120)
    private String applicationReason;

    @Column(name = "applied_at")
    private Instant appliedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "slack_delivery", columnDefinition = "jsonb")
    private JevSlackDelivery slackDelivery;

    @Column(name = "slack_due_at")
    private Instant slackDueAt;

    @Column(name = "control_revision", nullable = false, updatable = false)
    private long controlRevision;

    @Column(name = "dispatch_at", nullable = false)
    private Instant dispatchAt;

    @Column(name = "dispatch_attempts", nullable = false)
    private int dispatchAttempts;

    @Column(name = "unclaimed_dispatch_attempts", nullable = false)
    private int unclaimedDispatchAttempts;

    @Column(name = "dispatch_error", length = 120)
    private String dispatchError;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static JevModerationRun preview(
            UUID postId,
            UUID topicId,
            String topicTitle,
            String storageKey,
            UUID adminId,
            UUID classificationId,
            JevPromptDraft draft,
            Instant now
    ) {
        JevModerationRun result = new JevModerationRun();
        result.id = UUID.randomUUID();
        result.postId = postId;
        result.topicId = topicId;
        result.topicTitle = topicTitle;
        result.storageKey = storageKey;
        result.preview = true;
        result.createdByAdminId = adminId;
        result.classificationId = classificationId;
        result.previewDraft = draft;
        result.phase = JevPhase.ready(now);
        result.dispatchAt = now;
        result.createdAt = now;
        return result;
    }

    public static JevModerationRun production(
            Post post,
            JevPromptVersion classification,
            JevPromptVersion photo,
            JevTopicCategory category,
            String manualReason,
            long controlRevision,
            Instant now
    ) {
        JevModerationRun result = new JevModerationRun();
        result.id = UUID.randomUUID();
        result.postId = post.getId();
        result.topicId = post.getTopic().getId();
        result.topicTitle = post.getTopic().getTitle();
        result.storageKey = post.getPhoto().getOriginalStorageKey();
        result.controlRevision = controlRevision;
        result.classificationId = category == null ? null : category.getId();
        result.classificationPromptId = classification == null ? null : classification.getId();
        result.photoPromptId = photo == null ? null : photo.getId();
        result.phase = JevPhase.ready(now);
        result.dispatchAt = now;
        result.createdAt = now;
        if (manualReason != null) {
            result.suspend(manualReason, now);
        }
        return result;
    }

    public void updatePhase(JevPhase phase) {
        this.phase = phase;
    }

    public void evaluate(JevGoldLabel label) {
        if (finishedAt == null || label == null) {
            throw new IllegalArgumentException("완료된 검수에만 정답을 등록할 수 있습니다.");
        }
        this.goldLabel = label;
    }

    public void complete(JevEvaluation evaluation, Instant now) {
        // 늦은 모델 응답의 원문/사용량은 phase에 남기되, 확정한 전달 실패/중단을 승인으로 바꾸지 않는다.
        if (this.evaluation != null
                && ("DISPATCH_LIMIT_EXCEEDED".equals(this.evaluation.error())
                        || "AUTOMATION_DISABLED".equals(this.evaluation.error()))) {
            return;
        }
        this.evaluation = evaluation;
        if (finishedAt == null) {
            this.finishedAt = now;
        }
    }

    public void suspend(String reason, Instant now) {
        if (evaluation == null) {
            evaluation = JevEvaluation.review(reason);
        }
        if (finishedAt == null) {
            finishedAt = now;
        }
    }

    public void apply(
            JevDisposition disposition,
            String reason,
            Instant now
    ) {
        if (appliedAt != null) {
            return;
        }
        this.disposition = disposition;
        this.applicationReason = reason;
        this.appliedAt = now;
    }

    public void postponeDispatch(Instant next) {
        this.dispatchAt = next;
    }

    public void updateSlackDelivery(JevSlackDelivery delivery) {
        this.slackDelivery = delivery;
        this.slackDueAt = delivery.dueAt();
    }

    public int reserveDispatch(Instant now) {
        if (finishedAt != null) {
            return 0;
        }
        if (!hasDispatchCapacity()) {
            // 전달 상한 때문에 이미 발급된 사진 검사의 100초 예산을 줄이지 않는다.
            if (phase.deadline() != null && now.isBefore(phase.deadline())) {
                dispatchAt = phase.deadline();
                return 0;
            }
            suspend("DISPATCH_LIMIT_EXCEEDED", now);
            return 0;
        }
        dispatchAttempts++;
        unclaimedDispatchAttempts++;
        dispatchAt = now.plusSeconds(15);
        return dispatchAttempts;
    }

    public boolean hasDispatchCapacity() {
        return unclaimedDispatchAttempts < MAX_UNCLAIMED_DISPATCH_ATTEMPTS;
    }

    public void acknowledgeDispatch() {
        // 누적 전달 횟수/오류와 물리 호출 이력·100초 예산은 초기화하지 않는다.
        unclaimedDispatchAttempts = 0;
    }

    public void recordDispatch(int attempt, String error) {
        if (attempt == dispatchAttempts) {
            dispatchError = error;
        }
    }
}
