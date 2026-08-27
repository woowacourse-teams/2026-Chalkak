package com.chalkak.backend.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.chalkak.backend.auth.domain.SocialProvider;
import com.chalkak.backend.auth.domain.VerifiedSocialIdentity;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SocialIdentityVerifierTest {

    @Test
    @DisplayName("Kakao 제공자로 검증하면 Kakao ID Token 검증 결과를 반환한다")
    void verify_kakaoProvider_returnsKakaoIdentity() {
        // Given
        IdTokenVerifier googleVerifier = mock(IdTokenVerifier.class);
        IdTokenVerifier kakaoVerifier = mock(IdTokenVerifier.class);
        VerifiedSocialIdentity kakaoIdentity = new VerifiedSocialIdentity(
                SocialProvider.KAKAO,
                "kakao-subject",
                "user@chalkak.test");
        given(googleVerifier.getProvider()).willReturn(SocialProvider.GOOGLE);
        given(kakaoVerifier.getProvider()).willReturn(SocialProvider.KAKAO);
        given(kakaoVerifier.verify("kakao-id-token")).willReturn(kakaoIdentity);
        SocialIdentityVerifier socialIdentityVerifier = new SocialIdentityVerifier(
                List.of(googleVerifier, kakaoVerifier));

        // When
        VerifiedSocialIdentity identity = socialIdentityVerifier.verify(
                SocialProvider.KAKAO,
                "kakao-id-token");

        // Then
        assertThat(identity).isEqualTo(kakaoIdentity);
    }
}
