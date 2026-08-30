package com.chalkak.backend.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chalkak.backend.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class BannedSocialIdentityTest {

    private static final String SUBJECT_HMAC =
            "921c5d35312df654eaa8ec114fd1de5a156cbcc64b23ddb6a709a9423f90c218";

    @Test
    @DisplayName("소셜 제공자와 subject HMAC으로 차단 식별자를 생성한다")
    void create_validSocialIdentity_createsBannedIdentity() {
        // When
        BannedSocialIdentity bannedIdentity = BannedSocialIdentity.create(
                SocialProvider.GOOGLE,
                SUBJECT_HMAC);

        // Then
        assertThat(bannedIdentity.getProvider()).isEqualTo(SocialProvider.GOOGLE);
        assertThat(bannedIdentity.getSubjectHmac()).isEqualTo(SUBJECT_HMAC);
    }

    @Test
    @DisplayName("소셜 제공자가 없으면 차단 식별자를 생성할 수 없다")
    void create_missingProvider_throwsException() {
        // When & Then
        assertThatThrownBy(() -> BannedSocialIdentity.create(null, SUBJECT_HMAC))
                .isInstanceOf(BusinessException.class)
                .hasMessage("차단할 소셜 계정 식별 정보가 올바르지 않습니다.");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {
            "",
            " ",
            "ABCDEF1234567890ABCDEF1234567890ABCDEF1234567890ABCDEF1234567890",
            "abc123"
    })
    @DisplayName("subject HMAC 형식이 올바르지 않으면 차단 식별자를 생성할 수 없다")
    void create_invalidSubjectHmac_throwsException(String subjectHmac) {
        // When & Then
        assertThatThrownBy(() -> BannedSocialIdentity.create(
                SocialProvider.GOOGLE,
                subjectHmac))
                .isInstanceOf(BusinessException.class)
                .hasMessage("차단할 소셜 계정 식별 정보가 올바르지 않습니다.");
    }
}
