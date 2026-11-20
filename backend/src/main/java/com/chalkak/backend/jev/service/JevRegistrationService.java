package com.chalkak.backend.jev.service;

import com.chalkak.backend.jev.domain.JevModerationRun;
import com.chalkak.backend.jev.domain.JevTopicCategory;
import com.chalkak.backend.jev.repository.JevJobRepository;
import com.chalkak.backend.post.domain.ModerationStatus;
import com.chalkak.backend.post.domain.Post;
import com.chalkak.backend.post.repository.PostRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class JevRegistrationService {

    private static final Duration CLASSIFICATION_RECOVERY_DELAY = Duration.ofMinutes(10);

    private final JevJobRepository jobs;
    private final PostRepository posts;
    private final JevAutomationService automation;
    private final JevClassificationKeyFactory keys;
    private final Clock clock;

    /** 게시물 등록과 같은 트랜잭션에 설정을 고정한 예약을 남긴다. 실제 실행은 이미지 준비 후에 한다. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void registerNew(UUID postId) {
        Post post = posts.findByIdForUpdate(postId).orElse(null);
        if (post == null || post.getDeletedAt() != null
                || (post.getModerationStatus() != ModerationStatus.PENDING
                        && post.getModerationStatus() != ModerationStatus.VALIDATING)
                || jobs.findProductionRunByPostId(post.getId()).isPresent()) {
            return;
        }
        Instant now = clock.instant();
        var selected = automation.selectActive();
        JevTopicCategory category = null;
        if (selected.manualReason() == null) {
            String key = keys.create(
                    post.getTopic().getId(),
                    post.getTopic().getTitle(),
                    selected.classification().getModel(),
                    selected.classification().getConfiguration(),
                    "production:" + selected.classification().getId()
            );
            category = findOrCreateCategory(
                    post.getTopic().getId(),
                    key,
                    now
            );
        }
        jobs.saveRun(JevModerationRun.production(
                post,
                selected.classification(),
                selected.photo(),
                category,
                selected.manualReason(),
                selected.controlRevision(),
                now
        ));
    }

    /** 도입 전 등록이 나중에 준비된 경우 새 자동 검수 대상으로 소급하지 않는다. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void registerExistingPending(UUID postId) {
        Post post = posts.findByIdForUpdate(postId).orElse(null);
        if (post == null || post.getDeletedAt() != null
                || post.getModerationStatus() != ModerationStatus.PENDING
                || jobs.findProductionRunByPostId(postId).isPresent()) {
            return;
        }
        jobs.saveRun(JevModerationRun.production(
                post,
                null,
                null,
                null,
                "PREEXISTING_REGISTRATION",
                0,
                clock.instant()
        ));
    }

    private JevTopicCategory findOrCreateCategory(
            UUID topicId,
            String key,
            Instant now
    ) {
        // 원래 키의 잠금으로 최초 분류와 복구 분류 생성을 함께 직렬화한다.
        jobs.lockClassificationKey(key);
        JevTopicCategory original = jobs.findCategoryByKey(key).orElse(null);
        if (original == null) {
            return jobs.saveCategory(JevTopicCategory.create(
                    topicId,
                    key,
                    now
            ));
        }
        if (original.getPhase().failure() == null) {
            return original;
        }
        String recoveryKey = keys.createRecoveryKey(key);
        return jobs.findCategoryByKey(recoveryKey)
                .orElseGet(() -> canRecoverClassification(original, now)
                        ? jobs.saveCategory(JevTopicCategory.create(
                                topicId,
                                recoveryKey,
                                now
                        ))
                        : original);
    }

    private boolean canRecoverClassification(JevTopicCategory category, Instant now) {
        var phase = category.getPhase();
        if (phase.calls().isEmpty()) {
            return false;
        }
        var lastCall = phase.calls().getLast();
        if (!lastCall.isRetryable()) {
            return false;
        }
        Instant availableAt = lastCall.receivedAt().plus(CLASSIFICATION_RECOVERY_DELAY);
        if (lastCall.retryAfterSeconds() != null) {
            Instant providerAvailableAt = lastCall.receivedAt()
                    .plusSeconds(lastCall.retryAfterSeconds());
            if (providerAvailableAt.isAfter(availableAt)) {
                availableAt = providerAvailableAt;
            }
        }
        return !now.isBefore(availableAt);
    }
}
