package com.chalkak.backend.admin.domain;

import com.chalkak.backend.post.domain.ModerationStatus;
import com.chalkak.backend.user.domain.UserStatus;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

final class AdminAuditTransitionRules {

    private static final int MAX_TOPIC_TITLE_LENGTH = 255;
    private static final Set<String> POST_MODERATION_BEFORE_FIELDS = Set.of(
            "moderationStatus",
            "moderatedAt"
    );
    private static final Set<String> POST_MODERATION_AFTER_FIELDS = Set.of(
            "moderationStatus",
            "moderatedAt",
            "moderatedBy"
    );
    private static final Set<String> POST_DELETION_FIELDS = Set.of(
            "moderationStatus",
            "deletedAt"
    );
    private static final Set<String> USER_STATUS_FIELDS = Set.of("status");
    private static final Set<String> TOPIC_STATE_FIELDS = Set.of(
            "title",
            "topicDate",
            "startsAt",
            "endsAt"
    );
    private static final Set<String> DELETED_TOPIC_STATE_FIELDS = Set.of(
            "title",
            "topicDate",
            "startsAt",
            "endsAt",
            "deletedAt"
    );

    private AdminAuditTransitionRules() {
    }

    static boolean isPostApproved(
            UUID actorAdminId,
            AdminAuditSnapshot beforeState,
            AdminAuditSnapshot afterState
    ) {
        return isPostModeration(
                actorAdminId,
                beforeState,
                afterState,
                ModerationStatus.APPROVED
        );
    }

    static boolean isPostRejected(
            UUID actorAdminId,
            AdminAuditSnapshot beforeState,
            AdminAuditSnapshot afterState
    ) {
        return isPostModeration(
                actorAdminId,
                beforeState,
                afterState,
                ModerationStatus.REJECTED
        );
    }

    static boolean isPostDeleted(
            UUID actorAdminId,
            AdminAuditSnapshot beforeState,
            AdminAuditSnapshot afterState
    ) {
        Object beforeStatus = beforeState.value("moderationStatus");
        return beforeState.hasExactlyFields(POST_DELETION_FIELDS)
                && afterState.hasExactlyFields(POST_DELETION_FIELDS)
                && isModerationStatus(beforeStatus)
                && Objects.equals(beforeStatus, afterState.value("moderationStatus"))
                && beforeState.value("deletedAt") == null
                && isInstant(afterState.value("deletedAt"));
    }

    static boolean isUserBanned(
            UUID actorAdminId,
            AdminAuditSnapshot beforeState,
            AdminAuditSnapshot afterState
    ) {
        return isUserStatusChange(
                beforeState,
                afterState,
                UserStatus.ACTIVE,
                UserStatus.BANNED
        );
    }

    static boolean isUserUnbanned(
            UUID actorAdminId,
            AdminAuditSnapshot beforeState,
            AdminAuditSnapshot afterState
    ) {
        return isUserStatusChange(
                beforeState,
                afterState,
                UserStatus.BANNED,
                UserStatus.ACTIVE
        );
    }

    static boolean isTopicCreated(
            UUID actorAdminId,
            AdminAuditSnapshot beforeState,
            AdminAuditSnapshot afterState
    ) {
        return beforeState.isEmpty()
                && isValidTopicState(afterState, TOPIC_STATE_FIELDS);
    }

    static boolean isTopicUpdated(
            UUID actorAdminId,
            AdminAuditSnapshot beforeState,
            AdminAuditSnapshot afterState
    ) {
        return isValidTopicState(beforeState, TOPIC_STATE_FIELDS)
                && isValidTopicState(afterState, TOPIC_STATE_FIELDS)
                && !beforeState.values().equals(afterState.values());
    }

    static boolean isTopicDeleted(
            UUID actorAdminId,
            AdminAuditSnapshot beforeState,
            AdminAuditSnapshot afterState
    ) {
        return isValidTopicState(beforeState, DELETED_TOPIC_STATE_FIELDS)
                && isValidTopicState(afterState, DELETED_TOPIC_STATE_FIELDS)
                && beforeState.value("deletedAt") == null
                && isInstant(afterState.value("deletedAt"))
                && hasSameTopicBusinessState(beforeState, afterState);
    }

    private static boolean isPostModeration(
            UUID actorAdminId,
            AdminAuditSnapshot beforeState,
            AdminAuditSnapshot afterState,
            ModerationStatus decidedStatus
    ) {
        return beforeState.hasExactlyFields(POST_MODERATION_BEFORE_FIELDS)
                && afterState.hasExactlyFields(POST_MODERATION_AFTER_FIELDS)
                && hasValue(
                        beforeState,
                        "moderationStatus",
                        ModerationStatus.PENDING.name()
                )
                && beforeState.value("moderatedAt") == null
                && hasValue(afterState, "moderationStatus", decidedStatus.name())
                && isInstant(afterState.value("moderatedAt"))
                && hasValue(afterState, "moderatedBy", actorAdminId.toString());
    }

    private static boolean isUserStatusChange(
            AdminAuditSnapshot beforeState,
            AdminAuditSnapshot afterState,
            UserStatus beforeStatus,
            UserStatus afterStatus
    ) {
        return beforeState.hasExactlyFields(USER_STATUS_FIELDS)
                && afterState.hasExactlyFields(USER_STATUS_FIELDS)
                && hasValue(beforeState, "status", beforeStatus.name())
                && hasValue(afterState, "status", afterStatus.name());
    }

    private static boolean isValidTopicState(
            AdminAuditSnapshot state,
            Set<String> expectedFields
    ) {
        Instant startsAt = parseInstant(state.value("startsAt"));
        Instant endsAt = parseInstant(state.value("endsAt"));
        return state.hasExactlyFields(expectedFields)
                && isValidTitle(state.value("title"))
                && isLocalDate(state.value("topicDate"))
                && startsAt != null
                && endsAt != null
                && endsAt.isAfter(startsAt);
    }

    private static boolean hasSameTopicBusinessState(
            AdminAuditSnapshot beforeState,
            AdminAuditSnapshot afterState
    ) {
        Map<String, Object> beforeValues = beforeState.values();
        Map<String, Object> afterValues = afterState.values();
        return TOPIC_STATE_FIELDS.stream()
                .allMatch(fieldName -> Objects.equals(
                        beforeValues.get(fieldName),
                        afterValues.get(fieldName)
                ));
    }

    private static boolean hasValue(
            AdminAuditSnapshot state,
            String fieldName,
            Object expectedValue
    ) {
        return Objects.equals(state.value(fieldName), expectedValue);
    }

    private static boolean isModerationStatus(Object value) {
        if (!(value instanceof String status)) {
            return false;
        }
        return Arrays.stream(ModerationStatus.values())
                .map(Enum::name)
                .anyMatch(status::equals);
    }

    private static boolean isValidTitle(Object value) {
        if (!(value instanceof String title)) {
            return false;
        }
        return !title.isBlank()
                && title.codePointCount(0, title.length()) <= MAX_TOPIC_TITLE_LENGTH;
    }

    private static boolean isLocalDate(Object value) {
        if (!(value instanceof String date)) {
            return false;
        }
        try {
            LocalDate.parse(date);
            return true;
        } catch (DateTimeException exception) {
            return false;
        }
    }

    private static boolean isInstant(Object value) {
        return parseInstant(value) != null;
    }

    private static Instant parseInstant(Object value) {
        if (!(value instanceof String instant)) {
            return null;
        }
        try {
            return Instant.parse(instant);
        } catch (DateTimeException exception) {
            return null;
        }
    }
}
