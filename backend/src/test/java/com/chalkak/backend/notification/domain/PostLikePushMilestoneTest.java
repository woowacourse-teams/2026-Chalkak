package com.chalkak.backend.notification.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chalkak.backend.exception.BusinessException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PostLikePushMilestoneTest {

    private static final UUID POST_ID = UUID.fromString("019a36f0-3db8-72dc-8f2d-07a15432a101");
    private static final UUID USER_ID = UUID.fromString("019a36f0-3db8-72dc-8f2d-07a15432a102");
    private static final Instant CREATED_AT = Instant.parse("2026-10-30T01:00:00Z");

    @Test
    @DisplayName("5단위 좋아요 마일스톤을 발행 대기 상태로 생성한다")
    void create_validMilestone_createsPendingMilestone() {
        // When
        PostLikePushMilestone milestone = PostLikePushMilestone.create(
                POST_ID,
                USER_ID,
                10L,
                CREATED_AT
        );

        // Then
        assertThat(milestone.getPostId()).isEqualTo(POST_ID);
        assertThat(milestone.getUserId()).isEqualTo(USER_ID);
        assertThat(milestone.getMilestoneCount()).isEqualTo(10L);
        assertThat(milestone.getCreatedAt()).isEqualTo(CREATED_AT);
        assertThat(milestone.getPublishStatus()).isEqualTo(PublishStatus.PENDING);
        assertThat(milestone.getNextAttemptAt()).isEqualTo(CREATED_AT);
        assertThat(milestone.getPublishedAt()).isNull();
    }

    @Test
    @DisplayName("5 미만의 좋아요 수로 마일스톤을 생성할 수 없다")
    void create_belowFirstMilestone_throwsBusinessException() {
        assertThatThrownBy(() -> PostLikePushMilestone.create(
                POST_ID,
                USER_ID,
                4L,
                CREATED_AT
        )).isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("5단위가 아닌 좋아요 수로 마일스톤을 생성할 수 없다")
    void create_nonMultipleMilestone_throwsBusinessException() {
        assertThatThrownBy(() -> PostLikePushMilestone.create(
                POST_ID,
                USER_ID,
                6L,
                CREATED_AT
        )).isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("필수 정보가 없으면 마일스톤을 생성할 수 없다")
    void create_missingRequiredValue_throwsBusinessException() {
        assertThatThrownBy(() -> PostLikePushMilestone.create(
                null,
                USER_ID,
                5L,
                CREATED_AT
        )).isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("SQS 수락 시 발행 완료와 수락 시각을 기록한다")
    void markPublished_pendingMilestone_marksPublished() {
        // Given
        PostLikePushMilestone milestone = createMilestone();
        Instant acceptedAt = CREATED_AT.plusSeconds(1);

        // When
        milestone.markPublished(acceptedAt);

        // Then
        assertThat(milestone.getPublishStatus()).isEqualTo(PublishStatus.PUBLISHED);
        assertThat(milestone.getPublishedAt()).isEqualTo(acceptedAt);
        assertThat(milestone.getNextAttemptAt()).isNull();
    }

    @Test
    @DisplayName("일시 실패는 1분 뒤 재시도하고 30분 기한을 넘기지 않는다")
    void retryPublication_pendingMilestone_schedulesWithinDeadline() {
        // Given
        PostLikePushMilestone milestone = createMilestone();

        // When
        milestone.retryPublication(CREATED_AT.plusSeconds(1790));

        // Then
        assertThat(milestone.getPublishStatus()).isEqualTo(PublishStatus.PENDING);
        assertThat(milestone.getNextAttemptAt()).isEqualTo(CREATED_AT.plusSeconds(1800));
    }

    @Test
    @DisplayName("30분 기한에 도달한 재시도는 만료로 종료한다")
    void retryPublication_atDeadline_expiresMilestone() {
        // Given
        PostLikePushMilestone milestone = createMilestone();

        // When
        milestone.retryPublication(CREATED_AT.plusSeconds(1800));

        // Then
        assertThat(milestone.getPublishStatus()).isEqualTo(PublishStatus.EXPIRED);
        assertThat(milestone.getNextAttemptAt()).isNull();
    }

    @Test
    @DisplayName("영구 발행 실패는 실패 상태로 종료한다")
    void failPublication_pendingMilestone_marksFailed() {
        // Given
        PostLikePushMilestone milestone = createMilestone();

        // When
        milestone.failPublication();

        // Then
        assertThat(milestone.getPublishStatus()).isEqualTo(PublishStatus.FAILED);
        assertThat(milestone.getNextAttemptAt()).isNull();
    }

    @Test
    @DisplayName("발행 대기 상태가 아니면 다시 상태를 바꿀 수 없다")
    void markPublished_alreadyPublished_throwsBusinessException() {
        // Given
        PostLikePushMilestone milestone = createMilestone();
        milestone.markPublished(CREATED_AT.plusSeconds(1));

        // When & Then
        assertThatThrownBy(() -> milestone.markPublished(CREATED_AT.plusSeconds(2)))
                .isInstanceOf(BusinessException.class);
    }

    private PostLikePushMilestone createMilestone() {
        return PostLikePushMilestone.create(
                POST_ID,
                USER_ID,
                10L,
                CREATED_AT
        );
    }
}
