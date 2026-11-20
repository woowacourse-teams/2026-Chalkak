package com.chalkak.backend.jev.service;

import com.chalkak.backend.common.logging.LogFields;
import com.chalkak.backend.jev.domain.JevDecision;
import com.chalkak.backend.jev.domain.JevDisposition;
import com.chalkak.backend.jev.domain.JevModerationRun;
import com.chalkak.backend.jev.domain.JevSourcePolicy;
import com.chalkak.backend.jev.repository.JevJobRepository;
import com.chalkak.backend.notification.service.NotificationService;
import com.chalkak.backend.post.domain.ModerationStatus;
import com.chalkak.backend.post.domain.Post;
import com.chalkak.backend.post.repository.PostRepository;
import com.chalkak.backend.user.repository.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import com.chalkak.backend.jev.domain.JevSlackDelivery;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@Transactional(readOnly = true)
public class JevResultApplicationService {

    private static final Logger MODERATION_LOG = LoggerFactory.getLogger("chalkak.moderation");

    private final JevJobRepository jobs;
    private final PostRepository posts;
    private final UserRepository users;
    private final JevAutomationService automation;
    private final NotificationService notifications;
    private final Clock clock;

    private final boolean slackDeliveryEnabled;

    public JevResultApplicationService(
            JevJobRepository jobs,
            PostRepository posts,
            UserRepository users,
            JevAutomationService automation,
            NotificationService notifications,
            Clock clock,
            @Value("${chalkak.admin.notification.delivery-enabled:false}") boolean slackDeliveryEnabled
    ) {
        this.jobs = jobs;
        this.posts = posts;
        this.users = users;
        this.automation = automation;
        this.notifications = notifications;
        this.clock = clock;
        this.slackDeliveryEnabled = slackDeliveryEnabled;
    }

    public List<UUID> findUnappliedRunIds() {
        if (!automation.isWorkerEnabled()) {
            // 미완료와 완료 대기는 별도 배치로 읽어 이미지 준비 중인 행이 다음 작업을 막지 않게 한다.
            return Stream.concat(
                    jobs.findUnfinishedProductionRunIds().stream(),
                    jobs.findUnappliedRunIds().stream()
            ).distinct().toList();
        }
        return jobs.findUnappliedRunIds();
    }

    @Transactional
    public void apply(UUID id) {
        var target = jobs.findTarget(id).orElse(null);
        if (target == null || target.preview()) {
            return;
        }
        // 회원→게시물→운영 제어→작업 순서. 탈퇴/등록/삭제와 잠금 순서를 뒤집지 않는다.
        posts.findAuthorIdByPostId(target.postId()).ifPresent(users::findByIdForUpdate);
        Post post = posts.findByIdForUpdate(target.postId()).orElse(null);
        automation.lockControl();
        JevModerationRun run = jobs.findRunForUpdate(id).orElse(null);
        if (run == null || run.getAppliedAt() != null) {
            return;
        }
        Instant now = clock.instant();
        if (run.getFinishedAt() == null) {
            if (automation.isWorkerEnabled()) {
                return;
            }
            // VALIDATING이어도 먼저 중단을 보존하고 실제 적용/알림은 이미지 준비 후로 미룬다.
            run.suspend("AUTOMATION_DISABLED", now);
        }
        String sourceReason = JevSourcePolicy.findHoldReason(post);
        if ("IMAGE_NOT_READY".equals(sourceReason)) {
            return;
        }
        if (sourceReason != null) {
            run.apply(
                    JevDisposition.SKIPPED,
                    sourceReason,
                    now
            );
            return;
        }
        String controlReason = automation.findHoldReason(run);
        if (controlReason != null) {
            String reason = run.getClassificationPromptId() == null && run.getEvaluation() != null
                    && run.getEvaluation().error() != null
                            ? run.getEvaluation().error()
                            : controlReason;
            markForAdmin(
                    run,
                    reason,
                    now
            );
            return;
        }
        if (run.getEvaluation() == null || run.getEvaluation().error() != null
                || run.getEvaluation().recommendation() != JevDecision.APPROVE) {
            String reason = run.getEvaluation() != null && run.getEvaluation().error() == null
                    ? "MODEL_RECOMMENDATION"
                    : "TECHNICAL_REVIEW";
            markForAdmin(
                    run,
                    reason,
                    now
            );
            return;
        }
        post.approve(now);
        run.apply(
                JevDisposition.AUTO_APPROVED,
                "ALL_QUESTIONS_APPROVED",
                now
        );
        // 실제 승인에만 기존 사용자 알림을 만든다. AI 실행에 가짜 관리자/감사 ID를 만들지 않는다.
        notifications.createForModeration(
                post,
                run.getId(),
                ModerationStatus.APPROVED,
                null,
                now
        );
        logApprovedAfterCommit(post, now);
    }

    private void logApprovedAfterCommit(Post post, Instant approvedAt) {
        // 커밋 뒤 지연 로딩 없이 기록하고, 롤백된 승인을 대시보드에 완료로 표시하지 않는다.
        UUID postId = post.getId();
        long waitSeconds = Duration.between(post.getCreatedAt(), approvedAt).getSeconds();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                MODERATION_LOG.atInfo()
                        .addKeyValue(LogFields.TYPE, LogFields.TYPE_MODERATION)
                        .addKeyValue(LogFields.EVENT, "approved")
                        .addKeyValue(LogFields.POST_ID, postId)
                        .addKeyValue(LogFields.WAIT_SECONDS, waitSeconds)
                        .log("검수 결정");
            }
        });
    }

    private void markForAdmin(
            JevModerationRun run,
            String reason,
            Instant now
    ) {
        run.apply(
                JevDisposition.ADMIN_REVIEW,
                reason,
                now
        );
        run.updateSlackDelivery(JevSlackDelivery.ready(now, slackDeliveryEnabled));
    }
}
