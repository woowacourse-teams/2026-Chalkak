package com.chalkak.backend.admin.service.notification;

import com.chalkak.backend.admin.domain.AdminAction;
import com.chalkak.backend.admin.domain.AdminAuditHash;
import com.chalkak.backend.admin.domain.AdminAuditSnapshot;
import com.chalkak.backend.admin.domain.AdminTargetType;
import com.chalkak.backend.admin.service.audit.AdminAuditLogCommand;
import com.chalkak.backend.admin.service.audit.AdminAuditLogService;
import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.notification.domain.Reminder;
import com.chalkak.backend.notification.domain.ReminderRepeat;
import com.chalkak.backend.notification.domain.ReminderTarget;
import com.chalkak.backend.notification.repository.ReminderRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminReminderService {

    private final ReminderRepository reminderRepository;
    private final AdminAuditLogService adminAuditLogService;
    private final Clock clock;

    @Transactional
    public AdminReminderResult createReminder(
            UUID adminId,
            String title,
            String body,
            ReminderRepeat repeat,
            ReminderTarget target,
            Instant scheduledAt
    ) {
        validateAdminId(adminId);
        Reminder reminder = Reminder.create(
                title,
                body,
                repeat,
                target,
                scheduledAt,
                clock.instant()
        );
        Reminder saved = reminderRepository.save(reminder);
        adminAuditLogService.createAuditLog(new AdminAuditLogCommand(
                adminId,
                AdminAction.REMINDER_CREATED,
                AdminTargetType.REMINDER,
                saved.getId(),
                null,
                AdminAuditSnapshot.from(Map.of()),
                createReminderState(saved)
        ));
        return AdminReminderResult.from(saved);
    }

    // 본문 원문을 감사 로그에 노출하지 않도록 공통 UTF-8 SHA-256 해시로 기록한다.
    private AdminAuditSnapshot createReminderState(Reminder reminder) {
        return AdminAuditSnapshot.from(Map.of(
                "title",
                reminder.getTitle(),
                "bodySha256",
                AdminAuditHash.createSha256(reminder.getBody()),
                "repeat",
                reminder.getRepeat().name(),
                "target",
                reminder.getTarget().name(),
                "scheduledAt",
                reminder.getScheduledAt().toString()
        ));
    }

    private void validateAdminId(UUID adminId) {
        if (adminId == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "관리자 식별자가 필요합니다.");
        }
    }
}
