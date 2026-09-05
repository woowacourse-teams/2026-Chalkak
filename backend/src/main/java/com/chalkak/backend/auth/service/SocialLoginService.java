package com.chalkak.backend.auth.service;

import com.chalkak.backend.auth.domain.SocialProvider;
import com.chalkak.backend.auth.domain.VerifiedSocialIdentity;
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
            String idToken
    ) {
        VerifiedSocialIdentity identity = socialIdentityVerifier.verify(
                provider,
                idToken);

        return existingSocialAccountLoginProcessor.processIfExists(identity)
                .map(success -> SocialLoginResult.loginSuccess(
                        success.userId(),
                        success.accessToken(),
                        success.refreshToken()))
                .orElseGet(SocialLoginResult::signUpRequired);
    }
}
