package com.chalkak.backend.auth.infrastructure.infra.oidc;

import static org.assertj.core.api.Assertions.assertThat;

import com.chalkak.backend.auth.domain.SocialProvider;
import com.chalkak.backend.auth.service.IdTokenVerifier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.validation.BindValidationException;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

class OidcIdTokenConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(OidcIdTokenConfig.class)
            .withPropertyValues(
                    "chalkak.auth.oidc.google.issuer=https://accounts.google.com",
                    "chalkak.auth.oidc.google.jwk-set-uri=https://www.googleapis.com/oauth2/v3/certs",
                    "chalkak.auth.oidc.google.client-id=google-client-id",
                    "chalkak.auth.oidc.kakao.issuer=https://kauth.kakao.com",
                    "chalkak.auth.oidc.kakao.jwk-set-uri=https://kauth.kakao.com/.well-known/jwks.json",
                    "chalkak.auth.oidc.kakao.app-key=kakao-app-key",
                    "chalkak.auth.oidc.apple.issuer=https://appleid.apple.com",
                    "chalkak.auth.oidc.apple.jwk-set-uri=https://appleid.apple.com/auth/keys",
                    "chalkak.auth.oidc.apple.client-id=apple-client-id");

    @Test
    @DisplayName("세 제공자의 OIDC 설정으로 디코더와 ID Token 검증기를 등록한다")
    void create_validProperties_registersDecodersAndVerifiers() {
        // When & Then
        contextRunner.run(context -> {
            assertThat(context.getBean("googleJwtDecoder")).isInstanceOf(NimbusJwtDecoder.class);
            assertThat(context.getBean("kakaoJwtDecoder")).isInstanceOf(NimbusJwtDecoder.class);
            assertThat(context.getBean("appleJwtDecoder")).isInstanceOf(NimbusJwtDecoder.class);
            assertThat(
                    context.getBean("googleIdTokenVerifier", IdTokenVerifier.class).getProvider())
                    .isEqualTo(SocialProvider.GOOGLE);
            assertThat(context.getBean("kakaoIdTokenVerifier", IdTokenVerifier.class).getProvider())
                    .isEqualTo(SocialProvider.KAKAO);
            assertThat(context.getBean("appleIdTokenVerifier", IdTokenVerifier.class).getProvider())
                    .isEqualTo(SocialProvider.APPLE);
        });
    }

    @Test
    @DisplayName("제공자의 필수 OIDC 설정이 없으면 애플리케이션을 시작하지 않는다")
    void create_missingRequiredProperty_failsToStart() {
        // When & Then
        contextRunner
                .withPropertyValues("chalkak.auth.oidc.apple.client-id=")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .rootCause()
                            .isInstanceOf(BindValidationException.class)
                            .hasMessageContaining("chalkak.auth.oidc.apple")
                            .hasMessageContaining("clientId");
                });
    }
}
