package com.chalkak.backend.admin.service.notification;

import com.chalkak.backend.admin.domain.AdminAction;
import com.chalkak.backend.admin.domain.AdminAuditSnapshot;
import com.chalkak.backend.admin.domain.AdminTargetType;
import com.chalkak.backend.admin.service.audit.AdminAuditLogCommand;
import com.chalkak.backend.admin.service.audit.AdminAuditLogService;
import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.notification.domain.Notice;
import com.chalkak.backend.notification.repository.NoticeRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminNoticeService {

    private final NoticeRepository noticeRepository;
    private final AdminAuditLogService adminAuditLogService;
    private final Clock clock;

    @Transactional
    public AdminNoticeResult createNotice(
            UUID adminId,
            String title,
            String body,
            Instant scheduledAt
    ) {
        validateAdminId(adminId);
        Notice notice = Notice.create(title, body, scheduledAt, clock.instant());
        Notice saved = noticeRepository.saveAndFlush(notice);
        adminAuditLogService.createAuditLog(new AdminAuditLogCommand(
                adminId, AdminAction.NOTICE_CREATED, AdminTargetType.NOTICE, saved.getId(), null,
                AdminAuditSnapshot.from(Map.of()), createNoticeState(saved)));
        return AdminNoticeResult.from(saved);
    }

    private AdminAuditSnapshot createNoticeState(Notice notice) {
        return AdminAuditSnapshot.from(Map.of(
                "title", notice.getTitle(),
                "body", notice.getBody(),
                "scheduledAt", notice.getScheduledAt().toString()));
    }

    private void validateAdminId(UUID adminId) {
        if (adminId == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "관리자 식별자가 필요합니다.");
        }
    }
}
