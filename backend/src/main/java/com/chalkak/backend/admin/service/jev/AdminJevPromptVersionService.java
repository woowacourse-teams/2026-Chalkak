package com.chalkak.backend.admin.service.jev;

import com.chalkak.backend.admin.domain.AdminAction;
import com.chalkak.backend.admin.domain.AdminAuditSnapshot;
import com.chalkak.backend.admin.domain.AdminTargetType;
import com.chalkak.backend.admin.service.audit.AdminAuditLogCommand;
import com.chalkak.backend.admin.service.audit.AdminAuditLogService;
import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.exception.NotFoundException;
import com.chalkak.backend.jev.domain.JevPromptConfiguration;
import com.chalkak.backend.jev.domain.JevPromptRole;
import com.chalkak.backend.jev.domain.JevPromptVersion;
import com.chalkak.backend.jev.repository.JevPromptVersionRepository;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminJevPromptVersionService {

    private final JevPromptVersionRepository repository;
    private final AdminAuditLogService auditLogService;
    private final Clock clock;

    @Transactional
    public JevPromptVersion createVersion(UUID adminId, CreateCommand command) {
        validateRole(command.role());
        repository.lockRole(command.role());
        JevPromptVersion saved = repository.save(JevPromptVersion.create(
                command.role(),
                command.model(),
                command.configuration(),
                repository.findMaxVersion(command.role()) + 1,
                command.memo(),
                adminId
        ));
        auditLogService.createAuditLog(new AdminAuditLogCommand(
                adminId,
                AdminAction.JEV_PROMPT_VERSION_CREATED,
                AdminTargetType.AI_PROMPT_VERSION,
                saved.getId(),
                null,
                AdminAuditSnapshot.from(Map.of()),
                AdminAuditSnapshot.from(Map.of(
                        "role",
                        saved.getRole(),
                        "version",
                        saved.getVersion()
                ))
        ));
        return saved;
    }

    public JevPromptVersion getVersion(UUID id) {
        return repository.findById(id).orElseThrow(() -> new NotFoundException(
                ErrorCode.BUSINESS_ERROR, "Jev 프롬프트 버전을 찾을 수 없습니다."));
    }

    public JevPromptVersionRepository.VersionPage getVersions(
            JevPromptRole role,
            int page,
            int pageSize
    ) {
        validateRole(role);
        if (page < 1 || pageSize < 1 || pageSize > 100
                || ((long) page - 1) * pageSize > Integer.MAX_VALUE) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "조회 조건이 올바르지 않습니다.");
        }
        return repository.findByRole(
                role,
                page,
                pageSize
        );
    }

    @Transactional
    public JevPromptVersion updateActivation(
            UUID adminId,
            UUID id,
            boolean active
    ) {
        JevPromptRole role = repository.findRoleById(id).orElseThrow(() -> new NotFoundException(
                ErrorCode.BUSINESS_ERROR, "Jev 프롬프트 버전을 찾을 수 없습니다."));
        // 두 역할을 항상 같은 순서로 잠가, 동시에 적용해도 서로 호환되는 조합을 유지한다.
        repository.lockRole(JevPromptRole.JEV_TOPIC_CLASSIFICATION);
        repository.lockRole(JevPromptRole.JEV_PHOTO_MODERATION);
        // 역할 잠금을 획득한 뒤 활성 상태를 읽어, 대기 중 바뀐 상태를 이전 엔티티로 덮지 않는다.
        JevPromptVersion target = getVersion(id);
        JevPromptVersion current = repository.findActiveByRole(role).orElse(null);
        if (active && current != null && current.getId().equals(id)) {
            return target;
        }
        if (!active && (current == null || !current.getId().equals(id))) {
            return target;
        }
        if (active) {
            validateCompatibleConfiguration(target);
        }
        AdminAuditSnapshot before = activationSnapshot(target.getRole(), current);
        if (current != null) {
            current.deactivate();
            repository.flush();
        }
        if (active) {
            target.activate(clock.instant());
        }
        repository.flush();
        auditLogService.createAuditLog(new AdminAuditLogCommand(
                adminId,
                AdminAction.JEV_PROMPT_ACTIVATION_UPDATED,
                AdminTargetType.AI_PROMPT_VERSION,
                target.getId(),
                null,
                before,
                activationSnapshot(target.getRole(), active ? target : null)
        ));
        return target;
    }

    private AdminAuditSnapshot activationSnapshot(JevPromptRole role, JevPromptVersion active) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("role", role);
        state.put("activeVersionId", active == null ? null : active.getId());
        return AdminAuditSnapshot.from(state);
    }

    private void validateCompatibleConfiguration(JevPromptVersion target) {
        JevPromptRole otherRole = target.getRole() == JevPromptRole.JEV_TOPIC_CLASSIFICATION
                ? JevPromptRole.JEV_PHOTO_MODERATION
                : JevPromptRole.JEV_TOPIC_CLASSIFICATION;
        JevPromptVersion other = repository.findActiveByRole(otherRole).orElse(null);
        if (other == null) {
            return;
        }
        JevPromptVersion classification = target.getRole() == JevPromptRole.JEV_TOPIC_CLASSIFICATION
                ? target
                : other;
        JevPromptVersion photo = target.getRole() == JevPromptRole.JEV_PHOTO_MODERATION
                ? target
                : other;
        if (!photo.getConfiguration().categoryRules().keySet().containsAll(
                classification.getConfiguration().questions().get("topic_category").criteria()
                        .keySet())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    "분류 선택지별 사진 검수 카테고리 규칙을 모두 입력해 주세요.");
        }
    }

    private void validateRole(JevPromptRole role) {
        if (role == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "Jev 프롬프트 용도를 지정해 주세요.");
        }
    }

    public record CreateCommand(
            JevPromptRole role,
            String model,
            JevPromptConfiguration configuration,
            String memo
    ) {
    }
}
