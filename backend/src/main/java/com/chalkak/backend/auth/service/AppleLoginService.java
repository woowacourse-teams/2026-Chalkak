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
public class AppleLoginService {

    private final SocialIdentityVerifier socialIdentityVerifier;
    private final AppleTokenClient appleTokenClient;
    private final AppleAuthorizationCipher authorizationCipher;
    private final AppleSignupAuthorizationService appleSignupAuthorizationService;
    private final ExistingSocialAccountLoginProcessor existingSocialAccountLoginProcessor;

    /**
     * 기존 회원은 authorizationCode를 교환하지 않는다. 교환할 때마다 Apple에 새 grant가
     * 생기는데 우리는 마지막 하나만 저장하므로, 로그인할 때마다 폐기할 수단이 없는 grant가
     * 하나씩 쌓인다. Apple Refresh Token의 용도는 탈퇴 시 연동 폐기 하나뿐이고 그 토큰은
     * 가입 시점에 이미 저장해 두었으므로, 기존 회원 경로에서 다시 받을 이유가 없다.
     *
     * <p>덕분에 기존 회원 로그인은 외부 HTTP 호출 없이 끝나고, Apple 토큰 API의 장애가
     * 로그인을 막지 않는다. 대신 사용자가 Apple 설정에서 연동을 해제한 뒤 다시 로그인하면
     * 저장된 토큰이 무효인 채로 남는데, 그때는 이미 사용자가 직접 연동을 끊은 상태라
     * 탈퇴 시 폐기 요청도 정상 응답을 받는다.
     *
     * <p>신규 회원도 만료 전 임시 보관분이 있으면 교환하지 않는다. 같은 이유로 grant가 쌓이기
     * 때문이며, 이때 요청의 authorizationCode는 쓰이지 않는다. 신원은 ID Token의 서명과 nonce로
     * 이미 확인했고 code의 용도인 폐기용 토큰은 보관돼 있으므로, 검증할 대상이 남지 않는다.
     *
     * <p>이 메서드 전체에 트랜잭션을 적용하지 않는 것은 신규 회원 경로의 Apple 토큰 교환
     * HTTP 호출 중 DB 트랜잭션과 커넥션을 점유하지 않기 위해서다. 보관분 조회·갱신과 저장은
     * {@link AppleSignupAuthorizationService}가, 기존 회원 경로의 DB 작업은
     * {@link ExistingSocialAccountLoginProcessor}가 각자의 트랜잭션 안에서 처리한다.
     */
    public AppleLoginResult login(
            String idToken,
            String authorizationCode,
            String rawNonce
    ) {
        VerifiedSocialIdentity identity = socialIdentityVerifier.verify(
                SocialProvider.APPLE,
                idToken,
                rawNonce);

        Optional<SocialLoginSuccess> loginSuccess = existingSocialAccountLoginProcessor
                .processIfExists(identity);
        if (loginSuccess.isPresent()) {
            return toLoginSuccess(loginSuccess.get());
        }
        if (!appleSignupAuthorizationService.renewIfPresent(identity)) {
            appleSignupAuthorizationService.store(
                    identity,
                    exchangeAuthorization(identity, authorizationCode, rawNonce));
        }
        return AppleLoginResult.signUpRequired();
    }

    private AppleLoginResult toLoginSuccess(SocialLoginSuccess success) {
        return AppleLoginResult.loginSuccess(
                success.userId(),
                success.accessToken(),
                success.refreshToken());
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
