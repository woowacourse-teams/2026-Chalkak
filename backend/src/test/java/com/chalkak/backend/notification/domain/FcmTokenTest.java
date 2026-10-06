package com.chalkak.backend.notification.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class FcmTokenTest {

    @Test
    @DisplayName("유효한 FCM 토큰의 원문을 변경하지 않고 중복 판별용 해시를 만든다")
    void constructor_validToken_preservesOpaqueValueAndCreatesHash() {
        // given
        String value = "opaque:FCM/token_-value";

        // when
        FcmToken token = new FcmToken(value);

        // then
        assertThat(token.getValue()).isEqualTo(value);
        assertThat(token.getHash()).matches("^[0-9a-f]{64}$");
        assertThat(new FcmToken(value).getHash()).isEqualTo(token.getHash());
        assertThat(new FcmToken(value + "2").getHash()).isNotEqualTo(token.getHash());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n"})
    @DisplayName("FCM 토큰이 없거나 공백뿐이면 등록할 수 없다")
    void constructor_missingToken_rejectsRegistration(String value) {
        // given
        // when & then
        assertThatThrownBy(() -> new FcmToken(value))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.BUSINESS_ERROR);
    }
}
