package com.chalkak.backend.notification.service;

import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.exception.UnauthorizedException;
import com.chalkak.backend.user.domain.User;
import com.chalkak.backend.user.repository.UserRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationSettingsService {

    private final UserRepository userRepository;

    public NotificationSettingsResult getSettings(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UnauthorizedException(
                        ErrorCode.UNAUTHORIZED, "유효하지 않은 인증 정보입니다."));
        user.validateNotWithdrawn();
        return new NotificationSettingsResult(
                user.isTopicPushEnabled(), user.isModerationPushEnabled());
    }

    @Transactional
    public void updateSettings(
            UUID userId,
            Boolean topicPushEnabled,
            Boolean moderationPushEnabled
    ) {
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new UnauthorizedException(
                        ErrorCode.UNAUTHORIZED, "유효하지 않은 인증 정보입니다."));
        user.updatePushPreferences(
                topicPushEnabled == null ? user.isTopicPushEnabled() : topicPushEnabled,
                moderationPushEnabled == null
                        ? user.isModerationPushEnabled()
                        : moderationPushEnabled);
    }
}
