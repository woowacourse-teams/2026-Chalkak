package com.chalkak.backend.admin.service;

import com.chalkak.backend.admin.domain.AdminAction;
import com.chalkak.backend.admin.domain.AdminAuditSnapshot;
import com.chalkak.backend.admin.domain.AdminTargetType;
import com.chalkak.backend.auth.service.SocialIdentityRestrictionService;
import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.exception.NotFoundException;
import com.chalkak.backend.user.domain.User;
import com.chalkak.backend.user.domain.UserStatus;
import com.chalkak.backend.user.repository.UserRepository;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminUserStatusService {

    private static final int MAX_REASON_LENGTH = 500;
    private static final String INVALID_REQUEST_MESSAGE =
            "사용자 상태 변경 요청이 올바르지 않습니다.";

    private final UserRepository userRepository;
    private final AdminAuditLogService adminAuditLogService;
    private final SocialIdentityRestrictionService socialIdentityRestrictionService;

    @Transactional
    public AdminUserStatusResult updateStatus(
            UUID userId,
            UUID adminId,
            UserStatus status,
            String reason
    ) {
        String normalizedReason = validateAndNormalize(userId, adminId, status, reason);
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new NotFoundException(
                        ErrorCode.BUSINESS_ERROR,
                        "사용자를 찾을 수 없습니다."
                ));

        AdminAuditSnapshot beforeState = statusSnapshot(user);
        changeStatus(user, status);
        AdminAuditSnapshot afterState = statusSnapshot(user);
        adminAuditLogService.createAuditLog(new AdminAuditLogCommand(
                adminId,
                actionOf(status),
                AdminTargetType.USER,
                user.getId(),
                normalizedReason,
                beforeState,
                afterState,
                UUID.randomUUID()
        ));

        return new AdminUserStatusResult(user.getId(), user.getStatus());
    }

    private String validateAndNormalize(
            UUID userId,
            UUID adminId,
            UserStatus status,
            String reason
    ) {
        if (userId == null || adminId == null || status == null
                || reason == null || reason.isBlank()) {
            throw invalidRequestException();
        }
        String normalizedReason = reason.trim();
        if (normalizedReason.codePointCount(0, normalizedReason.length()) > MAX_REASON_LENGTH) {
            throw invalidRequestException();
        }
        return normalizedReason;
    }

    private void changeStatus(User user, UserStatus status) {
        if (status == UserStatus.BANNED) {
            user.ban();
            socialIdentityRestrictionService.block(user.getId());
            return;
        }
        user.unban();
        socialIdentityRestrictionService.unblock(user.getId());
    }

    private AdminAction actionOf(UserStatus status) {
        if (status == UserStatus.BANNED) {
            return AdminAction.USER_BANNED;
        }
        return AdminAction.USER_UNBANNED;
    }

    private AdminAuditSnapshot statusSnapshot(User user) {
        return AdminAuditSnapshot.from(Map.of("status", user.getStatus()));
    }

    private BusinessException invalidRequestException() {
        return new BusinessException(ErrorCode.BUSINESS_ERROR, INVALID_REQUEST_MESSAGE);
    }
}
