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

class PendingAppleAuthorizationTest {

    private static final UUID UPLOAD_ID = UUID.randomUUID();
    private static final Instant EXPIRES_AT =
            Instant.parse("2026-09-04T00:05:00Z");

    @Test
    @DisplayName("업로드 식별자와 암호화된 RT, 만료 시각으로 임시 인증 정보를 만든다")
    void create_validArguments_createsAuthorization() {
        // When
        PendingAppleAuthorization authorization =
                PendingAppleAuthorization.create(
                        UPLOAD_ID,
                        "encrypted-refresh-token",
                        EXPIRES_AT);

        // Then
        assertThat(authorization.getUploadId()).isEqualTo(UPLOAD_ID);
        assertThat(authorization.getEncryptedRefreshToken())
                .isEqualTo("encrypted-refresh-token");
        assertThat(authorization.getExpiresAt()).isEqualTo(EXPIRES_AT);
        assertThat(authorization.getId()).isEqualTo(UPLOAD_ID);
        assertThat(authorization.isNew()).isTrue();
    }

    @Test
    @DisplayName("저장되거나 조회된 임시 인증 정보는 기존 엔티티로 표시한다")
    void markNotNew_persistedAuthorization_marksExistingEntity() {
        PendingAppleAuthorization authorization =
                PendingAppleAuthorization.create(
                        UPLOAD_ID,
                        "encrypted-refresh-token",
                        EXPIRES_AT);

        authorization.markNotNew();

        assertThat(authorization.isNew()).isFalse();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = " ")
    @DisplayName("암호화된 RT가 비어 있으면 임시 인증 정보를 만들 수 없다")
    void create_blankEncryptedRefreshToken_throwsBusinessException(
            String encryptedRefreshToken
    ) {
        assertInvalid(() -> PendingAppleAuthorization.create(
                UPLOAD_ID,
                encryptedRefreshToken,
                EXPIRES_AT));
    }

    @Test
    @DisplayName("암호화된 RT가 최대 길이를 넘으면 임시 인증 정보를 만들 수 없다")
    void create_tooLongEncryptedRefreshToken_throwsBusinessException() {
        assertInvalid(() -> PendingAppleAuthorization.create(
                UPLOAD_ID,
                "a".repeat(4097),
                EXPIRES_AT));
    }

    @Test
    @DisplayName("업로드 식별자나 만료 시각이 없으면 임시 인증 정보를 만들 수 없다")
    void create_missingIdentifierOrExpiry_throwsBusinessException() {
        assertInvalid(() -> PendingAppleAuthorization.create(
                null,
                "encrypted-refresh-token",
                EXPIRES_AT));
        assertInvalid(() -> PendingAppleAuthorization.create(
                UPLOAD_ID,
                "encrypted-refresh-token",
                null));
    }

    @Test
    @DisplayName("만료 시각부터 임시 인증 정보를 만료된 것으로 판단한다")
    void isExpired_atBoundary_returnsTrue() {
        PendingAppleAuthorization authorization =
                PendingAppleAuthorization.create(
                        UPLOAD_ID,
                        "encrypted-refresh-token",
                        EXPIRES_AT);

        assertThat(authorization.isExpired(EXPIRES_AT.minusNanos(1))).isFalse();
        assertThat(authorization.isExpired(EXPIRES_AT)).isTrue();
        assertThat(authorization.isExpired(EXPIRES_AT.plusNanos(1))).isTrue();
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
