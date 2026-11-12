package com.chalkak.backend.admin.service.user;

import com.chalkak.backend.admin.domain.AdminAction;
import com.chalkak.backend.admin.domain.AdminAuditSnapshot;
import com.chalkak.backend.admin.domain.AdminTargetType;
import com.chalkak.backend.admin.service.audit.AdminAuditLogCommand;
import com.chalkak.backend.admin.service.audit.AdminAuditLogService;
import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.exception.ForbiddenException;
import com.chalkak.backend.exception.NotFoundException;
import com.chalkak.backend.user.domain.User;
import com.chalkak.backend.user.domain.UserRole;
import com.chalkak.backend.user.repository.UserRepository;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminUserRoleService {

    private final UserRepository userRepository;
    private final AdminAuditLogService adminAuditLogService;
    private final boolean roleChangeEnabled;

    public AdminUserRoleService(
            UserRepository userRepository,
            AdminAuditLogService adminAuditLogService,
            @Value("${chalkak.admin.user-role-change-enabled}") boolean roleChangeEnabled
    ) {
        this.userRepository = userRepository;
        this.adminAuditLogService = adminAuditLogService;
        this.roleChangeEnabled = roleChangeEnabled;
    }

    @Transactional
    public AdminUserRoleResult updateRole(
            UUID userId,
            UUID adminId,
            UserRole role,
            String reason
    ) {
        if (!roleChangeEnabled) {
            throw new ForbiddenException(ErrorCode.FORBIDDEN, "현재 관리자 회원 역할 변경을 사용할 수 없습니다.");
        }
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new NotFoundException(
                        ErrorCode.BUSINESS_ERROR,
                        "사용자를 찾을 수 없습니다."));

        AdminAuditSnapshot beforeState = roleSnapshot(user);
        changeRole(user, role);
        adminAuditLogService.createAuditLog(new AdminAuditLogCommand(
                adminId,
                AdminAction.USER_ROLE_CHANGED,
                AdminTargetType.USER,
                user.getId(),
                reason,
                beforeState,
                roleSnapshot(user)
        ));
        return new AdminUserRoleResult(user.getId(), user.getRole());
    }

    private void changeRole(User user, UserRole role) {
        if (role == UserRole.ARTIST) {
            user.grantArtistRole();
            return;
        }
        if (role == UserRole.USER) {
            user.revokeArtistRole();
            return;
        }
        throw new BusinessException(ErrorCode.BUSINESS_ERROR, "변경할 역할이 올바르지 않습니다.");
    }

    private AdminAuditSnapshot roleSnapshot(User user) {
        return AdminAuditSnapshot.from(Map.of("role", user.getRole()));
    }
}
