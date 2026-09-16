package com.chalkak.backend.auth.service;

import com.chalkak.backend.auth.domain.SocialProvider;
import com.chalkak.backend.auth.domain.VerifiedSocialIdentity;
import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.exception.UnauthorizedException;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SocialLoginService {

    private final SocialIdentityVerifier socialIdentityVerifier;
    private final AppleTokenClient appleTokenClient;
    private final AppleAuthorizationCipher authorizationCipher;
    private final AppleSignupAuthorizationService appleSignupAuthorizationService;
    private final ExistingSocialAccountLoginProcessor existingSocialAccountLoginProcessor;

    /**
     * 트랜잭션을 열지 않는다. ID Token 검증과 Apple 토큰 교환은 외부 호출을 동반하므로 DB 커넥션을
     * 잡은 채로 기다리지 않아야 한다. 실제 DB 작업은 {@link ExistingSocialAccountLoginProcessor}와
     * {@link AppleSignupAuthorizationService}가 각자의 트랜잭션 안에서 처리한다.
     */
    public SocialLoginResult login(
            SocialProvider provider,
            String idToken,
            String rawNonce,
            String authorizationCode
    ) {
        VerifiedSocialIdentity identity = socialIdentityVerifier.verify(
                provider,
                idToken,
                rawNonce);

        Optional<SocialLoginSuccess> loginSuccess = existingSocialAccountLoginProcessor
                .processIfExists(identity);
        if (loginSuccess.isPresent()) {
            return toLoginSuccess(loginSuccess.get());
        }
        prepareAppleSignup(identity, authorizationCode, rawNonce);
        return SocialLoginResult.signUpRequired();
    }

    private SocialLoginResult toLoginSuccess(SocialLoginSuccess success) {
        return SocialLoginResult.loginSuccess(
                success.userId(),
                success.accessToken(),
                success.refreshToken());
    }

    /**
     * Apple 신규 회원만 탈퇴 시 폐기할 Refresh Token을 미리 받아 둔다. 다른 제공자는 폐기할 토큰이
     * 없어 로그인에서 할 일이 없다.
     *
     * <p>기존 회원은 여기까지 오지 않는다. 교환할 때마다 Apple에 새 grant가 생기는데 우리는 마지막
     * 하나만 저장하므로, 로그인할 때마다 폐기할 수단이 없는 grant가 하나씩 쌓인다. 가입 시점에 이미
     * 저장해 두었으므로 다시 받을 이유가 없다. 만료 전 보관분이 있는 신규 회원을 건너뛰는 것도 같은
     * 이유이며, 이때 요청의 authorizationCode는 쓰이지 않는다. 신원은 ID Token의 서명과 nonce로
     * 이미 확인했고 code의 용도인 폐기용 토큰은 보관돼 있어 검증할 대상이 남지 않는다.
     */
    private void prepareAppleSignup(
            VerifiedSocialIdentity identity,
            String authorizationCode,
            String rawNonce
    ) {
        if (identity.provider() != SocialProvider.APPLE) {
            return;
        }
        if (appleSignupAuthorizationService.renewIfPresent(identity)) {
            return;
        }
        appleSignupAuthorizationService.store(
                identity,
                exchangeAuthorization(identity, authorizationCode, rawNonce));
    }

    private String exchangeAuthorization(
            VerifiedSocialIdentity identity,
            String authorizationCode,
            String rawNonce
    ) {
        AppleTokenExchangeResult exchangeResult = appleTokenClient
                .exchangeAuthorizationCode(authorizationCode);
        VerifiedSocialIdentity exchangedIdentity = socialIdentityVerifier.verify(
                SocialProvider.APPLE,
                exchangeResult.idToken(),
                rawNonce);
        validateSameSubject(identity, exchangedIdentity);

        return authorizationCipher.encrypt(exchangeResult.refreshToken());
    }

    private void validateSameSubject(
            VerifiedSocialIdentity identity,
            VerifiedSocialIdentity exchangedIdentity
    ) {
        if (!identity.subject().equals(exchangedIdentity.subject())) {
            throw new UnauthorizedException(
                    ErrorCode.UNAUTHORIZED,
                    "Apple 로그인 사용자 정보가 일치하지 않습니다.");
        }
    }
}
