package com.chalkak.backend.admin.service;

import com.chalkak.backend.admin.domain.PostMediaDeletionPlan;
import com.chalkak.backend.admin.repository.PostMediaDeletionPlanRepository;
import com.chalkak.backend.post.repository.PostImageStorage;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class PostMediaDeletionProcessor {

    private static final long MAX_RETRY_DELAY_SECONDS = 3_600L;
    private static final String STORAGE_DELETE_FAILED = "STORAGE_DELETE_FAILED";

    private final PostMediaDeletionPlanRepository deletionPlanRepository;
    private final PostImageStorage postImageStorage;

    /** 외부 스토리지 호출은 삭제 요청의 DB 트랜잭션이 커밋된 뒤 별도 흐름에서 실행한다. */
    public void process(UUID postId) {
        Instant startedAt = Instant.now();
        deletionPlanRepository.findByPostId(postId)
                .filter(plan -> !plan.isSucceeded())
                .filter(plan -> plan.isDue(startedAt))
                .ifPresent(this::processPlan);
    }

    private void processPlan(PostMediaDeletionPlan plan) {
        boolean failed = false;
        for (String storageKey : plan.storageKeys()) {
            try {
                postImageStorage.deleteImage(storageKey);
            } catch (RuntimeException exception) {
                failed = true;
                log.warn(
                        "게시물 미디어 객체 삭제 실패. postId={}, failureType={}",
                        plan.getPostId(),
                        exception.getClass().getSimpleName()
                );
            }
        }
        Instant completedAt = Instant.now();
        if (!failed) {
            deletionPlanRepository.markSucceededIfIncomplete(
                    plan.getPostId(),
                    completedAt
            );
            return;
        }
        deletionPlanRepository.markFailedIfIncomplete(
                plan.getPostId(),
                STORAGE_DELETE_FAILED,
                completedAt.plusSeconds(retryDelaySeconds(plan.getAttemptCount())),
                completedAt
        );
    }

    private long retryDelaySeconds(int previousAttemptCount) {
        int exponent = Math.min(previousAttemptCount, 6);
        long delaySeconds = 60L << exponent;
        return Math.min(delaySeconds, MAX_RETRY_DELAY_SECONDS);
    }
}
