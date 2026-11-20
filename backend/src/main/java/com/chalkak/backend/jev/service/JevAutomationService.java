package com.chalkak.backend.jev.service;

import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.jev.domain.JevModerationRun;
import com.chalkak.backend.jev.domain.JevPromptDraft;
import com.chalkak.backend.jev.domain.JevPromptRole;
import com.chalkak.backend.jev.domain.JevPromptVersion;
import com.chalkak.backend.jev.repository.JevPromptVersionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JevAutomationService {

    private final JevPromptVersionRepository prompts;
    private final boolean workerEnabled;

    public JevAutomationService(
            JevPromptVersionRepository prompts,
            @Value("${chalkak.jev.worker-enabled:false}") boolean workerEnabled
    ) {
        this.prompts = prompts;
        this.workerEnabled = workerEnabled;
    }

    public boolean isWorkerEnabled() {
        return workerEnabled;
    }

    public void lockControlForRead() {
        prompts.lockRoleForRead(JevPromptRole.JEV_TOPIC_CLASSIFICATION);
        prompts.lockRoleForRead(JevPromptRole.JEV_PHOTO_MODERATION);
    }

    public Selection selectActive() {
        if (!workerEnabled) {
            return new Selection(
                    null,
                    null,
                    "AUTOMATION_DISABLED",
                    0
            );
        }
        lockControlForRead();
        JevPromptVersion classification = prompts
                .findActiveByRole(JevPromptRole.JEV_TOPIC_CLASSIFICATION)
                .orElse(null);
        JevPromptVersion photo = prompts.findActiveByRole(JevPromptRole.JEV_PHOTO_MODERATION)
                .orElse(null);
        return new Selection(
                classification,
                photo,
                classification == null || photo == null ? "CONFIGURATION_MISSING" : null,
                prompts.findLatestDisabledRevision()
        );
    }

    public String findHoldReason(JevModerationRun run) {
        if (run.isPreview()) {
            return null;
        }
        if (!workerEnabled) {
            return "AUTOMATION_DISABLED";
        }
        // 이미지 준비 중 중단한 기록도 재시작/재활성화 후 자동 공개로 되돌리지 않는다.
        if (run.getEvaluation() != null
                && "AUTOMATION_DISABLED".equals(run.getEvaluation().error())) {
            return "AUTOMATION_DISABLED";
        }
        if (run.getClassificationPromptId() == null || run.getPhotoPromptId() == null) {
            return "CONFIGURATION_MISSING";
        }
        if (prompts.findActiveByRole(JevPromptRole.JEV_TOPIC_CLASSIFICATION).isEmpty()
                || prompts.findActiveByRole(JevPromptRole.JEV_PHOTO_MODERATION).isEmpty()
                || prompts.findLatestDisabledRevision() > run.getControlRevision()) {
            return "AUTOMATION_DISABLED";
        }
        return null;
    }

    public JevPromptDraft resolve(JevModerationRun run) {
        if (run.isPreview()) {
            return run.getPreviewDraft();
        }
        JevPromptVersion classification = prompts.findById(run.getClassificationPromptId())
                .orElseThrow(() -> new BusinessException(ErrorCode.BUSINESS_ERROR,
                        "분류 프롬프트를 찾을 수 없습니다."));
        JevPromptVersion photo = prompts.findById(run.getPhotoPromptId())
                .orElseThrow(() -> new BusinessException(ErrorCode.BUSINESS_ERROR,
                        "사진 프롬프트를 찾을 수 없습니다."));
        return new JevPromptDraft(
                classification.getModel(),
                classification.getConfiguration(),
                photo.getConfiguration()
        );
    }

    public record Selection(
            JevPromptVersion classification,
            JevPromptVersion photo,
            String manualReason,
            long controlRevision
    ) {
    }
}
