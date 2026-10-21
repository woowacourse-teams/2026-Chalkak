package com.chalkak.backend.admin.service.notification;

import com.chalkak.backend.admin.domain.AdminAction;
import com.chalkak.backend.admin.domain.AdminAuditHash;
import com.chalkak.backend.admin.domain.AdminAuditSnapshot;
import com.chalkak.backend.admin.domain.AdminTargetType;
import com.chalkak.backend.admin.service.audit.AdminAuditLogCommand;
import com.chalkak.backend.admin.service.audit.AdminAuditLogService;
import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.exception.NotFoundException;
import com.chalkak.backend.notification.domain.Reminder;
import com.chalkak.backend.notification.domain.ReminderRepeat;
import com.chalkak.backend.notification.domain.ReminderTarget;
import com.chalkak.backend.notification.repository.ReminderRepository;
import com.chalkak.backend.notification.repository.ReminderSlice;
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

    private static final int MAX_PAGE_SIZE = 100;

    private final ReminderRepository reminderRepository;
    private final AdminAuditLogService adminAuditLogService;
    private final Clock clock;

    @Transactional(readOnly = true)
    public AdminReminderListResult getReminders(int page, int pageSize) {
        validatePagination(page, pageSize);
        ReminderSlice slice = reminderRepository.findReminders(page, pageSize);
        return AdminReminderListResult.from(
                slice,
                page,
                pageSize
        );
    }

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

    @Transactional
    public AdminReminderResult updateReminder(
            UUID reminderId,
            UUID adminId,
            String title,
            String body,
            ReminderRepeat repeat,
            ReminderTarget target,
            Instant scheduledAt
    ) {
        validateAdminId(adminId);
        Reminder reminder = reminderRepository.findByIdForUpdate(reminderId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.BUSINESS_ERROR,
                        "리마인더를 찾을 수 없습니다."));
        AdminAuditSnapshot beforeState = createReminderState(reminder);
        reminder.update(
                title,
                body,
                repeat,
                target,
                scheduledAt,
                clock.instant()
        );
        AdminAuditSnapshot afterState = createReminderState(reminder);
        // 같은 내용으로 수정하면 바뀐 것이 없으므로 감사 로그를 남기지 않는다.
        if (beforeState.values().equals(afterState.values())) {
            return AdminReminderResult.from(reminder);
        }
        // 응답의 수정 시각(@UpdateTimestamp)이 채워지도록 UPDATE를 먼저 실행한다.
        Reminder saved = reminderRepository.saveAndFlush(reminder);
        adminAuditLogService.createAuditLog(new AdminAuditLogCommand(
                adminId,
                AdminAction.REMINDER_UPDATED,
                AdminTargetType.REMINDER,
                saved.getId(),
                null,
                beforeState,
                afterState
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

    private void validatePagination(int page, int pageSize) {
        long offset = ((long) page - 1) * pageSize;
        if (page < 1
                || pageSize < 1
                || pageSize > MAX_PAGE_SIZE
                || offset > Integer.MAX_VALUE) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "조회 조건이 올바르지 않습니다.");
        }
    }

    private void validateAdminId(UUID adminId) {
        if (adminId == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "관리자 식별자가 필요합니다.");
        }
    }
}
