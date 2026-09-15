package com.chalkak.backend.auth.service;

import com.chalkak.backend.auth.domain.SocialProvider;
import com.chalkak.backend.auth.domain.VerifiedSocialIdentity;
import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SocialLoginService {

    private final SocialIdentityVerifier socialIdentityVerifier;
    private final ExistingSocialAccountLoginProcessor existingSocialAccountLoginProcessor;

    /**
     * 트랜잭션을 열지 않는다. ID Token 검증은 외부 키 조회를 동반하므로 DB 커넥션을 잡은 채로
     * 기다리지 않아야 하고, 실제 DB 작업은 {@link ExistingSocialAccountLoginProcessor}가 자신의
     * 트랜잭션 안에서 처리한다.
     */
    public SocialLoginResult login(
            SocialProvider provider,
            String idToken,
            String rawNonce
    ) {
        validateNotApple(provider);
        VerifiedSocialIdentity identity = socialIdentityVerifier.verify(
                provider,
                idToken,
                rawNonce);

        return existingSocialAccountLoginProcessor.processIfExists(identity)
                .map(success -> SocialLoginResult.loginSuccess(
                        success.userId(),
                        success.accessToken(),
                        success.refreshToken()))
                .orElseGet(SocialLoginResult::signUpRequired);
    }

    /**
     * Apple 로그인은 신규 회원의 authorizationCode를 교환해 탈퇴 시 폐기할 Refresh Token을 보관해야 하므로 전용 엔드포인트로만
     * 받는다. ID Token 검증기 목록에는 Apple도 있어, 이 엔드포인트로 들어온 Apple 요청은 여기서 지금과 같은 응답으로 거절한다. 로그인
     * 엔드포인트를 합칠 때(#412) 제거한다.
     */
    private void validateNotApple(SocialProvider provider) {
        if (provider == SocialProvider.APPLE) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_ERROR,
                    "지원하지 않는 소셜 로그인 제공자입니다.");
        }
    }
}
