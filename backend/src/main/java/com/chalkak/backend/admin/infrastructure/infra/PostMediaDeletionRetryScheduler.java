package com.chalkak.backend.admin.infrastructure.infra;

import com.chalkak.backend.admin.repository.PostMediaDeletionPlanRepository;
import com.chalkak.backend.admin.service.PostMediaDeletionProcessor;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Profile("!test")
@RequiredArgsConstructor
@Slf4j
public class PostMediaDeletionRetryScheduler {

    private static final int BATCH_SIZE = 100;
    private static final long RETRY_INTERVAL_MILLIS = 60_000L;

    private final PostMediaDeletionPlanRepository deletionPlanRepository;
    private final PostMediaDeletionProcessor deletionProcessor;

    @Scheduled(
            initialDelay = RETRY_INTERVAL_MILLIS,
            fixedDelay = RETRY_INTERVAL_MILLIS
    )
    public void retryDuePlans() {
        deletionPlanRepository.findDuePostIds(Instant.now(), BATCH_SIZE)
                .forEach(this::processSafely);
    }

    private void processSafely(UUID postId) {
        try {
            deletionProcessor.process(postId);
        } catch (RuntimeException exception) {
            log.error(
                    "게시물 미디어 삭제 재시도 처리 실패. postId={}, failureType={}",
                    postId,
                    exception.getClass().getSimpleName()
            );
        }
    }
}
