package com.chalkak.backend.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chalkak.backend.exception.BusinessException;
import java.time.Instant;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class PendingAppleAuthorizationTest {

    private static final String SUBJECT_HMAC = "a".repeat(64);
    private static final Instant EXPIRES_AT =
            Instant.parse("2026-09-04T00:05:00Z");

    @Test
    @DisplayName("신원 지문과 암호화된 RT, 만료 시각으로 임시 인증 정보를 만든다")
    void create_validArguments_createsAuthorization() {
        // When
        PendingAppleAuthorization authorization =
                PendingAppleAuthorization.create(
                        SUBJECT_HMAC,
                        "encrypted-refresh-token",
                        EXPIRES_AT);

        // Then
        assertThat(authorization.getSubjectHmac()).isEqualTo(SUBJECT_HMAC);
        assertThat(authorization.getEncryptedRefreshToken())
                .isEqualTo("encrypted-refresh-token");
        assertThat(authorization.getExpiresAt()).isEqualTo(EXPIRES_AT);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = " ")
    @DisplayName("암호화된 RT가 비어 있으면 임시 인증 정보를 만들 수 없다")
    void create_blankEncryptedRefreshToken_throwsBusinessException(
            String encryptedRefreshToken
    ) {
        assertInvalid(() -> PendingAppleAuthorization.create(
                SUBJECT_HMAC,
                encryptedRefreshToken,
                EXPIRES_AT));
    }

    @Test
    @DisplayName("암호화된 RT가 최대 길이를 넘으면 임시 인증 정보를 만들 수 없다")
    void create_tooLongEncryptedRefreshToken_throwsBusinessException() {
        assertInvalid(() -> PendingAppleAuthorization.create(
                SUBJECT_HMAC,
                "a".repeat(4097),
                EXPIRES_AT));
    }

    @ParameterizedTest
    @MethodSource("invalidSubjectHmacs")
    @DisplayName("신원 지문이 64자리 소문자 hex가 아니면 임시 인증 정보를 만들 수 없다")
    void create_invalidSubjectHmac_throwsBusinessException(String subjectHmac) {
        assertInvalid(() -> PendingAppleAuthorization.create(
                subjectHmac,
                "encrypted-refresh-token",
                EXPIRES_AT));
    }

    private static Stream<String> invalidSubjectHmacs() {
        return Stream.of(
                null,
                "",
                " ",
                "a".repeat(63),
                "a".repeat(65),
                "A".repeat(64),
                "g".repeat(64));
    }

    @Test
    @DisplayName("만료 시각이 없으면 임시 인증 정보를 만들 수 없다")
    void create_missingExpiry_throwsBusinessException() {
        assertInvalid(() -> PendingAppleAuthorization.create(
                SUBJECT_HMAC,
                "encrypted-refresh-token",
                null));
    }

    private void assertInvalid(ThrowingCallable callable) {
        assertThatThrownBy(callable::call)
                .isInstanceOf(BusinessException.class)
                .hasMessage("임시 Apple 인증 정보가 올바르지 않습니다.");
    }

    @FunctionalInterface
    private interface ThrowingCallable {

        void call();
    }
}
