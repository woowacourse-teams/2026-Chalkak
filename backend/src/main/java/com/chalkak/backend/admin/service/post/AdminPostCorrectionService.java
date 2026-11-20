package com.chalkak.backend.admin.service.post;

import com.chalkak.backend.admin.domain.AdminAction;
import com.chalkak.backend.admin.domain.AdminAuditSnapshot;
import com.chalkak.backend.admin.domain.AdminTargetType;
import com.chalkak.backend.admin.service.audit.AdminAuditLogCommand;
import com.chalkak.backend.admin.service.audit.AdminAuditLogService;
import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.exception.NotFoundException;
import com.chalkak.backend.notification.service.NotificationService;
import com.chalkak.backend.post.domain.ModerationStatus;
import com.chalkak.backend.post.domain.Post;
import com.chalkak.backend.post.repository.PostRepository;
import com.chalkak.backend.user.repository.UserRepository;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminPostCorrectionService {

    private final PostRepository posts;
    private final UserRepository users;
    private final AdminAuditLogService audits;
    private final NotificationService notifications;
    private final Clock clock;

    @Transactional
    public AdminPostModerationResult correct(
            UUID postId,
            UUID adminId,
            ModerationStatus status,
            String reason
    ) {
        if (adminId == null
                || (status != ModerationStatus.APPROVED && status != ModerationStatus.REJECTED)
                || reason == null || reason.isBlank()
                || reason.strip().codePointCount(0, reason.strip().length()) > 500) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "정정 결과와 500자 이하 사유가 필요합니다.");
        }
        UUID authorId = posts.findAuthorIdByPostId(postId).orElseThrow(() -> new NotFoundException(
                ErrorCode.BUSINESS_ERROR, "게시물을 찾을 수 없습니다."));
        users.findByIdForUpdate(authorId);
        Post post = posts.findByIdForUpdate(postId).orElseThrow();
        if (post.getDeletedAt() != null || post.getPhoto().getDeletedAt() != null
                || post.getTopic().getDeletedAt() != null || post.getAuthor().isDeleted()) {
            throw new BusinessException(ErrorCode.RESOURCE_STATE_CHANGED, "삭제된 게시물은 정정할 수 없습니다.");
        }
        if (post.getModeratedAt() == null
                || (post.getModerationStatus() != ModerationStatus.APPROVED
                        && post.getModerationStatus() != ModerationStatus.REJECTED)) {
            throw new BusinessException(ErrorCode.RESOURCE_STATE_CHANGED, "확정된 검수 결과만 정정할 수 있습니다.");
        }
        if (post.getModerationStatus() == status) {
            return result(
                    post,
                    null,
                    null
            );
        }
        if (status == ModerationStatus.APPROVED
                && posts.findActiveByAuthorIdAndTopicIdForUpdate(authorId, post.getTopic().getId())
                        .filter(active -> !active.getId().equals(postId)).isPresent()) {
            throw new BusinessException(ErrorCode.RESOURCE_STATE_CHANGED,
                    "이미 다른 활성 게시물이 있어 승인할 수 없습니다.");
        }
        var before = state(post);
        post.correctModeration(status, clock.instant());
        var audit = audits.createAuditLog(new AdminAuditLogCommand(
                adminId,
                AdminAction.POST_MODERATION_CORRECTED,
                AdminTargetType.POST,
                postId,
                reason.strip(),
                before,
                AdminAuditSnapshot.from(Map.of(
                        "moderationStatus",
                        post.getModerationStatus(),
                        "moderatedAt",
                        post.getModeratedAt(),
                        "moderatedBy",
                        adminId
                ))
        ));
        notifications.createForCorrection(
                post,
                audit.getId(),
                status,
                reason.strip(),
                post.getModeratedAt()
        );
        return result(
                post,
                adminId,
                reason.strip()
        );
    }

    private AdminAuditSnapshot state(Post post) {
        return AdminAuditSnapshot.from(Map.of(
                "moderationStatus",
                post.getModerationStatus(),
                "moderatedAt",
                post.getModeratedAt()
        ));
    }

    private AdminPostModerationResult result(
            Post post,
            UUID adminId,
            String reason
    ) {
        return new AdminPostModerationResult(
                post.getId(),
                post.getModerationStatus(),
                adminId,
                post.getModeratedAt(),
                post.getModerationStatus() == ModerationStatus.REJECTED ? reason : null
        );
    }
}
