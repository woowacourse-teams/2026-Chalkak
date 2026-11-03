package com.chalkak.backend.admin.service.feedback;

import com.chalkak.backend.admin.domain.AdminAction;
import com.chalkak.backend.admin.domain.AdminAuditSnapshot;
import com.chalkak.backend.admin.domain.AdminTargetType;
import com.chalkak.backend.admin.service.audit.AdminAuditLogCommand;
import com.chalkak.backend.admin.service.audit.AdminAuditLogService;
import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.exception.NotFoundException;
import com.chalkak.backend.feedback.domain.Feedback;
import com.chalkak.backend.feedback.repository.FeedbackRepository;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
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
    private final Clock clock;

    @Transactional
    public void deleteFeedback(UUID feedbackId, UUID adminId) {
        Feedback feedback = feedbackRepository.findByIdForUpdate(feedbackId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.BUSINESS_ERROR,
                        "문의를 찾을 수 없습니다."));
        if (feedback.getDeletedAt() != null) {
            return;
        }
        AdminAuditSnapshot beforeState = createDeletionState(feedback);
        feedback.delete(clock.instant().truncatedTo(ChronoUnit.MICROS));
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

    private AdminAuditSnapshot createDeletionState(Feedback feedback) {
        return AdminAuditSnapshot
                .from(Collections.singletonMap("deletedAt", feedback.getDeletedAt()));
    }
}
