package com.chalkak.backend.feedback.service;

import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.exception.UnauthorizedException;
import com.chalkak.backend.feedback.domain.Feedback;
import com.chalkak.backend.feedback.domain.FeedbackSubmissionHistory;
import com.chalkak.backend.feedback.repository.FeedbackRepository;
import com.chalkak.backend.user.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FeedbackService {

    private final FeedbackRepository feedbackRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    @Transactional
    public FeedbackSubmissionResult submit(UUID userId, String content) {
        Feedback feedback = Feedback.create(userId, content);
        // 문의 이력이 없는 첫 동시 접수도 같은 회원 행을 잠가 직렬화한다.
        userRepository.findActiveByIdForUpdate(userId)
                .orElseThrow(() -> new UnauthorizedException(
                        ErrorCode.UNAUTHORIZED,
                        "유효하지 않은 인증 정보입니다."
                ));
        Instant now = clock.instant();
        FeedbackSubmissionHistory history = new FeedbackSubmissionHistory(
                feedbackRepository.findSubmissionTimesAfter(userId,
                        FeedbackSubmissionHistory.getWindowStart(now))
        );
        history.validateSubmissionAt(now);
        return FeedbackSubmissionResult.from(feedbackRepository.save(feedback));
    }
}
