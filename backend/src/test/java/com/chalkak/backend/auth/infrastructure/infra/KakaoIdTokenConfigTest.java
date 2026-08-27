package com.chalkak.backend.auth.infrastructure.infra;

import static org.assertj.core.api.Assertions.assertThat;

import com.chalkak.backend.auth.domain.SocialProvider;
import com.chalkak.backend.auth.service.IdTokenVerifier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

class KakaoIdTokenConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(KakaoIdTokenConfig.class)
            .withPropertyValues(
                    "chalkak.auth.oidc.kakao.issuer=https://kauth.kakao.com",
                    "chalkak.auth.oidc.kakao.jwk-set-uri=https://kauth.kakao.com/.well-known/jwks.json",
                    "chalkak.auth.oidc.kakao.app-key=kakao-app-key");

    @Test
    @DisplayName("Kakao OIDC 설정으로 디코더와 ID Token 검증기를 등록한다")
    void create_validProperties_registersDecoderAndVerifier() {
        // When & Then
        contextRunner.run(context -> {
            assertThat(context).hasBean("kakaoJwtDecoder");
            assertThat(context.getBean("kakaoJwtDecoder"))
                    .isInstanceOf(NimbusJwtDecoder.class);
            assertThat(context).hasBean("kakaoIdTokenVerifier");
            IdTokenVerifier verifier = context.getBean(
                    "kakaoIdTokenVerifier",
                    IdTokenVerifier.class);
            assertThat(verifier.getProvider()).isEqualTo(SocialProvider.KAKAO);
        });
    }
}
