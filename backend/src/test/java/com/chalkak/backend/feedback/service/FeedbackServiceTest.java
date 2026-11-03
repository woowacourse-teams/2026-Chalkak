package com.chalkak.backend.feedback.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.UnauthorizedException;
import com.chalkak.backend.feedback.repository.FeedbackRepository;
import com.chalkak.backend.feedback.repository.FeedbackSlice;
import com.chalkak.backend.user.domain.UserFixture;
import com.chalkak.backend.user.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FeedbackServiceTest {

    private static final UUID USER_ID = UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6570a1");
    private static final Instant NOW = Instant.parse("2026-11-03T06:00:00Z");

    @Mock
    private FeedbackRepository feedbackRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private Clock clock;

    @InjectMocks
    private FeedbackService service;

    @Test
    @DisplayName("문의가 없으면 페이지 정보와 빈 목록을 반환한다")
    void getFeedbacks_noFeedback_returnsEmptyPage() {
        // Given
        given(feedbackRepository.findByUserId(
                USER_ID,
                1,
                20
        ))
                .willReturn(new FeedbackSlice(
                        List.of(),
                        1,
                        20,
                        false
                ));

        // When
        FeedbackListResult result = service.getFeedbacks(
                USER_ID,
                1,
                20
        );

        // Then
        assertThat(result.currentPage()).isEqualTo(1);
        assertThat(result.pageSize()).isEqualTo(20);
        assertThat(result.hasNext()).isFalse();
        assertThat(result.feedbacks()).isEmpty();
    }

    @Test
    @DisplayName("조회 위치가 정수 범위를 넘으면 조회하지 않는다")
    void getFeedbacks_offsetOverIntegerRange_rejectsBeforeQuery() {
        // Given
        int page = Integer.MAX_VALUE;
        int pageSize = 100;

        // When & Then
        assertThatThrownBy(() -> service.getFeedbacks(
                USER_ID,
                page,
                pageSize
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessage("조회 조건이 올바르지 않습니다.");
        verify(feedbackRepository, never()).findByUserId(
                USER_ID,
                page,
                pageSize
        );
    }

    @Test
    @DisplayName("접수 제한 초과 요청은 저장하지 않는다")
    void submit_limitExceeded_doesNotSaveFeedback() {
        // Given
        given(userRepository.findActiveByIdForUpdate(USER_ID))
                .willReturn(Optional.of(UserFixture.create(USER_ID)));
        given(clock.instant()).willReturn(NOW);
        given(feedbackRepository.findSubmissionTimesAfter(USER_ID, NOW.minusSeconds(86400)))
                .willReturn(List.of(NOW.minusSeconds(30)));

        // When & Then
        assertThatThrownBy(() -> service.submit(USER_ID, "문의합니다"))
                .isInstanceOf(BusinessException.class);
        verify(feedbackRepository, never()).save(any());
    }

    @Test
    @DisplayName("회원 잠금 시 이미 탈퇴했으면 접수하지 않는다")
    void submit_withdrawnUser_doesNotSaveFeedback() {
        // Given
        given(userRepository.findActiveByIdForUpdate(USER_ID)).willReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> service.submit(USER_ID, "문의합니다"))
                .isInstanceOf(UnauthorizedException.class);
        verify(feedbackRepository, never()).save(any());
    }
}
