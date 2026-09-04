package com.chalkak.backend.auth.infrastructure.infra.apple;

import static org.assertj.core.api.Assertions.assertThat;

import com.chalkak.backend.auth.infrastructure.infra.oidc.apple.AppleOidcProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.validation.BindValidationException;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

/**
 * Apple 프로퍼티 바인딩 검증만 확인한다. {@code AppleTokenClientConfig}를 직접 쓰지 않는 것은,
 * 그 클래스의 {@code @Bean}이 실제 RestClient·AppleClientSecretGenerator까지 만들어 이
 * 테스트가 검증하려는 범위(프로퍼티 바인딩)를 넘어서기 때문이다. 여기서만 쓰는 최소 설정으로
 * 프로퍼티 3종만 등록한다.
 */
class AppleAuthPropertiesTest {

    private static final String VALID_ENCRYPTION_KEY =
            "00112233445566778899aabbccddeeff00112233445566778899aabbccddeeff";

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class)
            .withPropertyValues(
                    "chalkak.auth.oidc.apple.issuer=https://appleid.apple.com",
                    "chalkak.auth.oidc.apple.jwk-set-uri=https://appleid.apple.com/auth/keys",
                    "chalkak.auth.oidc.apple.client-id=com.chalkak.ios",
                    "chalkak.auth.apple.token.team-id=APPLETEAM1",
                    "chalkak.auth.apple.token.key-id=APPLEKEY01",
                    "chalkak.auth.apple.token.private-key-base64=cHJpdmF0ZS1rZXk=",
                    "chalkak.auth.apple.token.token-uri=https://appleid.apple.com/auth/token",
                    "chalkak.auth.apple.token.revoke-uri=https://appleid.apple.com/auth/revoke",
                    "chalkak.auth.apple.refresh-token-encryption.key=" + VALID_ENCRYPTION_KEY
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

    @Configuration
    @EnableConfigurationProperties({
            AppleOidcProperties.class,
            AppleTokenProperties.class,
            AppleRefreshTokenEncryptionProperties.class
    })
    static class TestConfig {
    }
}
