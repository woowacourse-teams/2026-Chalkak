package com.chalkak.backend.auth.infrastructure.infra.apple;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.validation.BindValidationException;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class AppleAuthPropertiesConfigTest {

    private static final String ENCRYPTION_KEY =
            "00112233445566778899aabbccddeeff00112233445566778899aabbccddeeff";

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(AppleAuthPropertiesConfig.class)
            .withPropertyValues(
                    "chalkak.auth.oidc.apple.issuer=https://appleid.apple.com",
                    "chalkak.auth.oidc.apple.jwk-set-uri=https://appleid.apple.com/auth/keys",
                    "chalkak.auth.oidc.apple.client-id=com.chalkak.ios",
                    "chalkak.auth.apple.token.team-id=APPLETEAM1",
                    "chalkak.auth.apple.token.key-id=APPLEKEY01",
                    "chalkak.auth.apple.token.private-key-base64=cHJpdmF0ZS1rZXk=",
                    "chalkak.auth.apple.token.token-uri=https://appleid.apple.com/auth/token",
                    "chalkak.auth.apple.token.revoke-uri=https://appleid.apple.com/auth/revoke",
                    "chalkak.auth.apple.refresh-token-encryption.key=" + ENCRYPTION_KEY
            );

    @Test
    @DisplayName("Apple refresh token 암호화 키가 64자리 16진수가 아니면 시작을 거부한다")
    void appleProperties_invalidEncryptionKey_rejectsStartup() {
        // Given
        ApplicationContextRunner invalidContextRunner = contextRunner
                .withPropertyValues("chalkak.auth.apple.refresh-token-encryption.key=too-short");

        // When & Then
        invalidContextRunner.run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(BindValidationException.class);
        });
    }
}
