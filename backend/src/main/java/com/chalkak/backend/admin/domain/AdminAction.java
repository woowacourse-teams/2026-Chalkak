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
    ),

    TOPIC_PUSH_SCHEDULE_CREATED(
            AdminTargetType.TOPIC_PUSH_SCHEDULE,
            false,
            AdminAuditTransitionRules::isTopicPushScheduleCreated
    ),

    TOPIC_PUSH_SCHEDULE_UPDATED(
            AdminTargetType.TOPIC_PUSH_SCHEDULE,
            false,
            AdminAuditTransitionRules::isTopicPushScheduleUpdated
    ),

    TOPIC_PUSH_SCHEDULE_DELETED(
            AdminTargetType.TOPIC_PUSH_SCHEDULE,
            false,
            AdminAuditTransitionRules::isTopicPushScheduleDeleted
    ),

    NOTICE_CREATED(
            AdminTargetType.NOTICE,
            false,
            AdminAuditTransitionRules::isNoticeCreated
    ),

    NOTICE_UPDATED(
            AdminTargetType.NOTICE,
            false,
            AdminAuditTransitionRules::isNoticeUpdated
    ),

    NOTICE_DELETED(
            AdminTargetType.NOTICE,
            true,
            AdminAuditTransitionRules::isNoticeDeleted
    ),

    BANNER_CREATED(
            AdminTargetType.BANNER,
            false,
            AdminAuditTransitionRules::isBannerCreated
    ),

    BANNER_DELETED(
            AdminTargetType.BANNER,
            false,
            AdminAuditTransitionRules::isBannerDeleted
    ),

    AI_PROMPT_VERSION_CREATED(
            AdminTargetType.AI_PROMPT_VERSION,
            false,
            AdminAuditTransitionRules::isAiPromptVersionCreated
    ),

    REMINDER_CREATED(
            AdminTargetType.REMINDER,
            false,
            AdminAuditTransitionRules::isReminderCreated
    ),

    REMINDER_UPDATED(
            AdminTargetType.REMINDER,
            false,
            AdminAuditTransitionRules::isReminderUpdated
    ),

    REMINDER_DELETED(
            AdminTargetType.REMINDER,
            false,
            AdminAuditTransitionRules::isReminderDeleted
    ),

    APP_VERSION_CREATED(
            AdminTargetType.APP_VERSION,
            true,
            AdminAuditTransitionRules::isAppVersionCreated
    ),

    APP_VERSION_UPDATED(
            AdminTargetType.APP_VERSION,
            true,
            AdminAuditTransitionRules::isAppVersionUpdated
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
        return stateChangeValidator.isValid(
                actorAdminId,
                beforeState,
                afterState
        );
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
