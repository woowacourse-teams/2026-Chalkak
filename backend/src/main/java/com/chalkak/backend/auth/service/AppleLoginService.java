package com.chalkak.backend.auth.service;

import com.chalkak.backend.auth.domain.AppleSignupAuthorization;
import com.chalkak.backend.auth.domain.IssuedSocialSignupToken;
import com.chalkak.backend.auth.domain.SocialAccount;
import com.chalkak.backend.auth.domain.SocialProvider;
import com.chalkak.backend.auth.domain.VerifiedSocialIdentity;
import com.chalkak.backend.auth.repository.SocialAccountRepository;
import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.exception.ForbiddenException;
import com.chalkak.backend.exception.UnauthorizedException;
import com.chalkak.backend.user.domain.User;
import com.chalkak.backend.user.domain.UserStatus;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AppleLoginService {

    private final AppleIdTokenVerifier appleIdTokenVerifier;
    private final AppleTokenClient appleTokenClient;
    private final AppleRefreshTokenCipher refreshTokenCipher;
    private final SocialIdentityFingerprintEncoder fingerprintEncoder;
    private final SocialAccountRepository socialAccountRepository;
    private final SocialSignupTokenIssuer socialSignupTokenIssuer;
    private final AccessTokenIssuer accessTokenIssuer;
    private final UserRefreshTokenService userRefreshTokenService;

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
     */
    public AppleLoginResult login(
            String idToken,
            String authorizationCode,
            String rawNonce
    ) {
        VerifiedSocialIdentity identity = appleIdTokenVerifier.verify(
                idToken,
                rawNonce);
        String subjectHmac = fingerprintEncoder.encode(
                SocialProvider.APPLE,
                identity.subject());

        Optional<User> user = findExistingUser(subjectHmac);
        if (user.isPresent()) {
            return toLoginSuccess(user.get());
        }
        return issueSignupToken(
                identity,
                exchangeAuthorization(identity, authorizationCode, rawNonce));
    }

    private AppleLoginResult toLoginSuccess(User user) {
        return AppleLoginResult.loginSuccess(
                user.getId(),
                accessTokenIssuer.issue(user.getId()),
                userRefreshTokenService.issue(user));
    }

    private Optional<User> findExistingUser(String subjectHmac) {
        Optional<SocialAccount> socialAccount = socialAccountRepository
                .findByProviderAndSubjectHmac(
                        SocialProvider.APPLE,
                        subjectHmac);
        if (socialAccount.isEmpty()) {
            return Optional.empty();
        }

        User user = socialAccount.get().getUser();
        validateNotWithdrawnBannedAccount(user);
        if (user.isDeleted()) {
            return Optional.empty();
        }
        return Optional.of(user);
    }

    /**
     * 차단 회원은 탈퇴해도 재가입 우회 방지를 위해 소셜 계정을 유지하므로 여기서 로그인을 거부한다.
     * 탈퇴하지 않은 차단 회원은 로그인은 되고, 쓰기 요청만 인가 단계에서 막힌다.
     */
    private void validateNotWithdrawnBannedAccount(User user) {
        if (user.isDeleted() && user.getStatus() == UserStatus.BANNED) {
            throw new ForbiddenException(
                    ErrorCode.FORBIDDEN,
                    "탈퇴한 차단 소셜 계정입니다.");
        }
    }

    private AppleSignupAuthorization exchangeAuthorization(
            VerifiedSocialIdentity identity,
            String authorizationCode,
            String rawNonce
    ) {
        AppleTokenExchangeResult exchangeResult = appleTokenClient
                .exchangeAuthorizationCode(authorizationCode);
        VerifiedSocialIdentity exchangedIdentity = appleIdTokenVerifier.verify(
                exchangeResult.idToken(),
                rawNonce);
        validateSameSubject(identity, exchangedIdentity);

        return new AppleSignupAuthorization(
                exchangeResult.clientId(),
                refreshTokenCipher.encrypt(exchangeResult.refreshToken()));
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

    private AppleLoginResult issueSignupToken(
            VerifiedSocialIdentity identity,
            AppleSignupAuthorization signupAuthorization
    ) {
        IssuedSocialSignupToken signupToken = socialSignupTokenIssuer.issueApple(
                identity,
                UUID.randomUUID(),
                signupAuthorization);
        return AppleLoginResult.signUpRequired(signupToken);
    }
}
