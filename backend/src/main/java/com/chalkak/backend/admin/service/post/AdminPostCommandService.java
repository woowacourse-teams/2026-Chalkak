package com.chalkak.backend.admin.service.post;

import com.chalkak.backend.admin.domain.AdminAction;
import com.chalkak.backend.admin.domain.AdminAuditLog;
import com.chalkak.backend.admin.domain.AdminAuditSnapshot;
import com.chalkak.backend.admin.domain.AdminTargetType;
import com.chalkak.backend.admin.service.audit.AdminAuditLogCommand;
import com.chalkak.backend.admin.service.audit.AdminAuditLogService;
import com.chalkak.backend.common.logging.LogFields;
import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.exception.NotFoundException;
import com.chalkak.backend.like.repository.PostLikeRepository;
import com.chalkak.backend.notification.service.UserNotificationService;
import com.chalkak.backend.post.domain.ModerationStatus;
import com.chalkak.backend.post.domain.Post;
import com.chalkak.backend.post.repository.PostRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminPostCommandService {

    private static final Logger MODERATION_LOG = LoggerFactory.getLogger("chalkak.moderation");
    private static final int MAX_REJECTION_REASON_LENGTH = 500;
    private static final String INVALID_MODERATION_REQUEST_MESSAGE = "게시물 검수 요청이 올바르지 않습니다.";
    private static final String INVALID_STATE_MESSAGE = "대기 중인 게시물만 검수할 수 있습니다.";
    private static final int MAX_REASON_LENGTH = 500;
    private static final String INVALID_DELETION_REQUEST_MESSAGE = "게시물 삭제 요청이 올바르지 않습니다.";

    private final PostRepository postRepository;
    private final PostLikeRepository postLikeRepository;
    private final AdminAuditLogService adminAuditLogService;
    private final UserNotificationService userNotificationService;

    @Transactional
    public AdminPostModerationResult moderate(
            UUID postId,
            UUID adminId,
            ModerationStatus status,
            String rejectionReason
    ) {
        String normalizedReason = validateAndNormalizeModeration(
                postId,
                adminId,
                status,
                rejectionReason);
        Post post = postRepository.findActiveByIdForUpdate(postId)
                .orElseThrow(() -> new NotFoundException(
                        ErrorCode.BUSINESS_ERROR,
                        "게시물을 찾을 수 없습니다."));
        validatePending(post);

        AdminAuditSnapshot beforeState = moderationBeforeState(post);
        Instant moderatedAt = Instant.now();
        decide(post, status, moderatedAt);
        logModerated(post, status, moderatedAt);
        AdminAuditSnapshot afterState = moderationAfterState(post, adminId);
        AdminAuditLog auditLog = adminAuditLogService.createAuditLog(new AdminAuditLogCommand(
                adminId,
                actionOf(status),
                AdminTargetType.POST,
                post.getId(),
                normalizedReason,
                beforeState,
                afterState));
        userNotificationService.createForModeration(
                post,
                auditLog.getId(),
                status,
                normalizedReason,
                moderatedAt);

        return new AdminPostModerationResult(
                post.getId(),
                post.getModerationStatus(),
                adminId,
                post.getModeratedAt(),
                normalizedReason);
    }

    @Transactional
    public void deletePost(
            UUID postId,
            UUID adminId,
            String reason
    ) {
        String normalizedReason = validateAndNormalizeDeletion(postId, adminId, reason);
        Post post = postRepository.findByIdForUpdate(postId)
                .orElseThrow(() -> new NotFoundException(
                        ErrorCode.BUSINESS_ERROR,
                        "게시물을 찾을 수 없습니다."));
        Instant requestedAt = Instant.now();
        if (post.getDeletedAt() != null) {
            post.getPhoto().delete(post.getDeletedAt());
            return;
        }

        AdminAuditSnapshot beforeState = deletionState(post);
        post.deleteByAdmin(requestedAt);
        postLikeRepository.deleteByPostId(postId);
        adminAuditLogService.createAuditLog(new AdminAuditLogCommand(
                adminId,
                AdminAction.POST_DELETED,
                AdminTargetType.POST,
                post.getId(),
                normalizedReason,
                beforeState,
                deletionState(post)));
    }

    private String validateAndNormalizeModeration(
            UUID postId,
            UUID adminId,
            ModerationStatus status,
            String rejectionReason
    ) {
        if (postId == null || adminId == null || !isDecision(status)) {
            throw invalidModerationRequestException();
        }
        if (status == ModerationStatus.APPROVED) {
            validateApprovedReason(rejectionReason);
            return null;
        }
        return normalizeRejectionReason(rejectionReason);
    }

    private boolean isDecision(ModerationStatus status) {
        return status == ModerationStatus.APPROVED || status == ModerationStatus.REJECTED;
    }

    private void validateApprovedReason(String rejectionReason) {
        if (rejectionReason != null) {
            throw invalidModerationRequestException();
        }
    }

    private String normalizeRejectionReason(String rejectionReason) {
        if (rejectionReason == null || rejectionReason.isBlank()) {
            throw invalidModerationRequestException();
        }
        String normalizedReason = rejectionReason.trim();
        if (normalizedReason.codePointCount(0,
                normalizedReason.length()) > MAX_REJECTION_REASON_LENGTH) {
            throw invalidModerationRequestException();
        }
        return normalizedReason;
    }

    private void validatePending(Post post) {
        if (post.getModerationStatus() != ModerationStatus.PENDING) {
            throw new BusinessException(
                    ErrorCode.RESOURCE_STATE_CHANGED,
                    INVALID_STATE_MESSAGE);
        }
    }

    private void decide(
            Post post,
            ModerationStatus status,
            Instant moderatedAt
    ) {
        if (status == ModerationStatus.APPROVED) {
            post.approve(moderatedAt);
            return;
        }
        post.reject(moderatedAt);
    }

    private void logModerated(Post post, ModerationStatus status, Instant moderatedAt) {
        MODERATION_LOG.atInfo()
                .addKeyValue(LogFields.TYPE, LogFields.TYPE_MODERATION)
                .addKeyValue(LogFields.EVENT, status.name().toLowerCase(Locale.ROOT))
                .addKeyValue(LogFields.POST_ID, post.getId())
                .addKeyValue(
                        LogFields.WAIT_SECONDS,
                        Duration.between(post.getCreatedAt(), moderatedAt).getSeconds())
                .log("검수 결정");
    }

    private AdminAction actionOf(ModerationStatus status) {
        if (status == ModerationStatus.APPROVED) {
            return AdminAction.POST_APPROVED;
        }
        return AdminAction.POST_REJECTED;
    }

    private AdminAuditSnapshot moderationBeforeState(Post post) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("moderationStatus", post.getModerationStatus());
        state.put("moderatedAt", post.getModeratedAt());
        return AdminAuditSnapshot.from(state);
    }

    private AdminAuditSnapshot moderationAfterState(Post post, UUID adminId) {
        return AdminAuditSnapshot.from(Map.of(
                "moderationStatus", post.getModerationStatus(),
                "moderatedAt", post.getModeratedAt(),
                "moderatedBy", adminId));
    }

    private BusinessException invalidModerationRequestException() {
        return new BusinessException(ErrorCode.BUSINESS_ERROR, INVALID_MODERATION_REQUEST_MESSAGE);
    }

    private AdminAuditSnapshot deletionState(Post post) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("moderationStatus", post.getModerationStatus());
        state.put("deletedAt", post.getDeletedAt());
        return AdminAuditSnapshot.from(state);
    }

    private String validateAndNormalizeDeletion(
            UUID postId,
            UUID adminId,
            String reason
    ) {
        if (postId == null || adminId == null || reason == null || reason.isBlank()) {
            throw invalidDeletionRequestException();
        }
        if (reason.codePointCount(0, reason.length()) > MAX_REASON_LENGTH) {
            throw invalidDeletionRequestException();
        }
        return reason.strip();
    }

    private BusinessException invalidDeletionRequestException() {
        return new BusinessException(ErrorCode.BUSINESS_ERROR, INVALID_DELETION_REQUEST_MESSAGE);
    }
}
