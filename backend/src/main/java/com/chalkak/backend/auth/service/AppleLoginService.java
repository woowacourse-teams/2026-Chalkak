package com.chalkak.backend.auth.service;

import com.chalkak.backend.auth.domain.PendingAppleAuthorization;
import com.chalkak.backend.auth.domain.SocialProvider;
import com.chalkak.backend.auth.domain.VerifiedSocialIdentity;
import com.chalkak.backend.auth.repository.PendingAppleAuthorizationRepository;
import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.exception.UnauthorizedException;
import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AppleLoginService {

    /**
     * 가입이 끝나지 않은 Apple Refresh Token의 임시 보관 기간. 클라이언트는 로그인에 쓴 ID
     * Token으로 업로드 URL을 다시 요청하므로, Apple ID Token 수명 10분 안에 업로드 URL을 받고
     * 그 뒤 회원가입 토큰(5분) 안에 가입을 끝낼 시간을 덮는다.
     */
    private static final Duration PENDING_AUTHORIZATION_EXPIRATION =
            Duration.ofMinutes(15);

    private final SocialIdentityVerifier socialIdentityVerifier;
    private final AppleTokenClient appleTokenClient;
    private final AppleAuthorizationCipher authorizationCipher;
    private final PendingAppleAuthorizationRepository pendingAuthorizationRepository;
    private final SocialIdentityFingerprintEncoder fingerprintEncoder;
    private final Clock clock;
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
     * <p>이 메서드 전체에 트랜잭션을 적용하지 않는 것은 신규 회원 경로의 Apple 토큰 교환
     * HTTP 호출 중 DB 트랜잭션과 커넥션을 점유하지 않기 위해서다. 기존 회원 경로의 DB 작업은
     * {@link ExistingSocialAccountLoginProcessor}가 자신의 트랜잭션 안에서 처리한다.
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
        storePendingAuthorization(
                identity,
                exchangeAuthorization(identity, authorizationCode, rawNonce));
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

    private void storePendingAuthorization(
            VerifiedSocialIdentity identity,
            String encryptedRefreshToken
    ) {
        pendingAuthorizationRepository.save(PendingAppleAuthorization.create(
                fingerprintEncoder.encode(
                        identity.provider(),
                        identity.subject()),
                encryptedRefreshToken,
                clock.instant().plus(PENDING_AUTHORIZATION_EXPIRATION)));
    }
}
