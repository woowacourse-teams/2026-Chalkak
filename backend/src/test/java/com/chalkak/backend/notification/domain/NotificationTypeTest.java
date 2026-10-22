package com.chalkak.backend.notification.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.chalkak.backend.user.domain.User;
import com.chalkak.backend.user.domain.UserFixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class NotificationTypeTest {

    @ParameterizedTest
    @CsvSource({
            "POST_APPROVED,POST,true", "POST_APPROVED,TOPIC,false", "POST_APPROVED,NOTICE,false",
            "POST_REJECTED,POST,true", "POST_REJECTED,TOPIC,false", "POST_REJECTED,NOTICE,false",
            "TOPIC,TOPIC,true", "TOPIC,POST,false", "TOPIC,NOTICE,false",
            "NOTICE,NOTICE,true", "NOTICE,POST,false", "NOTICE,TOPIC,false",
            "REMINDER,REMINDER,true", "REMINDER,TOPIC,false", "TOPIC,REMINDER,false"})
    @DisplayName("알림 종류마다 정해진 대상 종류만 자기 대상으로 판단한다")
    void isSourceOf_eachType_matchesOnlyItsSourceType(
            NotificationType type,
            NotificationSourceType sourceType,
            boolean expected
    ) {
        // When & Then
        assertThat(type.isSourceOf(sourceType)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
            "POST_APPROVED,true,false,true", "POST_APPROVED,false,true,false",
            "POST_REJECTED,true,false,true", "POST_REJECTED,false,true,false",
            "TOPIC,false,true,true", "TOPIC,true,false,false",
            "REMINDER,false,true,true", "REMINDER,true,false,false"})
    @DisplayName("승인·반려는 검수 결과 설정을, 주제·리마인더는 주제 설정을 따른다")
    void isPushEnabledFor_settingType_followsItsSetting(
            NotificationType type,
            boolean moderationPushEnabled,
            boolean topicPushEnabled,
            boolean expected
    ) {
        // Given
        User user = UserFixture.create();
        user.updatePushPreferences(topicPushEnabled, moderationPushEnabled);

        // When & Then
        assertThat(type.isPushEnabledFor(user)).isEqualTo(expected);
    }

    @Test
    @DisplayName("공지는 수신 설정을 모두 꺼도 보낸다")
    void isPushEnabledFor_noticeWithAllSettingsOff_returnsTrue() {
        // Given
        User user = UserFixture.create();
        user.updatePushPreferences(false, false);

        // When & Then
        assertThat(NotificationType.NOTICE.isPushEnabledFor(user)).isTrue();
    }
}
