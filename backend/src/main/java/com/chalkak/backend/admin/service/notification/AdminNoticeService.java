package com.chalkak.backend.admin.service.notification;

import com.chalkak.backend.admin.domain.AdminAction;
import com.chalkak.backend.admin.domain.AdminAuditSnapshot;
import com.chalkak.backend.admin.domain.AdminTargetType;
import com.chalkak.backend.admin.service.audit.AdminAuditLogCommand;
import com.chalkak.backend.admin.service.audit.AdminAuditLogService;
import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.exception.NotFoundException;
import com.chalkak.backend.notification.domain.Notice;
import com.chalkak.backend.notification.repository.NoticeRepository;
import com.chalkak.backend.notification.repository.NoticeSlice;
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

    private static final int MAX_PAGE_SIZE = 100;

    private final NoticeRepository noticeRepository;
    private final AdminAuditLogService adminAuditLogService;
    private final Clock clock;

    @Transactional(readOnly = true)
    public AdminNoticeListResult getNotices(int page, int pageSize) {
        validatePagination(page, pageSize);
        NoticeSlice slice = noticeRepository.findNotices(page, pageSize);
        return AdminNoticeListResult.from(slice, page, pageSize);
    }

    @Transactional(readOnly = true)
    public AdminNoticeResult getNotice(UUID noticeId) {
        Notice notice = noticeRepository.findById(noticeId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.BUSINESS_ERROR,
                        "공지를 찾을 수 없습니다."));
        return AdminNoticeResult.from(notice);
    }

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
