package com.chalkak.backend.admin.service.jev;

import com.chalkak.backend.admin.domain.AdminAction;
import com.chalkak.backend.admin.domain.AdminAuditSnapshot;
import com.chalkak.backend.admin.domain.AdminTargetType;
import com.chalkak.backend.admin.service.audit.AdminAuditLogCommand;
import com.chalkak.backend.admin.service.audit.AdminAuditLogService;
import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.exception.NotFoundException;
import com.chalkak.backend.jev.domain.JevDecision;
import com.chalkak.backend.jev.domain.JevGoldLabel;
import com.chalkak.backend.jev.repository.JevJobRepository;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminJevEvaluationService {

    private final JevJobRepository jobs;
    private final AdminAuditLogService audits;
    private final Clock clock;

    @Transactional
    public JevGoldLabel evaluate(
            UUID id,
            UUID adminId,
            JevDecision decision,
            Map<String, JevDecision> questions,
            String reason,
            JevGoldLabel.Sampling sampling
    ) {
        JevGoldLabel label;
        try {
            label = new JevGoldLabel(
                    decision,
                    questions,
                    reason,
                    sampling,
                    adminId,
                    clock.instant()
            );
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "평가 정답과 근거를 확인해 주세요.");
        }
        var run = jobs.findRunForUpdate(id).orElseThrow(() -> new NotFoundException(
                ErrorCode.BUSINESS_ERROR, "검수 이력을 찾을 수 없습니다."));
        if (run.getFinishedAt() == null) {
            throw new BusinessException(ErrorCode.RESOURCE_STATE_CHANGED, "완료된 검수만 평가할 수 있습니다.");
        }
        var before = snapshot(id, run.getGoldLabel());
        run.evaluate(label);
        audits.createAuditLog(new AdminAuditLogCommand(
                adminId,
                AdminAction.JEV_EVALUATION_UPDATED,
                AdminTargetType.POST,
                run.getPostId(),
                label.reason(),
                before,
                snapshot(id, label)
        ));
        return label;
    }

    private AdminAuditSnapshot snapshot(UUID runId, JevGoldLabel label) {
        if (label == null) {
            return AdminAuditSnapshot.from(Map.of());
        }
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("runId", runId);
        state.put("decision", label.decision());
        state.put("topicDecision", label.questions().get("topic_fit"));
        state.put("safetyDecision", label.questions().get("image_safety"));
        state.put("expressionDecision", label.questions().get("image_expression"));
        state.put("sampling", label.sampling());
        state.put("evaluatedBy", label.evaluatedBy());
        state.put("evaluatedAt", label.evaluatedAt());
        state.put("evaluationReason", label.reason());
        return AdminAuditSnapshot.from(state);
    }
}
