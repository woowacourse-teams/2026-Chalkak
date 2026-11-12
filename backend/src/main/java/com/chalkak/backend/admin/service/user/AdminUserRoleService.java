package com.chalkak.backend.admin.service.user;

import com.chalkak.backend.admin.domain.AdminAction;
import com.chalkak.backend.admin.domain.AdminAuditSnapshot;
import com.chalkak.backend.admin.domain.AdminTargetType;
import com.chalkak.backend.admin.service.audit.AdminAuditLogCommand;
import com.chalkak.backend.admin.service.audit.AdminAuditLogService;
import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.exception.NotFoundException;
import com.chalkak.backend.user.domain.User;
import com.chalkak.backend.user.domain.UserRole;
import com.chalkak.backend.user.repository.UserRepository;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminUserRoleService {

    private final UserRepository userRepository;
    private final AdminAuditLogService adminAuditLogService;

    @Transactional
    public AdminUserRoleResult updateRole(
            UUID userId,
            UUID adminId,
            UserRole role,
            String reason
    ) {
        if (role != UserRole.ARTIST) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "부여할 수 있는 역할은 ARTIST입니다.");
        }
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new NotFoundException(
                        ErrorCode.BUSINESS_ERROR,
                        "사용자를 찾을 수 없습니다."));

        AdminAuditSnapshot beforeState = roleSnapshot(user);
        user.grantArtistRole();
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

    private AdminAuditSnapshot roleSnapshot(User user) {
        return AdminAuditSnapshot.from(Map.of("role", user.getRole()));
    }
}
