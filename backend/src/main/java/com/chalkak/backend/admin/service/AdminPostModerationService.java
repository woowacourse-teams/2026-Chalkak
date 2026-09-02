package com.chalkak.backend.admin.service;

import com.chalkak.backend.admin.domain.AdminAction;
import com.chalkak.backend.admin.domain.AdminAuditSnapshot;
import com.chalkak.backend.admin.domain.AdminTargetType;
import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.exception.NotFoundException;
import com.chalkak.backend.post.domain.ModerationStatus;
import com.chalkak.backend.post.domain.Post;
import com.chalkak.backend.post.repository.PostRepository;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminPostModerationService {

    private static final int MAX_REJECTION_REASON_LENGTH = 500;
    private static final String INVALID_REQUEST_MESSAGE =
            "게시물 검수 요청이 올바르지 않습니다.";
    private static final String INVALID_STATE_MESSAGE =
            "대기 중인 게시물만 검수할 수 있습니다.";

    private final PostRepository postRepository;
    private final AdminAuditLogService adminAuditLogService;

    @Transactional
    public AdminPostModerationResult moderate(
            UUID postId,
            UUID adminId,
            ModerationStatus status,
            String rejectionReason
    ) {
        String normalizedReason = validateAndNormalize(
                postId,
                adminId,
                status,
                rejectionReason
        );
        Post post = postRepository.findActiveByIdForUpdate(postId)
                .orElseThrow(() -> new NotFoundException(
                        ErrorCode.BUSINESS_ERROR,
                        "게시물을 찾을 수 없습니다."
                ));
        validatePending(post);

        AdminAuditSnapshot beforeState = moderationBeforeState(post);
        Instant moderatedAt = Instant.now();
        decide(post, status, moderatedAt);
        AdminAuditSnapshot afterState = moderationAfterState(post, adminId);
        adminAuditLogService.createAuditLog(new AdminAuditLogCommand(
                adminId,
                actionOf(status),
                AdminTargetType.POST,
                post.getId(),
                normalizedReason,
                beforeState,
                afterState,
                UUID.randomUUID()
        ));

        return new AdminPostModerationResult(
                post.getId(),
                post.getModerationStatus(),
                adminId,
                post.getModeratedAt(),
                normalizedReason
        );
    }

    private String validateAndNormalize(
            UUID postId,
            UUID adminId,
            ModerationStatus status,
            String rejectionReason
    ) {
        if (postId == null || adminId == null || !isDecision(status)) {
            throw invalidRequestException();
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
            throw invalidRequestException();
        }
    }

    private String normalizeRejectionReason(String rejectionReason) {
        if (rejectionReason == null || rejectionReason.isBlank()) {
            throw invalidRequestException();
        }
        String normalizedReason = rejectionReason.trim();
        if (normalizedReason.codePointCount(0, normalizedReason.length())
                > MAX_REJECTION_REASON_LENGTH) {
            throw invalidRequestException();
        }
        return normalizedReason;
    }

    private void validatePending(Post post) {
        if (post.getModerationStatus() != ModerationStatus.PENDING) {
            throw new BusinessException(
                    ErrorCode.RESOURCE_STATE_CHANGED,
                    INVALID_STATE_MESSAGE
            );
        }
    }

    private void decide(Post post, ModerationStatus status, Instant moderatedAt) {
        if (status == ModerationStatus.APPROVED) {
            post.approve(moderatedAt);
            return;
        }
        post.reject(moderatedAt);
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
                "moderatedBy", adminId
        ));
    }

    private BusinessException invalidRequestException() {
        return new BusinessException(ErrorCode.BUSINESS_ERROR, INVALID_REQUEST_MESSAGE);
    }
}
