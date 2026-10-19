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
import com.chalkak.backend.notification.domain.Notice;
import com.chalkak.backend.notification.repository.NoticeRepository;
import com.chalkak.backend.notification.repository.NoticeSlice;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminNoticeService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_DELETION_REASON_LENGTH = 500;

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
                .orElseThrow(this::noticeNotFoundException);
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

    @Transactional
    public AdminNoticeResult updateNotice(
            UUID noticeId,
            UUID adminId,
            String title,
            String body,
            Instant scheduledAt
    ) {
        validateAdminId(adminId);
        Notice notice = noticeRepository.findByIdForUpdate(noticeId)
                .orElseThrow(this::noticeNotFoundException);
        AdminAuditSnapshot beforeState = createNoticeState(notice);
        notice.update(title, body, scheduledAt, clock.instant());
        AdminAuditSnapshot afterState = createNoticeState(notice);
        // 같은 내용으로 수정하면 바뀐 것이 없으므로 감사 로그를 남기지 않는다.
        if (beforeState.values().equals(afterState.values())) {
            return AdminNoticeResult.from(notice);
        }
        Notice saved = noticeRepository.saveAndFlush(notice);
        adminAuditLogService.createAuditLog(new AdminAuditLogCommand(
                adminId, AdminAction.NOTICE_UPDATED, AdminTargetType.NOTICE, saved.getId(), null,
                beforeState, afterState));
        return AdminNoticeResult.from(saved);
    }

    @Transactional
    public void deleteNotice(UUID noticeId, UUID adminId, String reason) {
        validateAdminId(adminId);
        String normalizedReason = normalizeDeletionReason(reason);
        Notice notice = noticeRepository.findByIdForUpdate(noticeId)
                .orElseThrow(this::noticeNotFoundException);
        // 이미 삭제한 공지는 최초 삭제 결과를 유지하고 감사 로그를 추가하지 않는다.
        if (notice.isDeleted()) {
            return;
        }
        AdminAuditSnapshot beforeState = createDeletionState(notice);
        notice.delete(clock.instant());
        adminAuditLogService.createAuditLog(new AdminAuditLogCommand(
                adminId, AdminAction.NOTICE_DELETED, AdminTargetType.NOTICE, notice.getId(),
                normalizedReason, beforeState, createDeletionState(notice)));
    }

    private String normalizeDeletionReason(String reason) {
        String normalized = reason == null ? "" : reason.trim();
        if (normalized.isEmpty()
                || normalized.codePointCount(0, normalized.length()) > MAX_DELETION_REASON_LENGTH) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    "공지 삭제 사유는 1자 이상 500자 이하여야 합니다.");
        }
        return normalized;
    }

    private AdminAuditSnapshot createDeletionState(Notice notice) {
        Map<String, Object> state = new LinkedHashMap<>(createNoticeState(notice).values());
        state.put("deletedAt", notice.getDeletedAt());
        return AdminAuditSnapshot.from(state);
    }

    // 본문 원문을 감사 로그에 노출하지 않도록 공통 UTF-8 SHA-256 해시로 기록한다.
    private AdminAuditSnapshot createNoticeState(Notice notice) {
        return AdminAuditSnapshot.from(Map.of(
                "title", notice.getTitle(),
                "scheduledAt", notice.getScheduledAt().toString(),
                "bodySha256", AdminAuditHash.createSha256(notice.getBody())));
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

    private NotFoundException noticeNotFoundException() {
        return new NotFoundException(ErrorCode.BUSINESS_ERROR, "공지를 찾을 수 없습니다.");
    }
}
