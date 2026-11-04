package com.chalkak.backend.admin.service.feedback;

import com.chalkak.backend.admin.domain.AdminAction;
import com.chalkak.backend.admin.domain.AdminAuditLog;
import com.chalkak.backend.admin.domain.AdminAuditSnapshot;
import com.chalkak.backend.admin.domain.AdminTargetType;
import com.chalkak.backend.admin.service.audit.AdminAuditLogCommand;
import com.chalkak.backend.admin.service.audit.AdminAuditLogService;
import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.exception.NotFoundException;
import com.chalkak.backend.feedback.domain.Feedback;
import com.chalkak.backend.feedback.repository.FeedbackRepository;
import com.chalkak.backend.notification.service.NotificationService;
import com.chalkak.backend.user.repository.UserRepository;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import java.util.Collections;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminFeedbackCommandService {

    private final FeedbackRepository feedbackRepository;
    private final AdminAuditLogService auditLogService;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final Clock clock;

    @Transactional
    public void answerFeedback(
            UUID feedbackId,
            UUID adminId,
            String answerContent,
            boolean sendPush
    ) {
        Feedback feedback = feedbackRepository.findByIdForUpdate(feedbackId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.BUSINESS_ERROR,
                        "문의를 찾을 수 없습니다."));
        userRepository.findActiveById(feedback.getUserId())
                .orElseThrow(() -> new BusinessException(ErrorCode.BUSINESS_ERROR,
                        "탈퇴한 회원의 문의에는 답변할 수 없습니다."));
        boolean pushEnabled = feedback.getAnsweredAt() == null || sendPush;
        AdminAuditSnapshot beforeState = createAnswerState(feedback);
        feedback.answer(answerContent, clock.instant());
        AdminAuditLog auditLog = auditLogService.createAuditLog(new AdminAuditLogCommand(
                adminId,
                AdminAction.FEEDBACK_ANSWERED,
                AdminTargetType.FEEDBACK,
                feedback.getId(),
                null,
                beforeState,
                createAnswerState(feedback)
        ));
        notificationService.createForFeedbackAnswer(
                feedback.getUserId(),
                feedback.getId(),
                auditLog.getId(),
                feedback.getAnsweredAt(),
                pushEnabled
        );
    }

    @Transactional
    public void deleteFeedbackAnswer(
            UUID feedbackId,
            UUID adminId,
            String expectedAnswerVersion
    ) {
        Feedback feedback = feedbackRepository.findByIdForUpdate(feedbackId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.BUSINESS_ERROR,
                        "문의를 찾을 수 없습니다."));
        AdminAuditSnapshot beforeState = createAnswerState(feedback);
        if (!feedback.deleteAnswer(expectedAnswerVersion, clock.instant())) {
            return;
        }
        notificationService.deleteFeedbackNotifications(feedbackId);
        auditLogService.createAuditLog(new AdminAuditLogCommand(
                adminId,
                AdminAction.FEEDBACK_ANSWER_DELETED,
                AdminTargetType.FEEDBACK,
                feedbackId,
                null,
                beforeState,
                createAnswerState(feedback)
        ));
    }

    @Transactional
    public void deleteFeedback(UUID feedbackId, UUID adminId) {
        Feedback feedback = feedbackRepository.findByIdForUpdate(feedbackId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.BUSINESS_ERROR,
                        "문의를 찾을 수 없습니다."));
        // 이미 삭제한 문의는 감사 로그를 추가하지 않는다.
        if (feedback.isDeleted()) {
            return;
        }
        AdminAuditSnapshot beforeState = createDeletionState(feedback);
        feedback.delete(clock.instant());
        notificationService.deleteFeedbackNotifications(feedbackId);
        auditLogService.createAuditLog(new AdminAuditLogCommand(
                adminId,
                AdminAction.FEEDBACK_DELETED,
                AdminTargetType.FEEDBACK,
                feedback.getId(),
                null,
                beforeState,
                createDeletionState(feedback)
        ));
    }

    private AdminAuditSnapshot createAnswerState(Feedback feedback) {
        Map<String, Object> state = new HashMap<>();
        state.put("answeredAt", feedback.getAnsweredAt());
        state.put("answerDeletedAt", feedback.getAnswerDeletedAt());
        return AdminAuditSnapshot.from(state);
    }

    private AdminAuditSnapshot createDeletionState(Feedback feedback) {
        return AdminAuditSnapshot
                .from(Collections.singletonMap("deletedAt", feedback.getDeletedAt()));
    }
}
