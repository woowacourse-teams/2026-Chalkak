package com.chalkak.backend.admin.domain;

import java.util.UUID;

public enum AdminAction {
    POST_APPROVED(
            AdminTargetType.POST,
            false,
            AdminAuditTransitionRules::isPostApproved
    ),
    POST_REJECTED(
            AdminTargetType.POST,
            true,
            AdminAuditTransitionRules::isPostRejected
    ),
    POST_DELETED(
            AdminTargetType.POST,
            true,
            AdminAuditTransitionRules::isPostDeleted
    ),
    USER_BANNED(
            AdminTargetType.USER,
            true,
            AdminAuditTransitionRules::isUserBanned
    ),
    USER_UNBANNED(
            AdminTargetType.USER,
            true,
            AdminAuditTransitionRules::isUserUnbanned
    ),
    TOPIC_CREATED(
            AdminTargetType.TOPIC,
            false,
            AdminAuditTransitionRules::isTopicCreated
    ),
    TOPIC_UPDATED(
            AdminTargetType.TOPIC,
            false,
            AdminAuditTransitionRules::isTopicUpdated
    ),
    TOPIC_DELETED(
            AdminTargetType.TOPIC,
            true,
            AdminAuditTransitionRules::isTopicDeleted
    );

    private final AdminTargetType targetType;
    private final boolean reasonRequired;
    private final StateChangeValidator stateChangeValidator;

    AdminAction(
            AdminTargetType targetType,
            boolean reasonRequired,
            StateChangeValidator stateChangeValidator
    ) {
        this.targetType = targetType;
        this.reasonRequired = reasonRequired;
        this.stateChangeValidator = stateChangeValidator;
    }

    public boolean isForTarget(AdminTargetType targetType) {
        return this.targetType == targetType;
    }

    public boolean isReasonRequired() {
        return reasonRequired;
    }

    boolean isValidStateChange(
            UUID actorAdminId,
            AdminAuditSnapshot beforeState,
            AdminAuditSnapshot afterState
    ) {
        return stateChangeValidator.isValid(actorAdminId, beforeState, afterState);
    }

    @FunctionalInterface
    private interface StateChangeValidator {

        boolean isValid(
                UUID actorAdminId,
                AdminAuditSnapshot beforeState,
                AdminAuditSnapshot afterState
        );
    }
}
