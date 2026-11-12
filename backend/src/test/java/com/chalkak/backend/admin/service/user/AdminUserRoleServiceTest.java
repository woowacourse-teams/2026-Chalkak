package com.chalkak.backend.admin.service.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.chalkak.backend.admin.service.audit.AdminAuditLogService;
import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.exception.ForbiddenException;
import com.chalkak.backend.user.domain.UserRole;
import com.chalkak.backend.user.repository.UserRepository;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class AdminUserRoleServiceTest {

    @ParameterizedTest
    @EnumSource(UserRole.class)
    @DisplayName("역할 변경이 비활성화된 동안에는 부여와 회수를 모두 거절한다")
    void updateRole_disabled_rejectsBeforeRoleChangeOrAudit(UserRole role) {
        // Given
        UserRepository userRepository = mock(UserRepository.class);
        AdminAuditLogService auditLogService = mock(AdminAuditLogService.class);
        AdminUserRoleService service = new AdminUserRoleService(
                userRepository,
                auditLogService,
                false
        );

        // When & Then
        assertThatThrownBy(() -> service.updateRole(
                UUID.randomUUID(),
                UUID.randomUUID(),
                role,
                "작가 권한 변경"
        )).isInstanceOfSatisfying(ForbiddenException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
        verifyNoInteractions(userRepository, auditLogService);
    }
}
