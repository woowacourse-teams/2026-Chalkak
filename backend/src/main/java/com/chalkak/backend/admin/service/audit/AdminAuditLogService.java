package com.chalkak.backend.admin.service.audit;

import com.chalkak.backend.admin.domain.AdminAuditLog;
import com.chalkak.backend.admin.repository.audit.AdminAuditLogRepository;
import com.chalkak.backend.common.logging.CurrentRequestId;
import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminAuditLogService {

    private static final String INVALID_AUDIT_LOG_MESSAGE = "관리자 감사 로그 정보가 올바르지 않습니다.";

    private final AdminAuditLogRepository adminAuditLogRepository;
    private final CurrentRequestId currentRequestId;

    /**
     * 감사 로그에는 현재 요청의 requestId를 직접 붙인다. 호출하는 업무 서비스가 값을 넘기지 않아도 접근·에러 로그와 같은 값으로
     * 이어진다.
     *
     * <p>관리자 업무 변경과 같은 트랜잭션에서만 감사 로그를 생성한다. 독립 트랜잭션을 열지 않아 이후 업무가 실패하거나 감사 INSERT가
     * 실패하면 둘 다 함께 롤백된다.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public AdminAuditLog createAuditLog(AdminAuditLogCommand command) {
        if (command == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, INVALID_AUDIT_LOG_MESSAGE);
        }
        AdminAuditLog auditLog = AdminAuditLog.create(
                command.actorAdminId(),
                command.action(),
                command.targetType(),
                command.targetId(),
                command.reason(),
                command.beforeState(),
                command.afterState(),
                Instant.now(),
                currentRequestId.resolve());
        return adminAuditLogRepository.save(auditLog);
    }
}
