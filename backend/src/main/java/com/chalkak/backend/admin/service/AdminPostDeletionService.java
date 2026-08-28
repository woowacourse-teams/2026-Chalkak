package com.chalkak.backend.admin.service;

import com.chalkak.backend.admin.domain.AdminAction;
import com.chalkak.backend.admin.domain.AdminAuditSnapshot;
import com.chalkak.backend.admin.domain.AdminTargetType;
import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.exception.NotFoundException;
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
public class AdminPostDeletionService {

    private static final int MAX_REASON_LENGTH = 500;
    private static final String INVALID_REQUEST_MESSAGE =
            "게시물 삭제 요청이 올바르지 않습니다.";

    private final PostRepository postRepository;
    private final AdminAuditLogService adminAuditLogService;

    @Transactional
    public void deletePost(UUID postId, UUID adminId, String reason) {
        String normalizedReason = validateAndNormalize(postId, adminId, reason);
        Post post = postRepository.findByIdForUpdate(postId)
                .orElseThrow(() -> new NotFoundException(
                        ErrorCode.BUSINESS_ERROR,
                        "게시물을 찾을 수 없습니다."
                ));
        Instant requestedAt = Instant.now();
        if (post.getDeletedAt() != null) {
            post.getPhoto().delete(post.getDeletedAt());
            return;
        }

        AdminAuditSnapshot beforeState = deletionState(post);
        post.delete(requestedAt);
        adminAuditLogService.createAuditLog(new AdminAuditLogCommand(
                adminId,
                AdminAction.POST_DELETED,
                AdminTargetType.POST,
                post.getId(),
                normalizedReason,
                beforeState,
                deletionState(post),
                UUID.randomUUID()
        ));
    }

    private AdminAuditSnapshot deletionState(Post post) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("moderationStatus", post.getModerationStatus());
        state.put("deletedAt", post.getDeletedAt());
        return AdminAuditSnapshot.from(state);
    }

    private String validateAndNormalize(UUID postId, UUID adminId, String reason) {
        if (postId == null || adminId == null || reason == null || reason.isBlank()) {
            throw invalidRequestException();
        }
        if (reason.codePointCount(0, reason.length()) > MAX_REASON_LENGTH) {
            throw invalidRequestException();
        }
        return reason.strip();
    }

    private BusinessException invalidRequestException() {
        return new BusinessException(ErrorCode.BUSINESS_ERROR, INVALID_REQUEST_MESSAGE);
    }
}
