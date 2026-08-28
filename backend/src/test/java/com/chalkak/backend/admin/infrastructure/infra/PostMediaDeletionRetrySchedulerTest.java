package com.chalkak.backend.admin.infrastructure.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

import com.chalkak.backend.admin.repository.PostMediaDeletionPlanRepository;
import com.chalkak.backend.admin.service.PostMediaDeletionProcessor;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class PostMediaDeletionRetrySchedulerTest {

    private static final int BATCH_SIZE = 100;
    private static final UUID FIRST_DUE_POST_ID =
            UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b657801");
    private static final UUID SECOND_DUE_POST_ID =
            UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b657802");

    private final PostMediaDeletionPlanRepository deletionPlanRepository =
            mock(PostMediaDeletionPlanRepository.class);
    private final PostMediaDeletionProcessor deletionProcessor =
            mock(PostMediaDeletionProcessor.class);
    private final PostMediaDeletionRetryScheduler scheduler =
            new PostMediaDeletionRetryScheduler(deletionPlanRepository, deletionProcessor);

    @Test
    @DisplayName("재시도 시각이 도래한 게시물 ID만 삭제 처리기에 위임한다")
    void retryDuePlans_duePostIds_delegatesEachPostToProcessor() {
        // Given
        given(deletionPlanRepository.findDuePostIds(any(Instant.class), eq(BATCH_SIZE)))
                .willReturn(List.of(FIRST_DUE_POST_ID, SECOND_DUE_POST_ID));
        ArgumentCaptor<Instant> dueAtCaptor = ArgumentCaptor.forClass(Instant.class);
        Instant beforeExecution = Instant.now();

        // When
        scheduler.retryDuePlans();
        Instant afterExecution = Instant.now();

        // Then
        then(deletionPlanRepository).should()
                .findDuePostIds(dueAtCaptor.capture(), eq(BATCH_SIZE));
        assertThat(dueAtCaptor.getValue()).isBetween(beforeExecution, afterExecution);
        then(deletionProcessor).should().process(FIRST_DUE_POST_ID);
        then(deletionProcessor).should().process(SECOND_DUE_POST_ID);
        then(deletionProcessor).shouldHaveNoMoreInteractions();
    }
}
