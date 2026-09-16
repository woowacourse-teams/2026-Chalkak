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

    private static final String EMAIL_POLICY_FIELD = "emailPolicy";

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

    /**
     * 제공자별로 다른 것은 이메일 정책뿐이라, 어느 제공자에 어떤 정책이 연결됐는지를 여기서 고정한다. Kakao ID Token에는
     * email_verified가 없어 확인된 이메일만 받는 정책을 걸면 이메일이 항상 버려진다.
     */
    @Test
    @DisplayName("Google·Apple은 확인된 이메일만 받고 Kakao는 받은 이메일을 그대로 쓰도록 등록한다")
    void create_validProperties_registersProviderEmailPolicies() {
        // When & Then
        contextRunner.run(context -> {
            assertThat(context.getBean("googleIdTokenVerifier"))
                    .extracting(EMAIL_POLICY_FIELD)
                    .isEqualTo(OidcEmailPolicy.VERIFIED_ONLY);
            assertThat(context.getBean("appleIdTokenVerifier"))
                    .extracting(EMAIL_POLICY_FIELD)
                    .isEqualTo(OidcEmailPolicy.VERIFIED_ONLY);
            assertThat(context.getBean("kakaoIdTokenVerifier"))
                    .extracting(EMAIL_POLICY_FIELD)
                    .isEqualTo(OidcEmailPolicy.AS_PROVIDED);
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
