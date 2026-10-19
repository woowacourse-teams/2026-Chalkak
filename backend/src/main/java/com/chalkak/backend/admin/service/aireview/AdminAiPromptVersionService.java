package com.chalkak.backend.admin.service.aireview;

import com.chalkak.backend.admin.domain.AdminAction;
import com.chalkak.backend.admin.domain.AdminAuditSnapshot;
import com.chalkak.backend.admin.domain.AdminTargetType;
import com.chalkak.backend.admin.service.audit.AdminAuditLogCommand;
import com.chalkak.backend.admin.service.audit.AdminAuditLogService;
import com.chalkak.backend.aireview.domain.AiPromptDraft;
import com.chalkak.backend.aireview.domain.AiPromptParameters;
import com.chalkak.backend.aireview.domain.AiPromptParameters.ImageDetail;
import com.chalkak.backend.aireview.domain.AiPromptParameters.ReasoningEffort;
import com.chalkak.backend.aireview.domain.AiPromptType;
import com.chalkak.backend.aireview.domain.AiPromptVersion;
import com.chalkak.backend.aireview.repository.AiPromptVersionRepository;
import com.chalkak.backend.aireview.service.AiReviewGenerator;
import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 프롬프트 버전을 새로 만든다. 만든 버전은 바꾸거나 지울 수 없고, 같은 용도에서 가장 큰 버전이 최신이다.
 */
@Service
@RequiredArgsConstructor
public class AdminAiPromptVersionService {

    private final AiPromptVersionRepository promptVersionRepository;
    private final AiReviewGenerator aiReviewGenerator;
    private final AdminAuditLogService auditLogService;

    @Transactional
    public VersionResult createVersion(UUID adminId, CreateCommand command) {
        AiPromptDraft draft = createDraft(command);
        validateModel(draft.model());
        promptVersionRepository.lockType(draft.type());
        int version = promptVersionRepository.findMaxVersion(draft.type()) + 1;
        AiPromptVersion saved = promptVersionRepository.save(
                AiPromptVersion.create(draft, version, command.memo(), adminId));
        auditLogService.createAuditLog(new AdminAuditLogCommand(
                adminId, AdminAction.AI_PROMPT_VERSION_CREATED, AdminTargetType.AI_PROMPT_VERSION,
                saved.getId(), null, AdminAuditSnapshot.from(Map.of()),
                AdminAuditSnapshot.from(Map.of("type", saved.getType(), "version", version))));
        return VersionResult.from(saved);
    }

    private AiPromptDraft createDraft(CreateCommand command) {
        return new AiPromptDraft(
                command.type(),
                command.systemPrompt(),
                command.userPromptTemplate(),
                command.model(),
                new AiPromptParameters(
                        command.imageDetail(),
                        command.maxOutputTokens(),
                        command.reasoningEffort(),
                        command.distinctivePerspectiveThreshold()));
    }

    private void validateModel(String model) {
        if (!aiReviewGenerator.supportsModel(model)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "허용되지 않은 모델입니다.");
        }
    }

    public record CreateCommand(
            AiPromptType type,
            String systemPrompt,
            String userPromptTemplate,
            String model,
            ImageDetail imageDetail,
            Integer maxOutputTokens,
            ReasoningEffort reasoningEffort,
            Double distinctivePerspectiveThreshold,
            String memo
    ) {
    }

    public record VersionResult(
            UUID id,
            AiPromptType type,
            int version,
            String systemPrompt,
            String userPromptTemplate,
            String model,
            AiPromptParameters parameters,
            String outputSchemaVersion,
            String memo,
            UUID createdByAdminId,
            Instant createdAt
    ) {

        private static VersionResult from(AiPromptVersion promptVersion) {
            return new VersionResult(
                    promptVersion.getId(),
                    promptVersion.getType(),
                    promptVersion.getVersion(),
                    promptVersion.getSystemPrompt(),
                    promptVersion.getUserPromptTemplate(),
                    promptVersion.getModel(),
                    promptVersion.getParameters(),
                    promptVersion.getOutputSchemaVersion(),
                    promptVersion.getMemo(),
                    promptVersion.getCreatedByAdminId(),
                    promptVersion.getCreatedAt());
        }
    }
}
