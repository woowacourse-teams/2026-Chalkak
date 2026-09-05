package com.chalkak.backend.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chalkak.backend.exception.BusinessException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class ConsumedSignupTokenTest {

    private static final Instant EXPIRES_AT = Instant.parse("2026-09-04T00:05:00Z");

    @Test
    @DisplayName("jti와 만료 시각으로 소진 기록을 만든다")
    void create_validArguments_createsToken() {
        // Given
        String jti = UUID.randomUUID().toString();

        // When
        ConsumedSignupToken token = ConsumedSignupToken.create(jti, EXPIRES_AT);

        // Then
        assertThat(token.getJti()).isEqualTo(jti);
        assertThat(token.getExpiresAt()).isEqualTo(EXPIRES_AT);
        assertThat(token.getId()).isEqualTo(jti);
        assertThat(token.isNew()).isTrue();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = " ")
    @DisplayName("jti가 비어 있으면 생성할 수 없다")
    void create_blankJti_throwsBusinessException(String jti) {
        // When & Then
        assertThatThrownBy(() -> ConsumedSignupToken.create(jti, EXPIRES_AT))
                .isInstanceOf(BusinessException.class)
                .hasMessage("회원가입 토큰 식별자가 올바르지 않습니다.");
    }

    @Test
    @DisplayName("jti가 36자를 넘으면 생성할 수 없다")
    void create_jtiTooLong_throwsBusinessException() {
        // Given
        String tooLongJti = "a".repeat(37);

        // When & Then
        assertThatThrownBy(() -> ConsumedSignupToken.create(tooLongJti, EXPIRES_AT))
                .isInstanceOf(BusinessException.class)
                .hasMessage("회원가입 토큰 식별자가 올바르지 않습니다.");
    }

    @Test
    @DisplayName("36자인 jti는 경계값으로 허용한다")
    void create_jtiAtMaxLength_createsToken() {
        // Given
        String maxLengthJti = "a".repeat(36);

        // When
        ConsumedSignupToken token = ConsumedSignupToken.create(maxLengthJti, EXPIRES_AT);

        // Then
        assertThat(token.getJti()).isEqualTo(maxLengthJti);
    }

    @Test
    @DisplayName("만료 시각이 없으면 생성할 수 없다")
    void create_nullExpiresAt_throwsBusinessException() {
        // When & Then
        assertThatThrownBy(() -> ConsumedSignupToken.create(
                UUID.randomUUID().toString(),
                null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("회원가입 토큰 만료 시각이 올바르지 않습니다.");
    }
}
