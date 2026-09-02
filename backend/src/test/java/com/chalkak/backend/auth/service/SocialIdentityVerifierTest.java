package com.chalkak.backend.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.chalkak.backend.auth.domain.SocialProvider;
import com.chalkak.backend.auth.domain.VerifiedSocialIdentity;
import com.chalkak.backend.exception.BusinessException;
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

    @Test
    @DisplayName("같은 제공자의 ID Token 검증기가 중복 등록되면 생성을 거부한다")
    void create_duplicateProvider_throwsIllegalStateException() {
        // Given
        IdTokenVerifier firstVerifier = mock(IdTokenVerifier.class);
        IdTokenVerifier secondVerifier = mock(IdTokenVerifier.class);
        given(firstVerifier.getProvider()).willReturn(SocialProvider.KAKAO);
        given(secondVerifier.getProvider()).willReturn(SocialProvider.KAKAO);

        // When & Then
        assertThatThrownBy(() -> new SocialIdentityVerifier(
                List.of(firstVerifier, secondVerifier)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("등록되지 않은 제공자로 검증하면 지원하지 않는 제공자 예외를 발생시킨다")
    void verify_unsupportedProvider_throwsBusinessException() {
        // Given
        IdTokenVerifier googleVerifier = mock(IdTokenVerifier.class);
        given(googleVerifier.getProvider()).willReturn(SocialProvider.GOOGLE);
        SocialIdentityVerifier socialIdentityVerifier = new SocialIdentityVerifier(
                List.of(googleVerifier));

        // When & Then
        assertThatThrownBy(() -> socialIdentityVerifier.verify(
                SocialProvider.KAKAO,
                "kakao-id-token"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("지원하지 않는 소셜 로그인 제공자입니다.");
    }
}
